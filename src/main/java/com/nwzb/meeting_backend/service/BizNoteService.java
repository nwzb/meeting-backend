package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizNote;
import com.nwzb.meeting_backend.model.dto.NoteDTO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.model.vo.NoteTreeVO;
import com.nwzb.meeting_backend.model.vo.NoteVO;

import java.util.List;

public interface BizNoteService extends IService<BizNote> {
    List<NoteTreeVO> getNoteTree(Long userId);
    void saveNote(NoteDTO dto, Long userId);
    void updateNote(NoteDTO dto, Long userId);
    void deleteNote(Long id, Long userId);
    NoteVO getNoteDetail(Long id, Long userId);
    List<GlobalSearchVO> searchGlobal(Long userId, String keyword);
}
