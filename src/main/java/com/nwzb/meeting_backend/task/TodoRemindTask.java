package com.nwzb.meeting_backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nwzb.meeting_backend.entity.BizTodo;
import com.nwzb.meeting_backend.mapper.BizTodoMapper;
import com.nwzb.meeting_backend.websocket.MeetingWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TodoRemindTask {

    private final BizTodoMapper todoMapper;
    private final MeetingWebSocketServer webSocketServer;

    @Scheduled(cron = "0 * * * * ?")
    public void executeTodoReminder() {
        // 1. 获取当前时间（精准到分钟）
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        log.info("【待办提醒任务】开始扫描，当前周期点: {}", now);

        // 2. 查询候选集：未完成 + 开启提醒 + 初始提醒时间 <= 当前时间
        // 注意：这里不再用 DATE_FORMAT 匹配分钟，而是查询所有“已经开始”的闹钟
        List<BizTodo> candidates = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getStatus, 0)
                .ne(BizTodo::getRemindType, 0)
                .isNotNull(BizTodo::getRemindTime)
                .le(BizTodo::getRemindTime, now)
        );

        if (candidates.isEmpty()) return;

        for (BizTodo todo : candidates) {
            if (shouldRemindNow(todo, now)) {
                sendRemind(todo);
            }
        }
    }

    /**
     * 核心数学校验逻辑：判断当前时间是否符合该待办的周期规律
     */
    private boolean shouldRemindNow(BizTodo todo, LocalDateTime now) {
        LocalDateTime start = todo.getRemindTime();

        // A. 基础校验：时和分必须匹配（闹钟的基本要求）
        if (start.getHour() != now.getHour() || start.getMinute() != now.getMinute()) {
            return false;
        }

        // B. 周期校验
        long daysBetween = ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate());

        switch (todo.getRemindType()) {
            case 1: // 单次提醒：必须是同一天
                return daysBetween == 0;
            case 2: // 每天提醒：只要时分对上就行（daysBetween >= 0 已经在 SQL le 条件中保证）
                return true;
            case 3: // 每周提醒：日期差必须是 7 的倍数
                return daysBetween % 7 == 0;
            case 4: // 每月提醒：日期（几号）必须一致
                // 细节：处理 31 号问题，如果 start 是 31 号，但本月只有 30 天，这里可能需要更复杂的逻辑
                // 简单处理：直接比对 getDayOfMonth
                return start.getDayOfMonth() == now.getDayOfMonth();
            default:
                return false;
        }
    }

    private void sendRemind(BizTodo todo) {
        log.info("【待办提醒】推送中 -> 用户: {}, 内容: {}", todo.getUserId(), todo.getTitle());
        String message = String.format(
                "{\"type\": \"TODO_REMIND\", \"title\": \"待办提醒\", \"content\": \"%s\"}",
                todo.getTitle()
        );
        webSocketServer.sendToUser(todo.getUserId(), message);

        // 可选：如果是单次提醒(type=1)，提醒完后关闭提醒，防止数据库一直查询出这条记录
        if (todo.getRemindType() == 1) {
            todo.setRemindType(0);
            todoMapper.updateById(todo);
        }
    }
}