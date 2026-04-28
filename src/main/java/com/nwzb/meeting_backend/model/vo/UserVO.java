package com.nwzb.meeting_backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回给前端的用户视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private Integer role; // 1-用户, 2-运维, 3-审计, 9-超管

    private String avatar;

    private String token; // JWT 字符串
}