package com.nwzb.meeting_backend.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.vo.DashboardStatsVO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.service.BizMeetingService;
import com.nwzb.meeting_backend.service.BizNoteService;
import com.nwzb.meeting_backend.service.BizTodoService;
import com.nwzb.meeting_backend.service.ai.AiRequestService;
import com.nwzb.meeting_backend.service.ai.AiTaskQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Slf4j
public class DashboardController {

    @Autowired
    private BizMeetingService bizMeetingService;

    @Autowired
    private BizNoteService bizNoteService;

    @Autowired
    private BizTodoService bizTodoService;

    @Autowired
    private AiTaskQueueManager aiTaskQueueManager;

    @Autowired
    private AiRequestService aiRequestService;
    /**
     * 获取数据可视化大屏统计图表数据
     */
    @GetMapping("/stats")
    public Result<DashboardStatsVO> getDashboardStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId(); // 获取当前登录用户

        // 此处调用 BizMeetingService 中的聚合查询方法 (下一步我们将去实现它)
        DashboardStatsVO statsVO = bizMeetingService.getDashboardStats(currentUserId, startDate, endDate);

        return Result.success(statsVO);
    }

    /**
     * 全局模糊搜索（会议、笔记、待办）
     */
    @GetMapping("/search")
    public Result<List<GlobalSearchVO>> globalSearch(@RequestParam("keyword") String keyword) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<GlobalSearchVO> searchResults = new ArrayList<>();

        // 1. 搜索会议
        searchResults.addAll(bizMeetingService.searchGlobal(currentUserId, keyword));
        // 2. 搜索笔记
        searchResults.addAll(bizNoteService.searchGlobal(currentUserId, keyword));
        // 3. 搜索待办
        searchResults.addAll(bizTodoService.searchGlobal(currentUserId, keyword));

        // 按创建时间倒序排列返回给前端
        searchResults.sort(Comparator.comparing(GlobalSearchVO::getCreateTime).reversed());

        return Result.success(searchResults);
    }

    /**
     * ======== 全局 RAG 超级会议助手问答接口 ========
     */
    @PostMapping("/rag/ask")
    public Result<?> askRag(@RequestBody Map<String, Object> payload) {
        String question = (String) payload.get("question");
        if (question == null || question.trim().isEmpty()) {
            return Result.error("提问内容不能为空");
        }

        boolean deepSearch = Boolean.TRUE.equals(payload.get("deepSearch"));

        // 1. 第一道防线：尝试在 Java 端抢占 AI 引擎显存锁
        if (!aiTaskQueueManager.tryAcquireLockForInstantTask()) {
            log.warn(">>> RAG 问答被拦截：Java 端锁已被占用 (ASR或总结任务正在运行)");
            return Result.error("AI 助手正在全力处理会议文件，显存繁忙，请稍后再问。");
        }

        try {
            Long currentUserId = SecurityUtils.getCurrentUserId();

            // 2. 查库：获取该用户所有的已完成且已向量化的会议 (is_vectorized = 1)
            List<BizMeeting> vectorizedMeetings = bizMeetingService.list(
                    new LambdaQueryWrapper<BizMeeting>()
                            .eq(BizMeeting::getUserId, currentUserId)
                            .eq(BizMeeting::getIsVectorized, 1)
            );

            if (vectorizedMeetings == null || vectorizedMeetings.isEmpty()) {
                return Result.error("您尚未拥有已完成智能分析的历史会议，暂时无法提供问答服务。");
            }

            // 3. 数据包装：为 Python 端剥离出干净的元数据 (ID, 名称, 时间)
            List<Map<String, Object>> meetingList = new ArrayList<>();
            for (BizMeeting m : vectorizedMeetings) {
                Map<String, Object> map = new HashMap<>();
                map.put("meetingId", m.getId().toString());
                map.put("meetingName", m.getTitle());
                map.put("meetingTime", m.getCreateTime() != null ? m.getCreateTime().toString() : "未知时间");
                meetingList.add(map);
            }

            // 4. 发送 HTTP 请求并等待大模型返回 (同步阻塞，交由底层的超时配置守护)
            return aiRequestService.sendRagAskRequest(question, meetingList, deepSearch);

        } catch (Exception e) {
            log.error("RAG 全局问答接口执行异常", e);
            return Result.error("系统异常，无法调用 AI 问答引擎");
        } finally {
            // 5. 绝对铁律：无论大模型报错还是成功，此块代码一定会执行！将锁归还给调度队列。
            log.info("<<< RAG 问答流程结束，归还显存锁。");
            aiTaskQueueManager.releaseLock();
        }
    }
}