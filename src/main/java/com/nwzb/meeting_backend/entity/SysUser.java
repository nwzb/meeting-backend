package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * 密码建议存储加密后的密文
     */
    private String password;

    /**
     * 角色: 1-普通用户, 2-运维管理员, 3-审计管理员, 9-超级管理员
     */
    private Integer role;

    private String avatar;

    private LocalDateTime createTime;
}