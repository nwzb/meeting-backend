package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.dto.AiSummaryCallbackDTO;
import com.nwzb.meeting_backend.model.dto.MeetingSaveDTO;
import com.nwzb.meeting_backend.model.dto.MeetingTodoImportDTO;
import com.nwzb.meeting_backend.model.vo.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 会议主表服务接口
 */
public interface BizMeetingService extends IService<BizMeeting> {

    /**
     * 普通用户：获取会议分页列表 (带字典映射与排序)
     */
    Page<MeetingVO> getUserMeetingPage(Long userId, Integer pageNum, Integer pageSize, String keyword, String sortField, Boolean isAsc);

    /**
     * 初始化会议任务：保存文件并记录入库
     */
    BizMeeting initMeetingTask(MultipartFile file, String title, Long topicId, Long duration);

    /**
     * 异步通知 Python AI 引擎开始处理
     */
    void startAiProcess(BizMeeting meeting);


    /**
     * 更新 AI 总结结果 (解析 JSON 并存入数据库)
     */
    void updateMeetingSummaryByDTO(AiSummaryCallbackDTO summaryDTO);

    @Transactional(rollbackFor = Exception.class)
    void importTodos(MeetingTodoImportDTO dto, Long userId);

    @Transactional(rollbackFor = Exception.class)
    void globalSaveMeeting(Long id, MeetingSaveDTO dto);


    @Transactional(rollbackFor = Exception.class)
    void deleteMeetingCascade(Long meetingId);

    // 普通用户大屏
    DashboardStatsVO getDashboardStats(Long userId,String startDate, String endDate);

    //模糊搜索
    List<GlobalSearchVO> searchGlobal(Long userId, String keyword);

    // 运维大屏
    AdminOpsStatsVO getGlobalDashboardStats(String startDate, String endDate);

    // 全局会议日志的分页查询方法 (供运维使用，不区分用户）
    IPage<MeetingLogVO> getGlobalMeetingLogs(Integer current, Integer size, String keyword, String sortField, String sortOrder);

    // 分页获取所有会议（供审查使用，不区分用户）
    Page<MeetingAuditVO> getAuditMeetingPage(Page<BizMeeting> page, Integer auditStatus, String keyword, String sortField, Boolean isAsc);

    @Transactional(rollbackFor = Exception.class)
    void recalculateAllSensitiveWords();


}