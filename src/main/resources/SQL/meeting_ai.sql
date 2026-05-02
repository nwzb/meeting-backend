SET FOREIGN_KEY_CHECKS = 0;

-- 1. 用户表：区分运维、审查、普通用户
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `role` tinyint(4) DEFAULT '1' COMMENT '角色: 1-普通用户, 2-运维管理员, 3-审查管理员, 9-超级管理员 0-封禁用户',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 直接在数据库中插入超级管理员 (role = 9)
-- 密码明文为: 123456
INSERT INTO `sys_user` (
    `username`,
    `password`,
    `role`,
    `avatar`,
    `create_time`
) VALUES (
             'admin',
             '$2a$10$cUW0JrBLPnbkC3O6kmAnreVdZciwm3fsEwLSQjietWXJsrNIUJ50a',
             9,
             'https://api.dicebear.com/7.x/avataaars/svg?seed=Admin',
             NOW()
         );


-- 2. 热词/敏感词表：给AI做修正和审查用
DROP TABLE IF EXISTS `sys_hot_word`;
CREATE TABLE `sys_hot_word` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `library_id` bigint(20) NOT NULL DEFAULT '1' COMMENT '所属词库ID',
  `word` varchar(100) NOT NULL COMMENT '热词/敏感词',
  `type` tinyint(4) DEFAULT '1' COMMENT '类型: 1-热词修正(ASR/LLM用), 2-敏感词(审查屏蔽用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_library` (`library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分主题的热词/敏感词表';

-- 3. 主题库表：用来管理“IT类”、“金融类”、“医疗类”这些选项。
DROP TABLE IF EXISTS `sys_topic_library`;
CREATE TABLE `sys_topic_library` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '词库名称 (如: 互联网, 金融, 法律)',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `is_public` tinyint(1) DEFAULT '1' COMMENT '是否公开: 1-是, 0-仅管理员可见',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词库配置';
-- ★ 插入系统级通用敏感词库 (固定 ID=1)
INSERT INTO `sys_topic_library` (`id`, `name`, `description`, `is_public`, `create_time`)
VALUES (1, '系统通用敏感词库', '存放全平台全局生效的违规屏蔽词 (系统内置，不可删除)', 0, NOW());

-- 4. 会议主表：列表页就查它
DROP TABLE IF EXISTS `biz_meeting`;
CREATE TABLE `biz_meeting` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '所属用户',
  `title` varchar(255) NOT NULL DEFAULT '未命名会议' COMMENT '会议标题',
  `topic_library_id` bigint(20) DEFAULT '1' COMMENT '所选主题词库ID',
  `audio_url` varchar(500) DEFAULT NULL COMMENT '音频文件路径',
  `duration` bigint(20) DEFAULT '0' COMMENT '时长(秒)',
  `asr_duration` INT DEFAULT 0 COMMENT 'ASR识别总耗时(秒)',
  `llm_duration` INT DEFAULT 0 COMMENT 'LLM总结总耗时(秒)',

  -- 核心字段1：AI处理流程状态 (机器管)
  `status` tinyint(4) DEFAULT '0' COMMENT 'AI流程状态: 0-上传中, 1-排队中, 2-ASR识别中, 3-LLM总结中, 4-完成, 9-失败',
  
  -- 核心字段2：审查管控状态 (人管)
  `audit_status` tinyint(4) DEFAULT '0' COMMENT '审查管控状态: 0-正常, 1-已归档(只读), 2-违规屏蔽(前端隐藏内容)',
  `audit_reason` varchar(255) DEFAULT NULL COMMENT '屏蔽原因',
  `sensitive_word_count` INT DEFAULT 0 COMMENT '命中的敏感词总数',

  `full_summary` mediumtext COMMENT 'AI生成的全文纪要(Markdown)',
  `ai_keywords` varchar(500) DEFAULT NULL COMMENT 'AI提取的关键词(逗号分隔)',
  `ai_todos` text COMMENT 'AI提取的待办事项(JSON数组格式)',
  `is_vectorized` tinyint(4) DEFAULT '0' COMMENT '是否已生成RAG向量: 0-否, 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_topic` (`topic_library_id`) -- 增加一个索引，方便按主题搜索
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议主表';

-- 5. 会议逐字稿表：ASR切片结果
DROP TABLE IF EXISTS `biz_meeting_content`;
CREATE TABLE `biz_meeting_content` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `meeting_id` bigint(20) NOT NULL COMMENT '关联会议ID',
  `slice_index` int(11) NOT NULL COMMENT '切片序号',
  `start_time` decimal(10,2) NOT NULL COMMENT '开始秒数',
  `end_time` decimal(10,2) NOT NULL COMMENT '结束秒数',
  `speaker` varchar(50) DEFAULT 'Speaker' COMMENT '说话人',
  `content` text COMMENT '识别文本',
  PRIMARY KEY (`id`),
  KEY `idx_meeting` (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议逐字稿';

-- 6. 会议章节表：AI分析出的议程
DROP TABLE IF EXISTS `biz_meeting_agenda`;
CREATE TABLE `biz_meeting_agenda` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `meeting_id` bigint(20) NOT NULL,
  `timestamp` decimal(10,2) DEFAULT NULL COMMENT '跳转时间戳',
  `title` varchar(255) DEFAULT NULL COMMENT '章节标题',
  `summary` text COMMENT '章节摘要',
  PRIMARY KEY (`id`),
  KEY `idx_meeting` (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议章节智能分析';

-- 7. 笔记分类表 (文件夹)
DROP TABLE IF EXISTS `biz_note_collection`;
CREATE TABLE `biz_note_collection` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `name` varchar(50) NOT NULL COMMENT '分类名称(如: 工作日志, 灵感)',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序优先级',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记分类集合';

-- 8. 笔记主表
DROP TABLE IF EXISTS `biz_note`;
CREATE TABLE `biz_note` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `collection_id` bigint(20) DEFAULT NULL COMMENT '所属分类ID',
  `title` varchar(255) DEFAULT '无标题笔记',
  `content` longtext COMMENT '笔记内容(支持富文本/HTML)',
  `is_top` tinyint(1) DEFAULT '0' COMMENT '是否置顶: 1-是, 0-否',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序优先级',
  `source_meeting_id` bigint(20) DEFAULT NULL COMMENT '关联源会议ID(如果是从会议导入的)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_collection` (`user_id`, `collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人笔记';

-- 9. 待办事项表
DROP TABLE IF EXISTS `biz_todo`;
CREATE TABLE `biz_todo` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `source_meeting_id` bigint(20) DEFAULT NULL COMMENT '关联源会议ID(如果是从会议导入的)',
  `title` varchar(255) NOT NULL COMMENT '待办内容',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态: 0-未完成, 1-已完成',
  `priority_quadrant` tinyint(4) DEFAULT '4' COMMENT '四象限: 1-重要紧急, 2-重要不紧急, 3-紧急不重要, 4-不重要不紧急',
  `deadline` datetime DEFAULT NULL COMMENT '截止时间',
  `remind_time` datetime DEFAULT NULL COMMENT '提醒时间',
  `remind_type` tinyint(4) DEFAULT '0' COMMENT '提醒方式: 0-不提醒, 1-单次, 2-每天, 3-每周, 4-每月',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父任务ID(支持子待办)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `sort_order` int(11) DEFAULT '0' COMMENT '排序优先级',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办事项';