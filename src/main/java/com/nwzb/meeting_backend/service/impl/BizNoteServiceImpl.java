package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.entity.BizNote;
import com.nwzb.meeting_backend.entity.BizNoteCollection;
import com.nwzb.meeting_backend.mapper.BizNoteCollectionMapper;
import com.nwzb.meeting_backend.mapper.BizNoteMapper;
import com.nwzb.meeting_backend.model.dto.NoteDTO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.model.vo.NoteTreeVO;
import com.nwzb.meeting_backend.model.vo.NoteVO;
import com.nwzb.meeting_backend.service.BizNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizNoteServiceImpl extends ServiceImpl<BizNoteMapper, BizNote> implements BizNoteService {

    private final BizNoteCollectionMapper collectionMapper;

    @Override
    public List<NoteTreeVO> getNoteTree(Long userId) {
        // 1. 获取该用户所有的分类文件夹，按 sort_order 排序
        List<BizNoteCollection> collections = collectionMapper.selectList(
                Wrappers.<BizNoteCollection>lambdaQuery()
                        .eq(BizNoteCollection::getUserId, userId)
                        .orderByAsc(BizNoteCollection::getSortOrder)
        );

        // 2. 获取该用户所有的笔记 (先按是否置顶排，再按自定义排序值排，最后按时间兜底)
        List<BizNote> allNotes = this.list(
                Wrappers.<BizNote>lambdaQuery()
                        .eq(BizNote::getUserId, userId)
                        .orderByDesc(BizNote::getIsTop)      // 1. 绝对的优先级：置顶在前
                        .orderByAsc(BizNote::getSortOrder)   // 2. 次优先级：用户拖拽的顺序
                        .orderByDesc(BizNote::getUpdateTime) // 3. 兜底：修改时间
        );

        // 3. 将笔记转为 VO，并按 collectionId 分组
        Map<Long, List<NoteVO>> noteGroup = allNotes.stream().map(note -> {
            NoteVO vo = new NoteVO();
            BeanUtils.copyProperties(note, vo);
            // 补充布尔类型转换，MyBatis-Plus中tinyint(1)会自动映射为Boolean
            return vo;
        }).collect(Collectors.groupingBy(note -> note.getCollectionId() == null ? 0L : note.getCollectionId()));

        List<NoteTreeVO> tree = new ArrayList<>();

        // 4. 组装默认分类 ( collectionId 为 null 的笔记 )
        NoteTreeVO defaultNode = new NoteTreeVO();
        defaultNode.setCollectionId(0L);
        defaultNode.setCollectionName("默认分类");
        defaultNode.setSortOrder(0);
        defaultNode.setNotes(noteGroup.getOrDefault(0L, new ArrayList<>()));
        tree.add(defaultNode);

        // 5. 组装用户自定义的分类
        for (BizNoteCollection coll : collections) {
            NoteTreeVO node = new NoteTreeVO();
            node.setCollectionId(coll.getId());
            node.setCollectionName(coll.getName());
            node.setSortOrder(coll.getSortOrder());
            node.setNotes(noteGroup.getOrDefault(coll.getId(), new ArrayList<>()));
            tree.add(node);
        }

        return tree;
    }

    @Override
    public void saveNote(NoteDTO dto, Long userId) {
        BizNote note = new BizNote();
        BeanUtils.copyProperties(dto, note);
        note.setUserId(userId);
        // isTop 默认为 false
        if (note.getIsTop() == null) {
            note.setIsTop(0);
        }
        this.save(note);
    }

    @Override
    public void updateNote(NoteDTO dto, Long userId) {
        BizNote note = this.getById(dto.getId());
        if (note == null || !note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权修改或笔记不存在");
        }

        // 关键修复：使用 LambdaUpdateWrapper 强制更新字段，即使前端传了 null 也能生效
        this.update(Wrappers.<BizNote>lambdaUpdate()
                .eq(BizNote::getId, dto.getId())
                .eq(BizNote::getUserId, userId)
                .set(BizNote::getTitle, dto.getTitle())
                .set(BizNote::getContent, dto.getContent())
                .set(BizNote::getIsTop, dto.getIsTop())
                // 支持将笔记移出分类 (即设为 null)
                .set(BizNote::getCollectionId, dto.getCollectionId())
                // 如果后续加了 sortOrder 字段，这里也要加上
                .set(dto.getSortOrder() != null, BizNote::getSortOrder, dto.getSortOrder())
        );
    }

    @Override
    public void deleteNote(Long id, Long userId) {
        BizNote note = this.getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除或笔记不存在");
        }
        this.removeById(id);
    }

    @Override
    public NoteVO getNoteDetail(Long id, Long userId) {
        BizNote note = this.getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权查看或笔记不存在");
        }
        NoteVO vo = new NoteVO();
        BeanUtils.copyProperties(note, vo);
        return vo;
    }

    @Override
    public List<GlobalSearchVO> searchGlobal(Long userId, String keyword) {
        return baseMapper.searchGlobal(userId, "%" + keyword + "%");
    }
}
