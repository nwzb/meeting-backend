package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.SysHotWord;
import com.nwzb.meeting_backend.model.vo.WordVO;

import java.util.List;

public interface SysHotWordService extends IService<SysHotWord> {
    /**
     * 获取热词列表
     */
    List<String> getAllHotWords(Long libraryId);

    /**
     * 获取敏感词列表
     */
    List<String> getAllSensitiveWords(Long libraryId);

    /**
     * 获取带有词库名称的 热词/敏感词 分页列表
     * @param page 分页参数
     * @param libraryId 所属词库ID (可选)
     * @param type 类型 (可选)
     * @param keyword 模糊搜索词 (可选)
     * @return 封装了 libraryName 的 WordVO 分页数据
     */
    Page<WordVO> getWordVOPage(Page<SysHotWord> page, Long libraryId, Integer type, String keyword);
}