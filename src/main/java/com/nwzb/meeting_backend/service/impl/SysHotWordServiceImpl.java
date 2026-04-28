package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.entity.SysHotWord;
import com.nwzb.meeting_backend.entity.SysTopicLibrary;
import com.nwzb.meeting_backend.mapper.SysHotWordMapper;
import com.nwzb.meeting_backend.model.vo.WordVO;
import com.nwzb.meeting_backend.service.SysHotWordService;
import com.nwzb.meeting_backend.service.SysTopicLibraryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysHotWordServiceImpl extends ServiceImpl<SysHotWordMapper, SysHotWord> implements SysHotWordService {

    @Autowired
    private SysTopicLibraryService topicLibraryService;

    @Override
    public List<String> getAllHotWords(Long libraryId) {
        // 调用我们自定义的随机 SQL
        return this.baseMapper.selectAllHotWords(libraryId);
    }

    @Override
    public List<String> getAllSensitiveWords(Long libraryId) {
        // 调用我们自定义的随机 SQL
        return this.baseMapper.selectAllSensitiveWords(libraryId);
    }

    @Override
    public Page<WordVO> getWordVOPage(Page<SysHotWord> page, Long libraryId, Integer type, String keyword) {
        // 1. 构建基础查询条件
        QueryWrapper<SysHotWord> wrapper = new QueryWrapper<>();
        if (libraryId != null) {
            wrapper.eq("library_id", libraryId);
        }
        if (type != null) {
            wrapper.eq("type", type);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("word", keyword);
        }
        wrapper.orderByDesc("create_time");

        // 2. 执行底层分页查询
        Page<SysHotWord> entityPage = this.page(page, wrapper);

        // 3. 提取所有关联的 library_id 集合
        List<Long> libraryIds = entityPage.getRecords().stream()
                .map(SysHotWord::getLibraryId)
                .distinct()
                .collect(Collectors.toList());

        // 4. 批量查询词库信息，并转为 Map<ID, Name> 以供快速匹配
        Map<Long, String> libraryNameMap = null;
        if (!libraryIds.isEmpty()) {
            List<SysTopicLibrary> libraries = topicLibraryService.listByIds(libraryIds);
            libraryNameMap = libraries.stream()
                    .collect(Collectors.toMap(SysTopicLibrary::getId, SysTopicLibrary::getName));
        }

        // 5. 将 Entity 转换为 VO，并组装 libraryName
        Map<Long, String> finalLibraryNameMap = libraryNameMap;
        List<WordVO> voList = entityPage.getRecords().stream().map(entity -> {
            WordVO vo = new WordVO();
            BeanUtils.copyProperties(entity, vo);
            if (finalLibraryNameMap != null && finalLibraryNameMap.containsKey(entity.getLibraryId())) {
                vo.setLibraryName(finalLibraryNameMap.get(entity.getLibraryId()));
            } else {
                vo.setLibraryName("未知词库");
            }
            return vo;
        }).collect(Collectors.toList());

        // 6. 构造新的分页结果返回
        Page<WordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }
}