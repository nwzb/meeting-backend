package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.entity.BizTodo;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.mapper.BizTodoMapper;
import com.nwzb.meeting_backend.mapper.BizMeetingMapper;
import com.nwzb.meeting_backend.model.dto.TodoDTO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.model.vo.TodoVO;
import com.nwzb.meeting_backend.service.BizTodoService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizTodoServiceImpl extends ServiceImpl<BizTodoMapper, BizTodo> implements BizTodoService {

    // 注入会议的 Mapper 用于查标题
    private final BizMeetingMapper bizMeetingMapper;

    @Override
    public List<TodoVO> getAllTodos(Long userId) {
        // 1. 查询该用户的所有待办事项，按创建时间倒序排
        List<BizTodo> list = this.list(
                Wrappers.<BizTodo>lambdaQuery()
                        .eq(BizTodo::getUserId, userId)
                        .orderByAsc(BizTodo::getSortOrder)
                        .orderByDesc(BizTodo::getCreateTime)
        );

        // 提取所有源自会议的 ID，批量查询会议标题
        Set<Long> meetingIds = list.stream()
                .map(BizTodo::getSourceMeetingId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, String> meetingTitleMap = new java.util.HashMap<>();
        if (!meetingIds.isEmpty()) {
            List<BizMeeting> meetings = bizMeetingMapper.selectBatchIds(meetingIds);
            for (BizMeeting m : meetings) {
                meetingTitleMap.put(m.getId(), m.getTitle());
            }
        }

        // 2. 将所有实体类转换为 VO 类
        List<TodoVO> voList = list.stream().map(todo -> {
            TodoVO vo = new TodoVO();
            BeanUtils.copyProperties(todo, vo);
            // 初始化 children 列表，防止前端拿到 null 报错
            vo.setChildren(new ArrayList<>());
            // 填入会议标题
            if (vo.getSourceMeetingId() != null) {
                vo.setSourceMeetingTitle(meetingTitleMap.get(vo.getSourceMeetingId()));
            }
            return vo;
        }).collect(Collectors.toList());

        // 3. 构建树形结构 (核心逻辑)
        // 3.1 建立一个按 ID 快速查找的 Map，时间复杂度 O(1)
        Map<Long, TodoVO> voMap = voList.stream()
                .collect(Collectors.toMap(TodoVO::getId, vo -> vo));

        // 3.2 准备一个集合用来存放最终的“根节点” (即没有父节点的待办)
        List<TodoVO> rootNodes = new ArrayList<>();

        for (TodoVO vo : voList) {
            Long parentId = vo.getParentId();
            // 如果 parentId 为 0 或 null，说明它是顶级待办
            if (parentId == null || parentId == 0L) {
                rootNodes.add(vo);
            } else {
                // 如果它是子待办，则找到它的父节点，并把自己塞进父节点的 children 列表中
                TodoVO parent = voMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 【容错处理】如果找不到父节点(可能父节点被物理删除了，成了孤儿节点)，为了防止数据丢失，把它当作根节点展示
                    rootNodes.add(vo);
                }
            }
        }

        // 返回组装好的树形列表
        return rootNodes;
    }

    // 批量更新
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateTodos(List<TodoDTO> dtoList, Long userId) {
        if (dtoList == null || dtoList.isEmpty()) return;

        // 提取需要更新的实体
        List<BizTodo> updateList = dtoList.stream().map(dto -> {
            BizTodo entity = new BizTodo();
            entity.setId(dto.getId());
            entity.setPriorityQuadrant(dto.getPriorityQuadrant());
            entity.setParentId(dto.getParentId());
            entity.setSortOrder(dto.getSortOrder());
            return entity;
        }).collect(Collectors.toList());

        // Mybatis-Plus 提供的批量更新方法
        this.updateBatchById(updateList);
    }

    @Override
    public void saveTodo(TodoDTO dto, Long userId) {
        // 1. 校验逻辑：如果设置了提醒，必须有提醒时间
        if (dto.getRemindType() != null && dto.getRemindType() > 0) {
            if (dto.getRemindTime() == null) {
                throw new CustomException(400, "设置了提醒方式，必须选择提醒时间");
            }
        }

        BizTodo todo = new BizTodo();
        BeanUtils.copyProperties(dto, todo);
        todo.setUserId(userId);

        // 2. 设置默认值
        if (todo.getStatus() == null) todo.setStatus(0);
        if (todo.getPriorityQuadrant() == null) todo.setPriorityQuadrant(4);
        if (todo.getParentId() == null) todo.setParentId(0L);
        // 如果 DTO 没传 remindType，默认 0 (不提醒)
        if (todo.getRemindType() == null) todo.setRemindType(0);

        // 3. 如果是子待办，自动继承父级的会议溯源 ID
        if (todo.getParentId() != 0L) {
            BizTodo parent = this.getById(todo.getParentId());
            if (parent != null && parent.getSourceMeetingId() != null) {
                todo.setSourceMeetingId(parent.getSourceMeetingId());
            }
        }
        this.save(todo);
    }

    @Override
    public void updateTodo(TodoDTO dto, Long userId) {
        // 1. 基础校验
        BizTodo todo = this.getById(dto.getId());
        if (todo == null || !todo.getUserId().equals(userId)) {
            throw new CustomException(403, "无权修改或待办不存在");
        }

        // 2. 提醒逻辑校验
        // 如果本次修改将 remindType 设置为开启状态(1-4)，则校验时间是否为空
        if (dto.getRemindType() != null && dto.getRemindType() > 0) {
            if (dto.getRemindTime() == null) {
                throw new CustomException(400, "设置了提醒方式，必须选择提醒时间");
            }
        }

        // 3. 执行更新
        // BeanUtils 会把 DTO 中的 remindType, remindTime 等字段拷贝给 todo 实体
        BeanUtils.copyProperties(dto, todo, "id", "userId", "createTime");

        // 特殊处理：如果用户改成了“不提醒”，将 remindTime 置空（可选逻辑，推荐保持数据干净）
        if (dto.getRemindType() != null && dto.getRemindType() == 0) {
            todo.setRemindTime(null);
        }

        this.updateById(todo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTodo(Long id, Long userId) {
        BizTodo todo = this.getById(id);
        if (todo == null || !todo.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除或待办不存在");
        }

        // 删除该待办本身
        this.removeById(id);

        // 级联删除：如果这个待办是父待办，顺便把它的子待办也删掉
        // 注意：如果你未来允许无限层级嵌套（孙子、重孙子），这里可能需要改成递归删除或者在数据库层加外键级联删除。
        // 目前对于仅一层的“待办-子待办”关系，这样的写法性能最高且足够用了。
        this.remove(Wrappers.<BizTodo>lambdaQuery()
                .eq(BizTodo::getParentId, id)
                .eq(BizTodo::getUserId, userId));
    }

    @Override
    public List<GlobalSearchVO> searchGlobal(Long userId, String keyword) {
        return baseMapper.searchGlobal(userId, "%" + keyword + "%");
    }
}
