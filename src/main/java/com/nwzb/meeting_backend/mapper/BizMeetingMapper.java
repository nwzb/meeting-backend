package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.vo.AdminOpsStatsVO;
import com.nwzb.meeting_backend.model.vo.DashboardStatsVO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.model.vo.MeetingLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BizMeetingMapper extends BaseMapper<BizMeeting> {
    // 列表页查它：基本的 selectList 就够了，MyBatis-Plus 真香

    // === 普通用户统计图表 ===
    List<DashboardStatsVO.TrendData> selectMeetingTrend(@Param("userId") Long userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
    List<DashboardStatsVO.PieData> selectSpeakerStats(@Param("userId") Long userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
    List<DashboardStatsVO.PieData> selectTopicStats(@Param("userId") Long userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
    List<String> selectAllKeywords(@Param("userId") Long userId, @Param("startDate") String startDate, @Param("endDate") String endDate);

    // 普通用户全局模糊搜索
    List<GlobalSearchVO> searchGlobal(@Param("userId") Long userId, @Param("keyword") String keyword);

    // === 全平台统计图表 ===
    List<DashboardStatsVO.TrendData> selectGlobalMeetingTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<DashboardStatsVO.PieData> selectGlobalSpeakerStats(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<DashboardStatsVO.PieData> selectGlobalTopicStats(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<String> selectGlobalAllKeywords(@Param("startDate") String startDate, @Param("endDate") String endDate);

    AdminOpsStatsVO.ResourceStats selectGlobalResourceStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 连表查询会议日志 (带用户名和动态排序)
     */
    IPage<MeetingLogVO> selectMeetingLogsWithUser(IPage<MeetingLogVO> page,
                                                  @Param("keyword") String keyword,
                                                  @Param("sortField") String sortField,
                                                  @Param("sortOrder") String sortOrder);
}