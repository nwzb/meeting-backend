package com.nwzb.meeting_backend.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import com.nwzb.meeting_backend.mapper.BizMeetingContentMapper;
import com.nwzb.meeting_backend.mapper.BizMeetingMapper;
import com.nwzb.meeting_backend.service.SysHotWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiRequestService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SysHotWordService hotWordService;

    @Autowired
    private BizMeetingMapper meetingMapper;

    @Autowired
    private BizMeetingContentMapper contentMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // 解决与调度器的循环依赖，专门用于静默任务执行完毕后释放锁
    @Lazy
    @Autowired
    private AiTaskQueueManager aiTaskQueueManager;

    // 引入物理存储路径，用于将数据库的虚拟路径还原给 Python
    @Value("${file.upload-path}")
    private String uploadPath;

    private static final String PYTHON_API_URL = "http://127.0.0.1:8000/api/v1";

    /**
     * 调用 Python 上传并开始流水线
     */
    public void postUploadTask(BizMeeting meeting, String ignored) {
        // 1.热词提取
        List<String> words = hotWordService.getAllHotWords(meeting.getTopicLibraryId());

        // 将 List<String> 转换为逗号分隔的字符串
        String hotWordsStr = words != null ? String.join(",", words) : "";

        log.info(">>> 准备下发任务，提取到热词数量: {}", words != null ? words.size() : 0);

        // 2.核心路径拼接
        String fileName = meeting.getAudioUrl().replace("/uploads/", "");
        String baseDir = uploadPath.endsWith("/") ? uploadPath : uploadPath + "/";
        String absoluteAudioPath = baseDir + fileName;

        // 3.构造请求头与请求体 (显式指定 MediaType 防止 FastAPI 解析失败)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("meeting_id", meeting.getId().toString());
        body.add("audio_path", absoluteAudioPath);
        body.add("hot_words", hotWordsStr);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info(">>> 发送给 Python 的文件路径为: {}", absoluteAudioPath);
            restTemplate.postForEntity(PYTHON_API_URL + "/upload", request, String.class);
        } catch (Exception e) {
            log.error("调用 Python /upload 接口失败: {}", e.getMessage());
            throw new RuntimeException("AI引擎调用失败", e);
        }
    }

    /**
     * 调用 Python 请求插队生成总结
     */
    public Result<?> sendPartialSummaryRequest(Long meetingId) {
        String url = PYTHON_API_URL + "/request_partial_summary";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("meeting_id", String.valueOf(meetingId));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return Result.success(response.getBody());
        } catch (Exception e) {
            return Result.error("AI 服务插队失败: " + e.getMessage());
        }
    }

    /**
     * 探活：检查 Python 引擎是否真正在运行任务
     * 防止出现 Java 认为忙碌，但 Python 实际空闲的“幽灵锁”死锁状态。
     */
    public boolean checkPythonIsRunning() {
        String url = PYTHON_API_URL + "/status";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                // 根据 Python 端 /status 接口的返回值解析结构
                if (body.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    Object isRunning = data.get("isAiRunning");
                    if (isRunning instanceof Boolean) {
                        return (Boolean) isRunning;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("探活 Python 状态失败，可能 Python 服务已宕机: {}", e.getMessage());
            // 如果连不上 Python，说明 Python 挂了，自然也没有在运行任务，返回 false 以释放锁
            return false;
        }
    }

    /**
     * ======== 请求重新生成最终摘要 ========
     * 这是一个独立的 LLM 任务，不需要经过 ASR。
     */
    public Result<?> sendRegenerateSummaryRequest(Long meetingId) {
        BizMeeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) return Result.error("会议不存在");

        // 1. 拼装所有逐字稿文本
        List<BizMeetingContent> contents = contentMapper.selectList(
                new LambdaQueryWrapper<BizMeetingContent>()
                        .eq(BizMeetingContent::getMeetingId, meetingId)
                        .orderByAsc(BizMeetingContent::getStartTime)
        );

        if (contents == null || contents.isEmpty()) {
            return Result.error("未找到任何会议逐字稿，无法生成摘要");
        }

        StringBuilder fullTextBuilder = new StringBuilder();
        for (BizMeetingContent content : contents) {
            // 格式：[开始时间-结束时间] 说话人: 内容
            fullTextBuilder.append(String.format("[%s-%s] %s: %s\n",
                    content.getStartTime(), content.getEndTime(), content.getSpeaker(), content.getContent()));
        }

        // 2. 获取热词
        List<String> words = hotWordService.getAllHotWords(meeting.getTopicLibraryId());
        String hotWordsStr = words != null ? String.join(",", words) : "";

        // 3. 构造请求发送给 Python
        String url = PYTHON_API_URL + "/regenerate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("meeting_id", String.valueOf(meetingId));
        body.add("full_text", fullTextBuilder.toString());
        body.add("hot_words", hotWordsStr);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info(">>> 发送重新生成请求，文本长度: {}", fullTextBuilder.length());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return Result.success(response.getBody());
        } catch (Exception e) {
            log.error("请求重新生成失败", e);
            return Result.error("AI 服务请求失败: " + e.getMessage());
        }
    }

    /**
     * ======== 向 Python 发送强制中断任务信号 ========
     * 解决“孤儿任务”问题，防止 Python 在后台占用资源白跑
     */
    public void cancelAiTask(Long meetingId) {
        String url = PYTHON_API_URL + "/cancel_task";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("meeting_id", String.valueOf(meetingId));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info(">>> 正在向 Python 发送强制中断信号, 会议ID: {}", meetingId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(">>> Python 中断信号接收成功");
            }
        } catch (Exception e) {
            log.error(">>> 调用 Python /cancel_task 接口失败 (可能任务已完成或引擎宕机): {}", e.getMessage());
        }
    }

    /**
     * ======== 发起 RAG 向量化构建任务 (静默后台执行) ========
     * 必须标注 @Async 放入独立线程池执行，防止阻塞定时器调度
     */
    @Async
    public void sendRagBuildRequest(BizMeeting meeting) {
        try {
            log.info(">>> 开始为会议 [{}] 打包逐字稿发送至 Python 进行向量化...", meeting.getId());

            // 1. 获取该会议所有切片
            List<BizMeetingContent> contents = contentMapper.selectList(
                    new LambdaQueryWrapper<BizMeetingContent>()
                            .eq(BizMeetingContent::getMeetingId, meeting.getId())
                            .orderByAsc(BizMeetingContent::getStartTime)
            );

            if (contents == null || contents.isEmpty()) {
                log.warn(">>> 会议 [{}] 无逐字稿内容，跳过向量化", meeting.getId());
                return;
            }

            // 2. 将切片转为 JSON 字符串
            String chunksJson = objectMapper.writeValueAsString(contents);

            // 3. 构建会议指纹文本（名称 + 时长 + 关键词 + 摘要 + 待办）
            String fingerprintText = buildFingerprintText(meeting);

            // 4. 构建 JSON 格式请求体 (FastAPI 的 BaseModel 需要 application/json)
            String url = PYTHON_API_URL + "/rag/build";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("meeting_id", String.valueOf(meeting.getId()));
            requestBody.put("chunks_json", chunksJson);
            requestBody.put("fingerprint_text", fingerprintText);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // 4. 同步等待长耗时响应 (由 RestTemplateConfig 的 5分钟 超时保护)
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("<<< 会议 [{}] 向量化成功，更新数据库标识", meeting.getId());
                // 更新数据库标识 is_vectorized = 1
                meeting.setIsVectorized(1);
                meetingMapper.updateById(meeting);
            }

        } catch (Exception e) {
            log.error("!!! 会议 [{}] 向量化构建发生异常: {}", meeting.getId(), e.getMessage());
        } finally {
            // ★ 最重要的一步：无论成功还是失败，必须调用调度器释放锁！
            log.info("<<< 向量化流程结束，正在释放 AI 显存全局锁...");
            aiTaskQueueManager.releaseLock();
        }
    }

    /**
     * ======== 发起 RAG 全局问答请求 (前端实时交互) ========
     * @param question 用户提问
     * @param meetingList 包含 meetingId, meetingName, meetingTime 的会议元数据列表
     * @param deepSearch 是否启用超深度检索（跳过一级粗排）
     */
    public Result<?> sendRagAskRequest(String question, List<Map<String, Object>> meetingList, boolean deepSearch) {
        String url = PYTHON_API_URL + "/rag/ask";

        try {
            String meetingListJson = objectMapper.writeValueAsString(meetingList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);
            requestBody.put("meeting_list_json", meetingListJson);
            requestBody.put("deep_search", deepSearch);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info(">>> 发起 RAG 问答，提问：[{}]，关联会议数：{}", question, meetingList.size());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            // ★ 核心修复：防止数据套娃，解包 Python 的响应体
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                // 判断 Python 返回的业务 code 是否为 200
                Object pythonCode = responseBody.get("code");
                if (pythonCode != null && !pythonCode.toString().equals("200")) {
                    String errorMsg = (String) responseBody.get("message");
                    return Result.error(errorMsg != null ? errorMsg : "AI 引擎内部处理失败");
                }

                // 解包成功，提取真正的 data (包含 answer 和 sources) 返回给前端
                return Result.success(responseBody.get("data"));
            }

            return Result.error("AI 引擎返回了空数据");

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn(">>> RAG 问答被拒绝：AI 引擎正在忙碌 (HTTP 429)");
            return Result.error("AI 助手正在全力处理排队的会议文件，显存繁忙，请稍后再问。");
        } catch (Exception e) {
            log.error("RAG 问答请求失败", e);
            return Result.error("智能问答引擎响应失败: " + e.getMessage());
        }
    }

    /**
     * 构建会议指纹文本：拼接名称、时长、关键词、摘要、待办等高维核心信息
     */
    private String buildFingerprintText(BizMeeting meeting) {
        StringBuilder sb = new StringBuilder();
        sb.append("会议名称：").append(meeting.getTitle() != null ? meeting.getTitle() : "未知会议");

        Long duration = meeting.getDuration();
        if (duration != null && duration > 0) {
            sb.append(" | 时长：").append(duration / 60).append("分钟");
        } else {
            sb.append(" | 时长：未知");
        }

        if (meeting.getAiKeywords() != null && !meeting.getAiKeywords().isEmpty()) {
            sb.append(" | 关键词：").append(meeting.getAiKeywords());
        }

        if (meeting.getFullSummary() != null && !meeting.getFullSummary().isEmpty()) {
            String summary = meeting.getFullSummary();
            if (summary.length() > 200) {
                summary = summary.substring(0, 200);
            }
            sb.append(" | 摘要：").append(summary.replace("|", " ").replace("\n", " "));
        }

        if (meeting.getAiTodos() != null && !meeting.getAiTodos().isEmpty()) {
            sb.append(" | 待办事项：").append(meeting.getAiTodos());
        }

        return sb.toString();
    }
}