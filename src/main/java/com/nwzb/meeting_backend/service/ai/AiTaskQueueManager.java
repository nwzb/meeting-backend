package com.nwzb.meeting_backend.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.mapper.BizMeetingMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 任务调度器：串行管控、红绿灯、防爆显存 (增强版)
 */
@Component
@Slf4j
public class AiTaskQueueManager {

    @Autowired
    private AiRequestService aiRequestService;

    // 降维使用 Mapper，打破与 BizMeetingService 的循环依赖
    @Autowired
    private BizMeetingMapper meetingMapper;

    // ================== 任务包装类，区分平级任务(上传会议和重新生成纪要） ==================
    public static class AiTaskWrapper {
        public BizMeeting meeting;
        public String taskType; // "UPLOAD" 或 "REGENERATE"

        public AiTaskWrapper(BizMeeting meeting, String taskType) {
            this.meeting = meeting;
            this.taskType = taskType;
        }
    }

    // 待处理任务队列
    private final BlockingQueue<AiTaskWrapper> taskQueue = new LinkedBlockingQueue<>();

    // 当前是否有任务正在 Python 端运行 (显存锁)
    private volatile boolean isAiBusy = false;

    // 记录 Python 忙碌的轮询次数，用于防死锁判定
    private int busyCheckCount = 0;

    // 记录 AI 引擎最近一次变为空闲的时间戳
    private volatile long aiIdleStartTime = System.currentTimeMillis();

    /**
     * 服务启动时，捞取数据库中意外中断的任务，重新入队
     */
    @PostConstruct
    public void init() {
        log.info(">>> 初始化 AI 任务调度器，检查是否有遗留排队任务...");
        LambdaQueryWrapper<BizMeeting> queryWrapper = new LambdaQueryWrapper<>();
        // 状态 0(上传中), 1(排队中) 属于原上传任务
        queryWrapper.in(BizMeeting::getStatus, 0, 1).orderByAsc(BizMeeting::getCreateTime);
        List<BizMeeting> pendingMeetings = meetingMapper.selectList(queryWrapper);

        if (pendingMeetings != null && !pendingMeetings.isEmpty()) {
            for (BizMeeting meeting : pendingMeetings) {
                meeting.setStatus(1);
                meetingMapper.updateById(meeting);
                // 初始化时恢复的全是上传任务
                taskQueue.offer(new AiTaskWrapper(meeting, "UPLOAD"));
            }
            log.info("<<< 成功恢复 {} 个遗留排队任务", pendingMeetings.size());
        }
    }

    /**
     * 将任务加入排队序列，并更新数据库状态
     * @param meeting 会议实体
     * @param taskType 任务类型："UPLOAD" 或 "REGENERATE"
     */
    public void enqueueTask(BizMeeting meeting, String taskType) {
        // 无论是上传还是重新生成，进入队列后统一都是 1-排队中 的状态
        meeting.setStatus(1);
        meetingMapper.updateById(meeting);

        taskQueue.offer(new AiTaskWrapper(meeting, taskType));
        log.info("任务 [{}] 已加入排队，类型: {}, 当前队列长度: {}", meeting.getId(), taskType, taskQueue.size());
    }

    /**
     * 每 5 秒扫描一次队列 (红绿灯轮询 + 防死锁探活)
     */
    @Scheduled(fixedDelay = 5000)
    public void processQueue() {
        if (isAiBusy) {
            busyCheckCount++;
            if (busyCheckCount >= 12) {
                try {
                    boolean actualRunningStatus = aiRequestService.checkPythonIsRunning();
                    if (!actualRunningStatus) {
                        log.error("!!! 发现幽灵锁，强制释放显存锁。");
                        this.releaseLock();
                    } else {
                        busyCheckCount = 0;
                    }
                } catch (Exception e) {
                    log.error("探活 Python 异常", e);
                }
            }
            return;
        }

        AiTaskWrapper nextTask = taskQueue.poll();
        if (nextTask != null) {
            isAiBusy = true;
            busyCheckCount = 0;
            BizMeeting meeting = nextTask.meeting;

            log.info(">>> 显存空闲，开始下发任务: {}, 类型: {}", meeting.getId(), nextTask.taskType);
            try {
                if ("UPLOAD".equals(nextTask.taskType)) {
                    // 改变状态为 2-ASR识别中
                    meeting.setStatus(2);
                    meetingMapper.updateById(meeting);
                    aiRequestService.postUploadTask(meeting, "");
                } else if ("REGENERATE".equals(nextTask.taskType)) {
                    // 改变状态为 3-LLM总结中
                    meeting.setStatus(3);
                    meetingMapper.updateById(meeting);
                    aiRequestService.sendRegenerateSummaryRequest(meeting.getId());
                }
            } catch (Exception e) {
                log.error("下发任务失败", e);
                meeting.setStatus(9); // 9-失败
                meetingMapper.updateById(meeting);
                this.releaseLock();
            }
        } else {
            // 队列为空，检查是否空闲超过 3 分钟 (180,000 毫秒)
            if (System.currentTimeMillis() - aiIdleStartTime >= 3 * 60 * 1000) {

                // 假设 status=4 代表“已完成”。去捞取一个已完成且尚未向量化的会议
                BizMeeting unVectorizedMeeting = meetingMapper.selectOne(
                        new LambdaQueryWrapper<BizMeeting>()
                                .eq(BizMeeting::getStatus, 4)
                                .eq(BizMeeting::getIsVectorized, 0) // 需要在数据库和实体类加这个字段
                                .last("LIMIT 1")
                );

                if (unVectorizedMeeting != null) {
                    isAiBusy = true; // 上锁，防止期间有其他任务进入抢占
                    busyCheckCount = 0;
                    log.info(">>> AI空闲超过3分钟，触发静默向量化任务，会议ID: {}", unVectorizedMeeting.getId());
                    try {
                        // 注意：这里调用的发送方法内部最好使用 @Async 异步发送，
                        // 防止 Python 端构建太久导致堵塞 Spring Boot 的定时任务线程
                        aiRequestService.sendRagBuildRequest(unVectorizedMeeting);
                    } catch (Exception e) {
                        log.error("触发向量化任务失败", e);
                        this.releaseLock();
                    }
                } else {
                    // 如果所有会议都已经向量化了，重置计时器，防止每隔 5 秒疯狂查数据库
                    aiIdleStartTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * 当收到 Python 的 FINAL_SUMMARY 或 ERROR 回调时，由 AiCallbackService 调用此方法释放锁
     */
    public void releaseLock() {
        log.info("<<< 收到释放信号，重置显存锁，队列将处理下一个任务");
        this.isAiBusy = false;
        this.busyCheckCount = 0;
        // 重置空闲计时器
        this.aiIdleStartTime = System.currentTimeMillis();
    }

    /**
     * ======== 供 RAG 全局问答 (Ask) 实时抢占锁 ========
     * 使用 synchronized 保证原子性，防止与定时器并发冲突
     * @return true=抢占成功, false=系统忙碌
     */
    public synchronized boolean tryAcquireLockForInstantTask() {
        if (this.isAiBusy) {
            return false;
        }
        this.isAiBusy = true;
        this.busyCheckCount = 0;
        log.info(">>> 实时问答任务抢占 AI 锁成功！暂缓后续队列任务下发。");
        return true;
    }
}