package com.nwzb.meeting_backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.dto.MeetingAuditDTO;
import com.nwzb.meeting_backend.model.vo.MeetingAuditVO;
import com.nwzb.meeting_backend.service.BizMeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 审查管理 - 会议审查控制器
 */
@RestController
@RequestMapping("/api/admin/audit/meeting")
public class AuditController {

    @Autowired
    private BizMeetingService bizMeetingService;

    /**
     * 分页获取所有会议（供审查使用，不区分用户）
     */
    @GetMapping("/list")
    public Result<Page<MeetingAuditVO>> getAuditMeetingList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(defaultValue = "false") Boolean isAsc) {

        Page<BizMeeting> page = new Page<>(current, size);
        Page<MeetingAuditVO> voPage = bizMeetingService.getAuditMeetingPage(page, auditStatus, keyword, sortField, isAsc);
        return Result.success(voPage);
    }

    /**
     * 更改会议审查状态（违规屏蔽 / 归档）
     */
    @PostMapping("/status")
    public Result<?> changeMeetingAuditStatus(@RequestBody @Validated MeetingAuditDTO dto) {
        BizMeeting meeting = bizMeetingService.getById(dto.getMeetingId());
        if (meeting == null) {
            return Result.error("会议不存在");
        }

        meeting.setAuditStatus(dto.getAuditStatus());
        // 如果是屏蔽(2)，必须记录原因；如果是归档(1)或恢复正常(0)，可清空或保留原因
        if (dto.getAuditStatus() == 2) {
            meeting.setAuditReason(dto.getAuditReason());
        } else if (dto.getAuditStatus() == 0) {
            meeting.setAuditReason("");
        }

        boolean success = bizMeetingService.updateById(meeting);
        return success ? Result.success(null) : Result.error("状态更新失败");
    }

    /**
     * 一键重算全平台所有会议敏感词
     */
    @PostMapping("/recalculate-sensitive-words")
    public Result<?> recalculateAllSensitiveWords() {
        bizMeetingService.recalculateAllSensitiveWords();
        return Result.success("敏感词重计完成");
    }
}