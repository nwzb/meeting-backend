package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import com.nwzb.meeting_backend.mapper.BizMeetingContentMapper;
import com.nwzb.meeting_backend.model.dto.AiSliceCallbackDTO;
import com.nwzb.meeting_backend.service.BizMeetingContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal; // 必须导入
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BizMeetingContentServiceImpl extends ServiceImpl<BizMeetingContentMapper, BizMeetingContent> implements BizMeetingContentService {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void saveAsrSlices(Long meetingId, Object data) {
        List<Map<String, Object>> rawSlices = objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
        if (rawSlices == null) return;

        List<BizMeetingContent> contents = rawSlices.stream().map(map -> {
            BizMeetingContent content = new BizMeetingContent();
            content.setMeetingId(meetingId);
            // 修复 BigDecimal 转换问题
            content.setStartTime(new BigDecimal(map.get("start").toString()).divide(new BigDecimal("1000")));
            content.setEndTime(new BigDecimal(map.get("end").toString()).divide(new BigDecimal("1000")));
            content.setSpeaker(map.get("speaker").toString());
            content.setContent(map.get("text").toString());
            return content;
        }).collect(Collectors.toList());

        this.saveBatch(contents);
    }

    @Override
    public List<BizMeetingContent> getByMeetingId(Long meetingId) {
        return this.list(new LambdaQueryWrapper<BizMeetingContent>()
                .eq(BizMeetingContent::getMeetingId, meetingId)
                .orderByAsc(BizMeetingContent::getStartTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSingleSlice(AiSliceCallbackDTO sliceDTO) {
        BizMeetingContent content = new BizMeetingContent();
        content.setMeetingId(sliceDTO.getMeetingId());

        // 转换毫秒为秒，并处理 BigDecimal
        content.setStartTime(sliceDTO.getStart().divide(new BigDecimal("1000")));
        content.setEndTime(sliceDTO.getEnd().divide(new BigDecimal("1000")));

        content.setSpeaker(sliceDTO.getSpeaker());
        content.setContent(sliceDTO.getText());

        // 这里的 sliceIndex 可以通过 count(*) 自增，或者前端只按时间排序
        this.save(content);
    }
}