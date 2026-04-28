-- 开启外键检查约束
SET FOREIGN_KEY_CHECKS = 0;


-- 1.+2.两个词库相关的表在其它sql语句中生成

-- 3.4.5.三个会议相关的表不额外插入测试数据

-- ----------------------------
-- 6.+7. 笔记与分类 (biz_note & biz_note_collection)
-- ----------------------------
-- 插入笔记分类 (biz_note_collection)
INSERT INTO `biz_note_collection` (`id`, `user_id`, `name`, `sort_order`) VALUES
(1, 1, '💡 灵感脑暴', 1),
(2, 1, '📝 会议复盘', 2);

-- 插入带排序优先级的笔记 (biz_note)
-- sort_order 越大越靠前 (或者根据你前端逻辑，越小越靠前)
INSERT INTO `biz_note` (`user_id`, `collection_id`, `title`, `content`, `is_top`, `sort_order`, `source_meeting_id`) VALUES
(1, 1, '2026 核心战略思考', '关于 AIGC 提效的几点想法...', 1, 999, NULL), -- 置顶且高优先级
(1, 1, '座舱 UI 改进灵感', '参考飞书的侧边栏交互...', 0, 10, NULL),
(1, 1, '待整理的碎碎念', '一些零散的音频记录...', 0, 1, NULL),
(1, 2, 'Q1 项目总结报告', '项目进展顺利，ASR 延迟降低...', 0, 5, 1);

-- ----------------------------
-- 8. 待办事项 (biz_todo)
-- ----------------------------
-- 插入 4 条主待办 (parent_id = 0)
-- 注意：sort_order 越小越靠前
INSERT INTO `biz_todo` (`id`, `user_id`, `source_meeting_id`, `title`, `status`, `priority_quadrant`, `deadline`, `parent_id`, `sort_order`) VALUES
    (1, 1, 1, '修复 WebSocket 掉线问题', 0, 1, '2026-03-10 18:00:00', 0, 1),
    (2, 1, 1, '撰写 Q2 规划文档', 0, 2, '2026-03-25 00:00:00', 0, 2),
    (3, 1, NULL, '取顺丰快递', 1, 3, NULL, 0, 3),
    (4, 1, NULL, '整理桌面文件', 0, 4, NULL, 0, 4);

-- 插入 3 条子待办 (全部关联到任务 1：修复 WebSocket)
-- 子待办也带上 source_meeting_id=1 和对应的 sort_order
INSERT INTO `biz_todo` (`user_id`, `source_meeting_id`, `title`, `status`, `priority_quadrant`, `parent_id`, `sort_order`) VALUES
(1, 1, '排查 ASR 识别切片时长的显存回收死锁', 0, 1, 1, 1),
(2, 1, '增加前端心跳重连机制 (5s/次)', 0, 1, 1, 2),
(3, 1, '优化后端 Session 过期清理逻辑', 1, 1, 1, 3);

SET FOREIGN_KEY_CHECKS = 1;