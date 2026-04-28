package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.entity.BizNote;
import com.nwzb.meeting_backend.entity.BizNoteCollection;
import com.nwzb.meeting_backend.mapper.BizNoteCollectionMapper;
import com.nwzb.meeting_backend.mapper.BizNoteMapper;
import com.nwzb.meeting_backend.model.dto.NoteCollectionDTO;
import com.nwzb.meeting_backend.service.BizNoteCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BizNoteCollectionServiceImpl extends ServiceImpl<BizNoteCollectionMapper, BizNoteCollection> implements BizNoteCollectionService {

    private final BizNoteMapper noteMapper;

    @Override
    public void addCollection(NoteCollectionDTO dto, Long userId) {
        BizNoteCollection collection = new BizNoteCollection();
        BeanUtils.copyProperties(dto, collection);
        collection.setUserId(userId);
        this.save(collection);
    }

    @Override
    public void updateCollection(NoteCollectionDTO dto, Long userId) {
        BizNoteCollection collection = this.getById(dto.getId());
        if (collection == null || !collection.getUserId().equals(userId)) {
            throw new CustomException(403, "无权修改或分类不存在");
        }
        collection.setName(dto.getName());
        collection.setSortOrder(dto.getSortOrder());
        this.updateById(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCollection(Long id, Long userId) {
        BizNoteCollection collection = this.getById(id);
        if (collection == null || !collection.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除或分类不存在");
        }
        // 删除分类前，将该分类下的笔记移入"默认分类" (即 collection_id 置为 null)
        noteMapper.update(null, Wrappers.<BizNote>lambdaUpdate()
                .set(BizNote::getCollectionId, null)
                .eq(BizNote::getCollectionId, id)
                .eq(BizNote::getUserId, userId));

        this.removeById(id);
    }
}
