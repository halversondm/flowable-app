package com.halversondm.flowableapp.process;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Processes", description = "Start and interact with running Flowable process instances. Process variables passed at start or task completion are stored in the engine and drive conditional flow in BPMN gateways.")
@RestController
@RequestMapping("/processes")
public class ProcessController {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public ProcessController(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Operation(
        summary = "Start a process instance",
        description = "Starts a new instance of the process identified by its definition key. " +
                      "The key matches the 'id' attribute on the <process> element in the BPMN XML. " +
                      "Optional process variables can be supplied in the request body and are available immediately to the first flow elements."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Process instance started successfully"),
        @ApiResponse(responseCode = "404", description = "No process definition found for the given key", content = @Content),
        @ApiResponse(responseCode = "500", description = "Flowable engine error", content = @Content)
    })
    @PostMapping("/{processKey}/start")
    public ResponseEntity<Map<String, String>> startProcess(
            @Parameter(description = "The process definition key (matches the 'id' attribute in the BPMN <process> element)", required = true, example = "order-approval")
            @PathVariable String processKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Optional map of process variables to pass at start. Keys become variable names accessible in expressions and service tasks.",
                content = @Content(mediaType = "application/json"))
            @RequestBody(required = false) Map<String, Object> variables) {

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                processKey, variables != null ? variables : Map.of());

        return ResponseEntity.ok(Map.of(
                "processInstanceId", instance.getId(),
                "processDefinitionId", instance.getProcessDefinitionId()
        ));
    }

    @Operation(
        summary = "List open user tasks",
        description = "Returns all active user tasks across all process instances. " +
                      "Optionally filter by assignee to retrieve tasks for a specific user. " +
                      "Only tasks in a running (non-suspended, non-completed) state are returned."
    )
    @ApiResponse(responseCode = "200", description = "List of open tasks")
    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, String>>> getOpenTasks(
            @Parameter(description = "Filter tasks by assignee username. Omit to return all open tasks.", example = "john.doe")
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

    @Operation(
        summary = "Complete a user task",
        description = "Marks the specified task as complete and advances the process to the next flow element. " +
                      "Completion variables are merged into the process instance scope and can influence downstream gateway conditions."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Task completed successfully"),
        @ApiResponse(responseCode = "404", description = "Task not found or already completed", content = @Content),
        @ApiResponse(responseCode = "500", description = "Flowable engine error", content = @Content)
    })
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeTask(
            @Parameter(description = "The Flowable task ID", required = true)
            @PathVariable String taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Optional variables to set on task completion. These are merged into the process instance scope.",
                content = @Content(mediaType = "application/json"))
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
