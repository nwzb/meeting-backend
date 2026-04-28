package com.nwzb.meeting_backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.entity.SysHotWord;
import com.nwzb.meeting_backend.entity.SysTopicLibrary;
import com.nwzb.meeting_backend.model.dto.TopicDTO;
import com.nwzb.meeting_backend.model.dto.WordDTO;
import com.nwzb.meeting_backend.model.vo.WordVO;
import com.nwzb.meeting_backend.service.SysHotWordService;
import com.nwzb.meeting_backend.service.SysTopicLibraryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 审计管理 - 词库与热词控制器
 * 拦截器或Security层需确保只有 role=3 或 role=9 可访问此路径 (/admin/audit/**)
 */
@RestController
@RequestMapping("/api/admin/audit/word")
public class SysWordController {

    @Autowired
    private SysTopicLibraryService topicLibraryService;

    @Autowired
    private SysHotWordService hotWordService;

    // ==================== 主题词库管理 ====================

    @GetMapping("/topic/list")
    public Result<Page<SysTopicLibrary>> getTopicList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<SysTopicLibrary> page = new Page<>(current, size);
        return Result.success(topicLibraryService.page(page));
    }

    @PostMapping("/topic/save")
    public Result<?> saveTopic(@RequestBody @Validated TopicDTO dto) {
        SysTopicLibrary topic = new SysTopicLibrary();
        BeanUtils.copyProperties(dto, topic);
        boolean success = dto.getId() == null ? topicLibraryService.save(topic) : topicLibraryService.updateById(topic);
        return success ? Result.success(null) : Result.error("保存主题库失败");
    }

    @DeleteMapping("/topic/{id}")
    public Result<?> deleteTopic(@PathVariable Long id) {
        // 注意：实际业务中应检查该主题库下是否有绑定的热词或会议，这里走简单物理/逻辑删除
        return topicLibraryService.removeById(id) ? Result.success(null) : Result.error("删除失败");
    }

    // ==================== 热词/敏感词管理 ====================

    @GetMapping("/list")
    public Result<Page<WordVO>> getWordList( // 注意这里改成了 WordVO
                                             @RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @RequestParam(required = false) Long libraryId,
                                             @RequestParam(required = false) Integer type,
                                             @RequestParam(required = false) String keyword) {

        Page<SysHotWord> page = new Page<>(current, size);

        Page<WordVO> voPage = hotWordService.getWordVOPage(page, libraryId, type, keyword);
        return Result.success(voPage);
    }

    @PostMapping("/save")
    public Result<?> saveWord(@RequestBody @Validated WordDTO dto) {
        SysHotWord word = new SysHotWord();
        BeanUtils.copyProperties(dto, word);
        boolean success = dto.getId() == null ? hotWordService.save(word) : hotWordService.updateById(word);
        return success ? Result.success(null) : Result.error("保存词汇失败");
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteWord(@PathVariable Long id) {
        return hotWordService.removeById(id) ? Result.success(null) : Result.error("删除失败");
    }
}