package com.nwzb.meeting_backend.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.entity.BizMeetingAgenda;
import com.nwzb.meeting_backend.entity.SysTopicLibrary;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.model.dto.MeetingSaveDTO;
import com.nwzb.meeting_backend.model.dto.MeetingTodoImportDTO;
import com.nwzb.meeting_backend.model.vo.MeetingDetailVO;
import com.nwzb.meeting_backend.model.vo.MeetingVO;
import com.nwzb.meeting_backend.service.*;
import com.nwzb.meeting_backend.service.ai.AiRequestService;
import com.nwzb.meeting_backend.service.ai.AiTaskQueueManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会议管理接口
 */
@RestController
@RequestMapping("/api/meeting")
@Slf4j
public class MeetingController {

    // 引入常量定义，代表数据库中的“系统通用敏感词库”，ID为1
    private static final Long GLOBAL_SENSITIVE_LIBRARY_ID = 1L;

    @Resource
    private SysTopicLibraryService sysTopicLibraryService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysHotWordService sysHotWordService;

    @Autowired
    private BizMeetingService meetingService;

    @Autowired
    private BizMeetingContentService contentService;

    @Autowired
    private BizMeetingAgendaService agendaService;

    @Autowired
    private AiRequestService aiRequestService;

    @Autowired
    private AiTaskQueueManager aiTaskQueueManager;

