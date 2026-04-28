package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizTodo;
import com.nwzb.meeting_backend.model.dto.TodoDTO;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import com.nwzb.meeting_backend.model.vo.TodoVO;
import java.util.List;

public interface BizTodoService extends IService<BizTodo> {
    // 获取该用户的所有待办（前端拿回去后自己按四象限拼装，这样最灵活）
    List<TodoVO> getAllTodos(Long userId);
    void batchUpdateTodos(List<TodoDTO> list, Long userId);

    void saveTodo(TodoDTO dto, Long userId);
    void updateTodo(TodoDTO dto, Long userId);
    void deleteTodo(Long id, Long userId);
    List<GlobalSearchVO> searchGlobal(Long userId, String keyword);
}
