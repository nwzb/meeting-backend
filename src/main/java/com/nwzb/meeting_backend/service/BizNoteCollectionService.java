package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizNoteCollection;
import com.nwzb.meeting_backend.model.dto.NoteCollectionDTO;

public interface BizNoteCollectionService extends IService<BizNoteCollection> {
    void addCollection(NoteCollectionDTO dto, Long userId);
    void updateCollection(NoteCollectionDTO dto, Long userId);
    void deleteCollection(Long id, Long userId);
}
