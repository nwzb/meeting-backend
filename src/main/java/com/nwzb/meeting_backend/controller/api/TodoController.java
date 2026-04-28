package com.nwzb.meeting_backend.controller.api;

import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.model.dto.TodoDTO;
import com.nwzb.meeting_backend.model.vo.TodoVO;
import com.nwzb.meeting_backend.service.BizTodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todo")
@RequiredArgsConstructor
@Validated
public class TodoController {

    private final BizTodoService todoService;

    /**
     * 获取当前用户的待办树形列表
     */
    @GetMapping("/list")
    public Result<List<TodoVO>> getTodoList() {
        // 直接返回所有待办清单，前端可以通过 computed 属性自动过滤分发到 4 个象限中
        List<TodoVO> list = todoService.getAllTodos(SecurityUtils.getUserId());
        return Result.success(list);
    }

    /**
     * 批量更新（主要用于拖拽排序或四象限移动）
     */
    @PutMapping("/batch")
    public Result<String> batchUpdateTodos(@RequestBody List<TodoDTO> dtoList) {
        todoService.batchUpdateTodos(dtoList, SecurityUtils.getUserId());
        return Result.success(null);
    }

    /**
     * 新增待办
     */
    @PostMapping
    public Result<String> addTodo(@Validated @RequestBody TodoDTO dto) {
        todoService.saveTodo(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    /**
     * 更新待办内容、提醒时间等
     */
    @PutMapping
    public Result<String> updateTodo(@Validated @RequestBody TodoDTO dto) {
        todoService.updateTodo(dto, SecurityUtils.getUserId());
        return Result.success(null);
    }

    /**
     * 删除待办及其子待办
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTodo(@PathVariable Long id) {
        todoService.deleteTodo(id, SecurityUtils.getUserId());
        return Result.success(null);
    }
}