    /**
     * 获取会议列表 (带分页、搜索、动态排序)
     */
    @GetMapping("/list")
    public Result<Page<MeetingVO>> getMeetingList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(defaultValue = "false") Boolean isAsc) {

        // 1. 获取当前登录用户的 ID
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 2. 调用 Service 层进行分页查询和数据组装
        Page<MeetingVO> pageData = meetingService.getUserMeetingPage(currentUserId, pageNum, pageSize, keyword, sortField, isAsc);

        return Result.success(pageData);
    }

    /**
     * 获取主题词库列表 (供前端下拉框使用)
     */
    @GetMapping("/topic-libraries")
    public Result<List<SysTopicLibrary>> getPublicTopicLibraries() {
        // 仅查询 is_public = 1 的词库
        LambdaQueryWrapper<SysTopicLibrary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysTopicLibrary::getIsPublic, 1)
                .orderByAsc(SysTopicLibrary::getId);
        List<SysTopicLibrary> list = sysTopicLibraryService.list(queryWrapper);
        return Result.success(list);
    }

    /**
     * 上传会议音频并启动 AI 识别流程
     * 流程：保存文件 -> 入库记录(status=1) -> 异步通知Python
     */
    @PostMapping("/upload")
    public Result<Long> uploadMeeting(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("topicId") Long topicId,
            @RequestParam(value = "duration", defaultValue = "0") Long duration) {

        log.info("接收到会议上传请求: title={}, topicId={}", title, topicId);

        // 1. 初始化任务（保存文件 + 数据库落库）
        BizMeeting meeting = meetingService.initMeetingTask(file, title, topicId, duration);

        // 2. 异步调用 Python AI 引擎
        meetingService.startAiProcess(meeting);

        return Result.success(meeting.getId());
    }

    /**
     * 更新 AI 摘要 (对应前端 AIEditor 的持久化)
     */
    @PutMapping("/{id}/summary")
    public Result<Boolean> updateSummary(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // ★ 核心拦截：先查出原会议，进行状态校验
        BizMeeting existMeeting = meetingService.getById(id);
        if (existMeeting == null) {
            return Result.error("会议不存在");
        }
        if (existMeeting.getAuditStatus() != null && existMeeting.getAuditStatus() == 1) {
            return Result.error("该会议已归档，禁止修改");
        }

        String summary = body.get("summary");
        BizMeeting meeting = new BizMeeting();
        meeting.setId(id);
        meeting.setFullSummary(summary);
        return Result.success(meetingService.updateById(meeting));
    }

    // 识别途中立即生成摘要
    @PostMapping("/{id}/partial-summary")
    public Result<?> requestPartialSummary(@PathVariable Long id) {
        // 逻辑：向 Python 端 (127.0.0.1:8000/api/v1/request_partial_summary) 发送 Form 插队请求
        return aiRequestService.sendPartialSummaryRequest(id);
    }

    // ======== 请求重新生成最终摘要 ========
    @PostMapping("/{id}/regenerate-summary")
    public Result<?> regenerateSummary(@PathVariable Long id) {
        BizMeeting existMeeting = meetingService.getById(id);
        if (existMeeting == null) {
            return Result.error("会议不存在");
        }
        if (existMeeting.getStatus() != 4 && existMeeting.getStatus() != 9) {
            return Result.error("当前会议状态不支持重新生成摘要");
        }
        if (existMeeting.getAuditStatus() != null && existMeeting.getAuditStatus() == 1) {
            return Result.error("该会议已归档，禁止操作");
        }

        // 核心修改：不再直接调 Python，而是扔进队列管理器排队！
        aiTaskQueueManager.enqueueTask(existMeeting, "REGENERATE");

        return Result.success("任务已加入队列排队");
    }

    /**
     * 获取会议详情
     */
    @GetMapping("/{id}/detail")
    public Result<MeetingDetailVO> getMeetingDetail(@PathVariable Long id) {
        MeetingDetailVO vo = new MeetingDetailVO();

        // 1. 获取主表
        BizMeeting meeting = meetingService.getById(id);

        // ================== 🌟 核心越权校验逻辑开始 ==================
        if (meeting == null) {
            throw new CustomException("会议不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 获取当前用户信息，判断角色
        SysUser currentUser = sysUserService.getById(currentUserId);
        Integer role = currentUser.getRole(); // 角色: 1-用户, 2-运维, 3-审计, 9-超管
        log.info("审计检查 - 访问者ID: {}, 访问者角色: {}, 会议所属人ID: {}", currentUserId, role, meeting.getUserId());

        // 判定：如果是审计或超管，拥有“豁免权”
        boolean hasAuditPrivilege = (role != null && (role == 3 || role == 9));

        // 校验：如果不具有豁免权，且不是本人，才拦截
        if (!hasAuditPrivilege && !meeting.getUserId().equals(currentUserId)) {
            log.warn("鉴权失败：用户 {} 无权访问会议 {}", currentUserId, id);
            throw new CustomException("无权限访问该会议内容");
        }

        // 审计状态校验：审计/超管自己看自己的屏蔽会议，或者看别人的屏蔽会议，也应当允许（因为要审查）
        // 只有普通用户看已被屏蔽的会议时，才拦截
        if (!hasAuditPrivilege && meeting.getAuditStatus() != null && meeting.getAuditStatus() == 2) {
            throw new CustomException("该会议内容涉嫌违规，已被屏蔽");
        }
        // ================== 🌟 核心越权校验逻辑结束 ==================


        // 2. 解析待办事项 JSON 字符串转为 List<String>
        if (meeting.getAiTodos() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<String> todoList = mapper.readValue(meeting.getAiTodos(), new TypeReference<List<String>>(){});
                vo.setAiTodos(todoList);
            } catch (Exception e) {
                log.error("解析会议待办事项失败, meetingId: {}", id, e);
                vo.setAiTodos(new ArrayList<>());
            }
            // 【关键】：清空主表中的原始 JSON 字符串，防止传给前端造成类型混乱
            meeting.setAiTodos(null);
        } else {
            vo.setAiTodos(new ArrayList<>()); // 如果没有待办，给个空数组防空指针
        }

        vo.setMeeting(meeting);

        // 3. 获取逐字稿 (之前写的 getByMeetingId 方法)
        vo.setContents(contentService.getByMeetingId(id));

        // 4. 获取章节列表 (MP Lambda查询)
        vo.setAgendas(agendaService.list(new LambdaQueryWrapper<BizMeetingAgenda>()
                .eq(BizMeetingAgenda::getMeetingId, id)
                .orderByAsc(BizMeetingAgenda::getTimestamp)));

        // 5. 获取敏感词：合并通用敏感词库(ID=1) 与 当前会议专属主题库，供前端渲染红色高亮和实时计数
        Set<String> mergedSensitiveWords = new java.util.HashSet<>();

        // 5.1 获取通用敏感词库的敏感词 (type = 2)
        List<String> generalWords = sysHotWordService.getAllSensitiveWords(GLOBAL_SENSITIVE_LIBRARY_ID);
        if (generalWords != null) {
            mergedSensitiveWords.addAll(generalWords);
        }

        // 5.2 获取该会议专属主题库下的敏感词
        Long topicId = meeting.getTopicLibraryId();
        if (topicId != null && !topicId.equals(GLOBAL_SENSITIVE_LIBRARY_ID)) {
            List<String> topicWords = sysHotWordService.getAllSensitiveWords(topicId);
            if (topicWords != null) {
                mergedSensitiveWords.addAll(topicWords);
            }
        }

        // 5.3 转换为 List 返回给前端
        vo.setSensitiveWords(new ArrayList<>(mergedSensitiveWords));

        return Result.success(vo);
    }

    /**
     * 将 AI 提取的待办确认导入到用户的待办四象限中
     */
    @PostMapping("/import-todos")
    public Result<Void> importTodos(@Validated @RequestBody MeetingTodoImportDTO dto) {
        // 假设你使用 SecurityUtils 获取当前登录用户 ID
        Long currentUserId = SecurityUtils.getCurrentUserId();
        meetingService.importTodos(dto, currentUserId);
        return Result.success(null);
    }

    /**
     * 全局保存会议一切内容 (摘要、待办、逐字稿、章节)
     */
    @PutMapping("/{id}/save")
    public Result<Void> saveMeetingData(@PathVariable Long id, @RequestBody MeetingSaveDTO dto) {
        // 直接调用 Service 层的事务级全局保存方法
        meetingService.globalSaveMeeting(id, dto);
        return Result.success(null);
    }

    /**
     * 删除会议及所有关联数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteMeeting(@PathVariable Long id) {
        meetingService.deleteMeetingCascade(id);
        return Result.success(null);
    }

    /**
     * 轻量级接口：专门用于实时更新会议的敏感词总数
     */
    @PutMapping("/{id}/sensitive-count")
    public Result<?> updateSensitiveCount(@PathVariable Long id, @RequestParam Integer count) {
        BizMeeting meeting = new BizMeeting();
        meeting.setId(id);
        meeting.setSensitiveWordCount(count);
        meetingService.updateById(meeting);
        return Result.success(null);
    }
}