package com.halversondm.flowableapp.process;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/processes")
public class ProcessController {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public ProcessController(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @PostMapping("/{processKey}/start")
    public ResponseEntity<Map<String, String>> startProcess(
            @PathVariable String processKey,
            @RequestBody(required = false) Map<String, Object> variables) {

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                processKey, variables != null ? variables : Map.of());

        return ResponseEntity.ok(Map.of(
                "processInstanceId", instance.getId(),
                "processDefinitionId", instance.getProcessDefinitionId()
        ));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, String>>> getOpenTasks(
            @RequestParam(required = false) String assignee) {

        var query = taskService.createTaskQuery();
        if (assignee != null) {
            query = query.taskAssignee(assignee);
        }

        List<Map<String, String>> tasks = query.list().stream()
                .map(this::toMap)
                .toList();

        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> variables) {

        taskService.complete(taskId, variables != null ? variables : Map.of());
        return ResponseEntity.noContent().build();
    }

    private Map<String, String> toMap(Task task) {
        return Map.of(
                "id", task.getId(),
                "name", task.getName() != null ? task.getName() : "",
                "assignee", task.getAssignee() != null ? task.getAssignee() : "",
                "processInstanceId", task.getProcessInstanceId()
        );
    }
}
