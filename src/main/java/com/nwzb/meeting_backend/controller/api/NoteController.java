package com.nwzb.meeting_backend.controller.api;

import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.model.dto.NoteCollectionDTO;
import com.nwzb.meeting_backend.model.dto.NoteDTO;
import com.nwzb.meeting_backend.model.vo.NoteTreeVO;
import com.nwzb.meeting_backend.model.vo.NoteVO;
import com.nwzb.meeting_backend.service.BizNoteCollectionService;
import com.nwzb.meeting_backend.service.BizNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/note")
@RequiredArgsConstructor
public class NoteController {

    private final BizNoteService noteService;
    private final BizNoteCollectionService collectionService;

    // ================= 分类管理 =================

    @PostMapping("/collection")
    public Result<String> addCollection(@Validated @RequestBody NoteCollectionDTO dto) {
        collectionService.addCollection(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    @PutMapping("/collection")
    public Result<String> updateCollection(@Validated @RequestBody NoteCollectionDTO dto) {
        collectionService.updateCollection(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    @DeleteMapping("/collection/{id}")
    public Result<String> deleteCollection(@PathVariable Long id) {
        collectionService.deleteCollection(id, SecurityUtils.getUserId());
        return Result.success(null);
    }

    // ================= 笔记管理 =================

    @GetMapping("/tree")
    public Result<List<NoteTreeVO>> getNoteTree() {
        List<NoteTreeVO> tree = noteService.getNoteTree(SecurityUtils.getUserId());
        return Result.success(tree);
    }

    @GetMapping("/{id}")
    public Result<NoteVO> getNoteDetail(@PathVariable Long id) {
        NoteVO vo = noteService.getNoteDetail(id, SecurityUtils.getUserId());
        return Result.success(vo);
    }

    @PostMapping
    public Result<String> addNote(@Validated @RequestBody NoteDTO dto) {
        noteService.saveNote(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    @PutMapping
    public Result<String> updateNote(@Validated @RequestBody NoteDTO dto) {
        noteService.updateNote(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id, SecurityUtils.getUserId());
        return Result.success(null);
    }
}
