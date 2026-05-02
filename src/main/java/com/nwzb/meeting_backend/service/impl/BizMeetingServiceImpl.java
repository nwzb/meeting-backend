package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.*;
import com.nwzb.meeting_backend.mapper.BizMeetingMapper;
import com.nwzb.meeting_backend.model.dto.AiSummaryCallbackDTO;
import com.nwzb.meeting_backend.model.dto.MeetingSaveDTO;
import com.nwzb.meeting_backend.model.dto.MeetingTodoImportDTO;
import com.nwzb.meeting_backend.model.vo.*;
import com.nwzb.meeting_backend.service.*;
import com.nwzb.meeting_backend.service.ai.AiRequestService;
import com.nwzb.meeting_backend.service.ai.AiTaskQueueManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BizMeetingServiceImpl extends ServiceImpl<BizMeetingMapper, BizMeeting> implements BizMeetingService {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private BizMeetingAgendaService agendaService;

    @Autowired
    private BizMeetingContentService contentService;

    @Autowired
    private BizTodoService todoService;

    @Autowired
    private AiTaskQueueManager aiTaskQueueManager;

    @Resource
    private AiRequestService aiRequestService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysHotWordService sysHotWordService;

    @Autowired
    private SysTopicLibraryService sysTopicLibraryService;

    @Override
    public Page<MeetingVO> getUserMeetingPage(Long userId, Integer pageNum, Integer pageSize, String keyword, String sortField, Boolean isAsc) {
        // 1. 初始化分页对象
        Page<BizMeeting> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizMeeting> wrapper = new LambdaQueryWrapper<>();

        // 2. 基础条件：只能看自己的（已屏蔽的会议也会显示，但前端会限制操作）
        wrapper.eq(BizMeeting::getUserId, userId);

        // 3. 搜索条件
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            wrapper.like(BizMeeting::getTitle, keyword);
        }

        // 4. ★ 架构师防御：动态排序白名单
        if (org.springframework.util.StringUtils.hasText(sortField)) {
            String dbColumn;
            switch (sortField) {
                case "title": dbColumn = "title"; break;
                case "duration": dbColumn = "duration"; break;
                case "topicLibraryName": dbColumn = "topic_library_id"; break; // 前端按词库名排序，后端按词库ID排序归类
                case "createTime": dbColumn = "create_time"; break;
                default: dbColumn = "create_time";
            }
            if (isAsc) {
                page.addOrder(OrderItem.asc(dbColumn));
            } else {
                page.addOrder(OrderItem.desc(dbColumn));
            }
        } else {
            page.addOrder(OrderItem.desc("create_time")); // 默认倒序
        }

        // 5. 执行主表分页查询
        this.page(page, wrapper);
        List<BizMeeting> records = page.getRecords();

        if (records.isEmpty()) {
            return new Page<>(pageNum, pageSize, 0);
        }

        // 6. 提取主题词库 ID 并批量查询字典
        List<Long> topicIds = records.stream()
                .map(BizMeeting::getTopicLibraryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> topicMap = new java.util.HashMap<>();
        if (!topicIds.isEmpty()) {
            topicMap = sysTopicLibraryService.listByIds(topicIds).stream()
                    .collect(Collectors.toMap(SysTopicLibrary::getId, SysTopicLibrary::getName));
        }

        // ★ 核心修复：声明一个 final 引用，让 Lambda 表达式能够合法捕获
        final Map<Long, String> finalTopicMap = topicMap;

        // 7. 组装 VO 列表
        List<MeetingVO> voList = records.stream().map(entity -> {
            MeetingVO vo = new MeetingVO();
            org.springframework.beans.BeanUtils.copyProperties(entity, vo);

            // 使用 finalTopicMap 进行取值
            vo.setTopicLibraryName(finalTopicMap.getOrDefault(entity.getTopicLibraryId(), "默认通用词库"));
            return vo;
        }).collect(Collectors.toList());

        // 8. 返回给前端
        Page<MeetingVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BizMeeting initMeetingTask(MultipartFile file, String title, Long topicId, Long duration) {
        // 1. 确保物理存储目录存在 (C:/.../meeting_uploads/audio/)
        String audioDirPath = uploadPath.endsWith("/") ? uploadPath + "audio/" : uploadPath + "/audio/";
        File dir = new File(audioDirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 2. 生成唯一文件名并执行物理落盘
        String originalFilename = file.getOriginalFilename();
        String fileName = System.currentTimeMillis() + "_" + originalFilename;
        File dest = new File(dir, fileName);

        try {
            file.transferTo(dest);
            log.info(">>> 音频文件物理落盘成功: {}", dest.getAbsolutePath());
        } catch (Exception e) {
            log.error("文件保存失败", e);
            throw new CustomException("音频文件保存失败");
        }

        // 3. 构造数据库记录
        BizMeeting meeting = new BizMeeting();
        meeting.setUserId(SecurityUtils.getCurrentUserId());
        meeting.setTitle(title);
        meeting.setTopicLibraryId(topicId);
        meeting.setAudioUrl("/uploads/audio/" + fileName);
        meeting.setStatus(1); // 状态：1-排队中
        meeting.setDuration(duration);
        meeting.setCreateTime(LocalDateTime.now());

        this.save(meeting);
        log.info(">>> 会议记录已入库，ID: {}, 虚拟路径: {}", meeting.getId(), meeting.getAudioUrl());

        return meeting;
    }

    @Override
    public void startAiProcess(BizMeeting meeting) {
        aiTaskQueueManager.enqueueTask(meeting, "UPLOAD");
        log.info("会议 {} 已提交至调度中心", meeting.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMeetingSummaryByDTO(AiSummaryCallbackDTO summaryDTO) {
        BizMeeting meeting = this.getById(summaryDTO.getMeetingId());
        if (meeting == null) return;

        // ================= 1. 更新主表 (BizMeeting) =================
        meeting.setFullSummary(summaryDTO.getSummary());

        // 处理 List<String> 到 String 的转换 (关键词)
        if (summaryDTO.getKeywords() != null && !summaryDTO.getKeywords().isEmpty()) {
            meeting.setAiKeywords(String.join(",", summaryDTO.getKeywords()));
        } else {
            meeting.setAiKeywords(null);
        }

        // 将纯文本的 List<String> 序列化为 JSON 存入 ai_todos 字段，仅存主表，不写待办表
        if (summaryDTO.getActionItems() != null && !summaryDTO.getActionItems().isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                // 将 ["待办1", "待办2"] 变成字符串 "[\"待办1\", \"待办2\"]"
                meeting.setAiTodos(mapper.writeValueAsString(summaryDTO.getActionItems()));
            } catch (Exception e) {
                log.error("AI 待办事项序列化 JSON 失败", e);
            }
        }

        if ("FINAL_SUMMARY".equals(summaryDTO.getType())) {

            // 存入 AI 耗时统计 (防止空指针，做个判空)
            if (summaryDTO.getAsrDuration() != null) {
                meeting.setAsrDuration(summaryDTO.getAsrDuration());
            }
            if (summaryDTO.getLlmDuration() != null) {
                meeting.setLlmDuration(summaryDTO.getLlmDuration());
            }

            meeting.setStatus(4); // 标记为已完成
        }
        this.updateById(meeting);

        // ================= 2. 刷新议程表 (BizMeetingAgenda) =================
        // 先删旧数据，防止因用户插队请求导致数据重复
        if (summaryDTO.getChapters() != null) {
            agendaService.remove(new LambdaQueryWrapper<BizMeetingAgenda>()
                    .eq(BizMeetingAgenda::getMeetingId, summaryDTO.getMeetingId()));

            // 对接强类型的 AiChapterDTO
            List<BizMeetingAgenda> agendas = summaryDTO.getChapters().stream().map(chapter -> {
                BizMeetingAgenda agenda = new BizMeetingAgenda();
                agenda.setMeetingId(summaryDTO.getMeetingId());
                agenda.setTitle(chapter.getTopic());
                agenda.setSummary(chapter.getContent());
                if (chapter.getTimestamp() != null) {
                    agenda.setTimestamp(BigDecimal.valueOf(chapter.getTimestamp()));
                }
                return agenda;
            }).collect(Collectors.toList());

            agendaService.saveBatch(agendas);
        }

        // ================= 3. 剥离待办写入逻辑 =================
        // 注意：此处删除了原有自动保存到 biz_todo 表的逻辑。
        // 改为由前端展示 AI 待办并允许用户修改后，再通过新的 Controller 接口手动触发入库。
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importTodos(MeetingTodoImportDTO dto, Long userId) {
        BizMeeting meeting = this.getById(dto.getMeetingId());
        if (meeting == null || !meeting.getUserId().equals(userId)) {
            throw new CustomException("会议不存在或无权限操作");
        }

        // ★ 核心拦截：后端物理防御归档会议被操作
        if (meeting.getAuditStatus() != null && meeting.getAuditStatus() == 1) {
            throw new CustomException("该会议已归档，无法导入待办");
        }

        List<MeetingTodoImportDTO.TodoItem> todoList = dto.getTodoList();
        if (todoList == null || todoList.isEmpty()) {
            return; // 列表为空直接返回
        }

        List<BizTodo> insertTodos = todoList.stream().map(item -> {
            BizTodo todo = new BizTodo();
            todo.setUserId(userId);
            todo.setSourceMeetingId(dto.getMeetingId());
            todo.setTitle(item.getText());
            todo.setStatus(0);
            // 核心修改：使用前端传来的象限，如果没有则默认 4 (灰)
            todo.setPriorityQuadrant(item.getQuadrant() != null ? item.getQuadrant() : 4);
            todo.setCreateTime(LocalDateTime.now());
            return todo;
        }).collect(Collectors.toList());

        todoService.saveBatch(insertTodos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void globalSaveMeeting(Long id, MeetingSaveDTO dto) {
        // ★ 核心拦截：先查出原会议，进行状态和权限校验
        BizMeeting existMeeting = this.getById(id);
        if (existMeeting == null) {
            throw new CustomException("会议不存在");
        }
        if (existMeeting.getAuditStatus() != null && existMeeting.getAuditStatus() == 1) {
            throw new CustomException("该会议已归档，禁止修改任何数据");
        }

        // 1. 保存主表 (纪要 + 待办)
        BizMeeting meeting = new BizMeeting();
        meeting.setId(id);
        meeting.setFullSummary(dto.getFullSummary());

        if (dto.getAiTodos() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                meeting.setAiTodos(mapper.writeValueAsString(dto.getAiTodos()));
            } catch (Exception e) {
                log.error("AI 待办序列化 JSON 失败", e);
            }
        }
        this.updateById(meeting);

        // 2. 保存逐字稿 (批量更新内容和说话人)
        if (dto.getContents() != null && !dto.getContents().isEmpty()) {
            // 逐字稿切片数量多，用批量更新根据 ID 刷新内容即可
            contentService.updateBatchById(dto.getContents());
        }

        // 3. 保存章节大纲 (覆盖式保存，防止数据冗余或遗漏删除)
        if (dto.getAgendas() != null) {
            // 先清理旧大纲
            agendaService.remove(new LambdaQueryWrapper<BizMeetingAgenda>()
                    .eq(BizMeetingAgenda::getMeetingId, id));

            // 重新插入最新大纲
            if (!dto.getAgendas().isEmpty()) {
                List<BizMeetingAgenda> newAgendas = dto.getAgendas().stream().peek(agenda -> {
                    // 确保关联 ID 正确，且清空原有自增 ID 以便重新插入
                    agenda.setMeetingId(id);
                    agenda.setId(null);
                }).collect(Collectors.toList());
                agendaService.saveBatch(newAgendas);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMeetingCascade(Long meetingId) {
        BizMeeting meeting = this.getById(meetingId);
        if (meeting == null) {
            return;
        }

        // 0.1 拦截并通知 Python 中断任务
        // 如果状态是 排队(1)、ASR中(2)、LLM中(3)
        if (meeting.getStatus() == 1 || meeting.getStatus() == 2 || meeting.getStatus() == 3) {
            try {
                log.info(">>> 会议[{}]正在AI处理中，向 Python 引擎发送强制中断信号...", meetingId);
                // 这里需要在 AiRequestService 中新增一个发送中止请求的方法
                aiRequestService.cancelAiTask(meetingId);
            } catch (Exception e) {
                log.error(">>> 通知 Python 中断任务失败，继续执行物理删除: {}", e.getMessage());
            }
        }
        // 0.2 清理物理音频文件
        // 先查出主会议记录，拿到音频的虚拟路径
        if (meeting.getAudioUrl() != null) {
            try {
                // 从 "/uploads/audio/173000000_test.mp3" 中提取真实文件名 "173000000_test.mp3"
                String audioUrl = meeting.getAudioUrl();
                String fileName = audioUrl.replace("/uploads/audio/", "");

                // 拼接与上传时完全一致的物理目录路径
                String audioDirPath = uploadPath.endsWith("/") ? uploadPath + "audio/" : uploadPath + "/audio/";
                File physicalFile = new File(audioDirPath, fileName);

                // 检查并删除文件
                if (physicalFile.exists()) {
                    boolean deleted = physicalFile.delete();
                    if (deleted) {
                        log.info(">>> 物理音频文件删除成功: {}", physicalFile.getAbsolutePath());
                    } else {
                        log.warn(">>> 物理音频文件删除失败: {}", physicalFile.getAbsolutePath());
                    }
                } else {
                    log.warn(">>> 物理音频文件不存在，跳过删除: {}", physicalFile.getAbsolutePath());
                }
            } catch (Exception e) {
                // 捕获文件删除的异常，只打日志，不抛出。
                // 防止因为文件被意外手动删除，导致整个删除事务回滚，造成数据库里产生删不掉的“脏数据”
                log.error(">>> 删除物理音频文件时发生异常: {}", e.getMessage());
            }
        }

        // 1. 删除逐字稿切片
        contentService.remove(new LambdaQueryWrapper<BizMeetingContent>()
                .eq(BizMeetingContent::getMeetingId, meetingId));

        // 2. 删除章节大纲
        agendaService.remove(new LambdaQueryWrapper<BizMeetingAgenda>()
                .eq(BizMeetingAgenda::getMeetingId, meetingId));

        // 3. 删除从该会议产生的待办事项
        todoService.remove(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getSourceMeetingId, meetingId));

        // 4. 删除主会议记录
        if (meeting != null) {
            this.removeById(meetingId);
            log.info(">>> 会议记录及其关联数据(切片/大纲/待办)已级联删除，ID: {}", meetingId);
        }
    }

    @Override
    public List<GlobalSearchVO> searchGlobal(Long userId, String keyword) {
        // 调用 Mapper 的模糊查询
        return baseMapper.searchGlobal(userId, "%" + keyword + "%");
    }

    // 获取当前用户统计数据
    @Override
    public DashboardStatsVO getDashboardStats(Long userId, String startDate, String endDate) {
        DashboardStatsVO vo = new DashboardStatsVO();

        // 1. 折线图：近7天会议趋势 (调用 Mapper)
        List<DashboardStatsVO.TrendData> rawTrend = baseMapper.selectMeetingTrend(userId, startDate, endDate);
        vo.setMeetingTrend(fillMissingDates(rawTrend, startDate, endDate));

        // 2. 饼图：参会人数统计+会议类型统计 (调用 Mapper 复杂聚合)
        vo.setSpeakerStats(baseMapper.selectSpeakerStats(userId, startDate, endDate));
        vo.setTopicStats(baseMapper.selectTopicStats(userId, startDate, endDate));

        // 3. 词云：汇总所有会议关键词
        List<String> allKeywords = baseMapper.selectAllKeywords(userId, startDate, endDate);
        Map<String, Integer> wordMap = new HashMap<>();
        for (String kwStr : allKeywords) {
            if (kwStr != null) {
                for (String s : kwStr.split(",")) {
                    String word = s.trim();
                    if(!word.isEmpty()) wordMap.put(word, wordMap.getOrDefault(word, 0) + 1);
                }
            }
        }
        List<DashboardStatsVO.WordCloudData> cloudData = wordMap.entrySet().stream()
                .map(e -> new DashboardStatsVO.WordCloudData(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(30) // 取前30个高频词
                .collect(Collectors.toList());
        vo.setWordCloud(cloudData);

        return vo;
    }

    // 获取全平台统计数据
    @Override
    public AdminOpsStatsVO getGlobalDashboardStats(String startDate, String endDate) {
        AdminOpsStatsVO vo = new AdminOpsStatsVO();

        // 将时间参数一路传给底层的 Mapper
        List<DashboardStatsVO.TrendData> rawGlobalTrend = baseMapper.selectGlobalMeetingTrend(startDate, endDate);
        List<DashboardStatsVO.TrendData> paddedGlobalTrend = fillMissingDates(rawGlobalTrend, startDate, endDate);
        vo.setMeetingTrend(paddedGlobalTrend.toArray(new DashboardStatsVO.TrendData[0]));

        vo.setSpeakerStats(baseMapper.selectGlobalSpeakerStats(startDate, endDate).toArray(new DashboardStatsVO.PieData[0]));
        vo.setTopicStats(baseMapper.selectGlobalTopicStats(startDate, endDate).toArray(new DashboardStatsVO.PieData[0]));

        // 处理词云
        List<String> allKeywords = baseMapper.selectGlobalAllKeywords(startDate, endDate);
        Map<String, Integer> wordMap = new HashMap<>();
        for (String kwStr : allKeywords) {
            if (kwStr != null) {
                for (String s : kwStr.split(",")) {
                    String word = s.trim();
                    if(!word.isEmpty()) wordMap.put(word, wordMap.getOrDefault(word, 0) + 1);
                }
            }
        }
        List<DashboardStatsVO.WordCloudData> cloudData = wordMap.entrySet().stream()
                .map(e -> new DashboardStatsVO.WordCloudData(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(30)
                .collect(Collectors.toList());
        vo.setWordCloud(cloudData.toArray(new DashboardStatsVO.WordCloudData[0]));

        // 资源耗时统计
        vo.setResourceStats(baseMapper.selectGlobalResourceStats(startDate, endDate));

        return vo;
    }

    @Override
    public IPage<MeetingLogVO> getGlobalMeetingLogs(Integer current, Integer size, String keyword, String sortField, String sortOrder) {
        Page<MeetingLogVO> page = new Page<>(current, size);

        // 1. 安全防护与字段映射：防止前端恶意注入 SQL，同时将驼峰转为数据库对应的下划线字段
        String dbSortField = "m.create_time"; // 默认值
        if (StringUtils.hasText(sortField)) {
            switch (sortField) {
                case "id": dbSortField = "m.id"; break;
                case "duration": dbSortField = "m.duration"; break;
                case "asrDuration": dbSortField = "m.asr_duration"; break;
                case "llmDuration": dbSortField = "m.llm_duration"; break;
                case "createTime": dbSortField = "m.create_time"; break;
                case "isVectorized": dbSortField = "m.is_vectorized"; break;
                default: dbSortField = "m.create_time"; // 遇到不认识的字段，统统按时间排，防注入
            }
        }

        // 2. 升降序映射
        String dbSortOrder = "DESC"; // 默认倒序
        if (StringUtils.hasText(sortOrder)) {
            if ("ascending".equals(sortOrder) || "asc".equalsIgnoreCase(sortOrder)) {
                dbSortOrder = "ASC";
            }
        }

        // 3. 调用 Mapper 的连表查询
        return baseMapper.selectMeetingLogsWithUser(page, keyword, dbSortField, dbSortOrder);
    }

    @Override
    public Page<MeetingAuditVO> getAuditMeetingPage(Page<BizMeeting> page, Integer auditStatus, String keyword, String sortField, Boolean isAsc) {
        QueryWrapper<BizMeeting> wrapper = new QueryWrapper<>();

        if (auditStatus != null) wrapper.eq("audit_status", auditStatus);
        if (keyword != null && !keyword.isEmpty()) wrapper.like("title", keyword);

        // ★ 架构师优化：使用白名单替换原来的正则，彻底杜绝 SQL 注入风险
        if (sortField != null && !sortField.isEmpty()) {
            String dbField;
            switch (sortField) {
                case "id": dbField = "id"; break;
                case "title": dbField = "title"; break;
                case "userId": dbField = "user_id"; break;
                case "sensitiveWordCount": dbField = "sensitive_word_count"; break; // 支持前端的新排序字段
                case "createTime": dbField = "create_time"; break;
                default: dbField = "create_time"; // 遇到不认识的字段，统一按时间排
            }
            wrapper.orderBy(true, isAsc, dbField);
        } else {
            wrapper.orderByDesc("create_time"); // 默认降序
        }

        Page<BizMeeting> entityPage = this.page(page, wrapper);
        List<BizMeeting> records = entityPage.getRecords();

        // 如果没查到数据，直接返回空页，省去后面的关联查询
        if (records.isEmpty()) {
            return new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        }

        // --- 1. 提取用户ID并查询映射 ---
        List<Long> userIds = records.stream()
                .map(BizMeeting::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = sysUserService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(com.nwzb.meeting_backend.entity.SysUser::getId, com.nwzb.meeting_backend.entity.SysUser::getUsername));
        }

        // --- 2. ★ 新增：提取词库ID并查询映射 ---
        List<Long> topicIds = records.stream()
                .map(BizMeeting::getTopicLibraryId)
                .filter(java.util.Objects::nonNull) // 过滤掉脏数据(可能存在的null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> topicMap = new java.util.HashMap<>();
        if (!topicIds.isEmpty()) {
            topicMap = sysTopicLibraryService.listByIds(topicIds).stream()
                    .collect(Collectors.toMap(com.nwzb.meeting_backend.entity.SysTopicLibrary::getId, com.nwzb.meeting_backend.entity.SysTopicLibrary::getName));
        }

        // --- 3. 组装 VO ---
        Map<Long, String> finalUserMap = userMap;
        Map<Long, String> finalTopicMap = topicMap;

        List<MeetingAuditVO> voList = records.stream().map(entity -> {
            MeetingAuditVO vo = new MeetingAuditVO();
            org.springframework.beans.BeanUtils.copyProperties(entity, vo);

            // 装配名字
            vo.setUsername(finalUserMap.getOrDefault(entity.getUserId(), "未知用户"));
            // ★ 新增：装配词库名称
            vo.setTopicLibraryName(finalTopicMap.getOrDefault(entity.getTopicLibraryId(), "默认通用词库"));

            // ★ 新增：兜底防御，防止数据库里的 null 到前端变成不显示
            if (vo.getSensitiveWordCount() == null) {
                vo.setSensitiveWordCount(0);
            }

            return vo;
        }).collect(Collectors.toList());

        Page<MeetingAuditVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 一键重算全平台所有会议的敏感词命中数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateAllSensitiveWords() {
        log.info(">>> 开始执行全平台会议敏感词重算任务...");

        // 1. 获取所有会议
        List<BizMeeting> allMeetings = this.list();

        for (BizMeeting meeting : allMeetings) {
            int count = 0;

            // 2. 获取该会议需要校验的敏感词集合 (通用词库 ID=1 + 专属词库)
            Set<String> wordsToCheck = new java.util.HashSet<>();
            List<String> generalWords = sysHotWordService.getAllSensitiveWords(1L);
            if (generalWords != null) wordsToCheck.addAll(generalWords);

            if (meeting.getTopicLibraryId() != null && meeting.getTopicLibraryId() != 1L) {
                List<String> topicWords = sysHotWordService.getAllSensitiveWords(meeting.getTopicLibraryId());
                if (topicWords != null) wordsToCheck.addAll(topicWords);
            }

            if (!wordsToCheck.isEmpty()) {
                // 3. 聚合该会议的所有文本内容
                StringBuilder fullTextBuilder = new StringBuilder();

                // 3.1 追加 AI 全文总结
                if (meeting.getFullSummary() != null) {
                    fullTextBuilder.append(meeting.getFullSummary());
                }

                // 3.2 追加所有逐字稿切片内容
                List<BizMeetingContent> contents = contentService.getByMeetingId(meeting.getId());
                if (contents != null) {
                    for (BizMeetingContent c : contents) {
                        if (c.getContent() != null) fullTextBuilder.append(c.getContent());
                    }
                }

                // 3.3 追加所有议程章节的标题和摘要
                List<BizMeetingAgenda> agendas = agendaService.list(
                        new LambdaQueryWrapper<BizMeetingAgenda>().eq(BizMeetingAgenda::getMeetingId, meeting.getId())
                );
                if (agendas != null) {
                    for (BizMeetingAgenda a : agendas) {
                        if (a.getTitle() != null) fullTextBuilder.append(a.getTitle());
                        if (a.getSummary() != null) fullTextBuilder.append(a.getSummary());
                    }
                }

                String allText = fullTextBuilder.toString();

                // 4. 执行高效率字符串匹配计数
                for (String word : wordsToCheck) {
                    if (word == null || word.trim().isEmpty()) continue;
                    int index = 0;
                    while ((index = allText.indexOf(word, index)) != -1) {
                        count++;
                        index += word.length();
                    }
                }
            }

            // 5. 更新该会议的敏感词数量
            if (meeting.getSensitiveWordCount() == null || meeting.getSensitiveWordCount() != count) {
                meeting.setSensitiveWordCount(count);
                this.updateById(meeting);
            }
        }

        log.info("<<< 全平台会议敏感词重算任务执行完毕。共处理 {} 场会议。", allMeetings.size());
    }

    /**
     * 辅助方法：为折线图补全缺失的日期数据（补全数量或时长）（注意，这是个私有方法，所以不用再BizMeetingService中进行重构）
     */
    private List<DashboardStatsVO.TrendData> fillMissingDates(List<DashboardStatsVO.TrendData> rawList, String startDate, String endDate) {
        if (rawList == null) {
            rawList = new ArrayList<>();
        }

        LocalDate start;
        LocalDate end;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 1. 确定起始日期
        if (StringUtils.hasText(startDate)) {
            // 前端传来的格式通常是 "2023-10-25 00:00:00"，截取前10位即可
            start = LocalDate.parse(startDate.substring(0, 10), formatter);
        } else {
            // 如果没传时间范围，且数据库一条数据都没有，直接返回空
            if (rawList.isEmpty()) return rawList;
            start = LocalDate.parse(rawList.get(0).getDate(), formatter);
        }

        // 2. 确定结束日期
        if (StringUtils.hasText(endDate)) {
            end = LocalDate.parse(endDate.substring(0, 10), formatter);
        } else {
            if (rawList.isEmpty()) return rawList;
            // 默认以今天的日期作为结束，或者以数据中最后一天作为结束
            end = LocalDate.now();
        }

        // 防御性编程：防止时间错乱导致死循环
        if (start.isAfter(end)) {
            return rawList;
        }

        // 3. 将数据库查询结果转为 Map，保存完整的 TrendData 对象
        Map<String, DashboardStatsVO.TrendData> dataMap = rawList.stream()
                .collect(Collectors.toMap(
                        DashboardStatsVO.TrendData::getDate,
                        td -> td,
                        (v1, v2) -> v1 // 解决可能存在的重复 Key 问题
                ));

        // 4. 游标遍历：从开始日期到结束日期，逐天生成数据
        List<DashboardStatsVO.TrendData> paddedList = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            String dateStr = current.format(formatter);
            DashboardStatsVO.TrendData td = new DashboardStatsVO.TrendData();
            td.setDate(dateStr);

            // ★ 核心修改：从 Map 中获取完整对象，如果存在则赋值 count 和 duration，否则全部给 0
            if (dataMap.containsKey(dateStr)) {
                DashboardStatsVO.TrendData existData = dataMap.get(dateStr);
                td.setCount(existData.getCount() != null ? existData.getCount() : 0);
                td.setDuration(existData.getDuration() != null ? existData.getDuration() : 0L);
            } else {
                td.setCount(0);
                td.setDuration(0L); // 空白日期时长为 0 秒
            }

            paddedList.add(td);
            current = current.plusDays(1); // 推进到下一天
        }

        return paddedList;
    }
}