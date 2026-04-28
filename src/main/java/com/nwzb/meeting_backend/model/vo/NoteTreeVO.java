package com.nwzb.meeting_backend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class NoteTreeVO {
    // 分类文件夹的ID (如果为0或null，可代表"默认分类")
    private Long collectionId;

    // 分类名称
    private String collectionName;

    // 排序号
    private Integer sortOrder;

    // 该分类下的所有笔记列表
    private List<NoteVO> notes;
}
