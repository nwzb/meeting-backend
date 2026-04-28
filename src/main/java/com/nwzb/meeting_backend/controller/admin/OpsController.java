package com.nwzb.meeting_backend.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.vo.AdminOpsStatsVO;
import com.nwzb.meeting_backend.model.vo.MeetingLogVO;
import com.nwzb.meeting_backend.model.vo.OpsMonitorVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ops")
public class OpsController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private com.nwzb.meeting_backend.service.BizMeetingService bizMeetingService;

    /**
     * 获取系统实时运行监控状态 (CPU, 内存, 显存以及各端连接情况)
     */
    @GetMapping("/system-status")
    public Result<OpsMonitorVO> getSystemStatus() {
        // Python 端的监控接口地址
        String pythonStatusUrl = "http://127.0.0.1:8000/api/v1/status";

        try {
            // 计时开始（Java到Python引擎的通信延迟）
            long startTime = System.currentTimeMillis();

            // 发起 GET 请求调用 Python 接口
            Map<String, Object> response = restTemplate.getForObject(pythonStatusUrl, Map.class);

            // 计时结束（Java到Python引擎的通信延迟）
            long endTime = System.currentTimeMillis();

            if (response != null && (Integer) response.get("code") == 200) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");

                OpsMonitorVO vo = new OpsMonitorVO();
                // 注意：从 Map 强转时要兼容不同类型的数字（Integer/Double）
                vo.setCpuUsage(((Number) data.get("cpuUsage")).doubleValue());
                vo.setRamUsage(((Number) data.get("ramUsage")).doubleValue());
                vo.setVramUsage(((Number) data.get("vramUsage")).doubleValue());
                vo.setIsAiRunning((Boolean) data.get("isAiRunning"));
                vo.setCurrentMeetingId((String) data.get("currentMeetingId"));

                // 记录网络延迟
                vo.setAiNetworkLatency(endTime - startTime);

                return Result.success(vo);
            }
            return Result.error("500, 解析 Python 状态数据失败");

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500, AI引擎离线或无法连接: " + e.getMessage());
        }
    }

    /**
     * 获取全平台数据大屏统计
     */
    @GetMapping("/stats")
    public Result<AdminOpsStatsVO> getGlobalStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        AdminOpsStatsVO stats = bizMeetingService.getGlobalDashboardStats(startDate, endDate);
        return Result.success(stats);
    }

    /**
     * 获取模型调用日志 (全平台会议分页列表)
     */
    @GetMapping("/meeting-logs")
    public Result<IPage<MeetingLogVO>> getGlobalMeetingLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {

        return Result.success(bizMeetingService.getGlobalMeetingLogs(current, size, keyword, sortField, sortOrder));
    }

    /**
     * 回溯底层 Python 端的异步执行日志 (不查库，直接传 Python 的文本)
     */
    @GetMapping("/meeting-logs/{meetingId}/detail")
    public Result<String> getMeetingLogDetail(@PathVariable String meetingId) {
        String pythonLogUrl = "http://127.0.0.1:8000/api/v1/logs/" + meetingId;

        try {
            Map<String, Object> response = restTemplate.getForObject(pythonLogUrl, Map.class);

            if (response != null && (Integer) response.get("code") == 200) {
                // 成功拿到文本内容
                return Result.success((String) response.get("data"));
            }
            return Result.error("404"+ (String) response.get("message")); // 日志文件不存在

        } catch (Exception e) {
            return Result.error("500, 无法连接 AI 引擎获取日志: " + e.getMessage());
        }
    }
}