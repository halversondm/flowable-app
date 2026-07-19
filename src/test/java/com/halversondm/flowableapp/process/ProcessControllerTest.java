package com.halversondm.flowableapp.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @InjectMocks private ProcessController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void startProcess_noBody_passesEmptyVariables() throws Exception {
        ProcessInstance instance = mockInstance("pi-1", "myProcess:1:abc");
        when(runtimeService.startProcessInstanceByKey(eq("myProcess"), anyMap())).thenReturn(instance);

        mockMvc.perform(post("/processes/myProcess/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processInstanceId").value("pi-1"))
                .andExpect(jsonPath("$.processDefinitionId").value("myProcess:1:abc"));

        verify(runtimeService).startProcessInstanceByKey("myProcess", Map.of());
    }

    @Test
    void startProcess_withVariables_forwardsToRuntime() throws Exception {
        ProcessInstance instance = mockInstance("pi-2", "myProcess:1:abc");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyMap())).thenReturn(instance);

        Map<String, Object> vars = Map.of("amount", 500, "currency", "USD");

        mockMvc.perform(post("/processes/myProcess/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vars)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processInstanceId").value("pi-2"));

        verify(runtimeService).startProcessInstanceByKey(eq("myProcess"), anyMap());
    }

    @Test
    void getOpenTasks_noAssignee_returnsAllTasks() throws Exception {
        Task task = mockTask("task-1", "Review Document", "alice", "pi-1");
        TaskQuery query = mockTaskQuery(List.of(task));

        mockMvc.perform(get("/processes/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("task-1"))
                .andExpect(jsonPath("$[0].name").value("Review Document"))
                .andExpect(jsonPath("$[0].assignee").value("alice"))
                .andExpect(jsonPath("$[0].processInstanceId").value("pi-1"));

        verify(query, never()).taskAssignee(anyString());
    }

    @Test
    void getOpenTasks_withAssignee_filtersQuery() throws Exception {
        Task task = mockTask("task-2", "Approve", "bob", "pi-2");
        TaskQuery query = mockTaskQuery(List.of(task));
        when(query.taskAssignee("bob")).thenReturn(query);

        mockMvc.perform(get("/processes/tasks").param("assignee", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignee").value("bob"));

        verify(query).taskAssignee("bob");
    }

    @Test
    void getOpenTasks_empty_returnsEmptyArray() throws Exception {
        mockTaskQuery(List.of());

        mockMvc.perform(get("/processes/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getOpenTasks_nullNameAndAssignee_defaultsToEmptyString() throws Exception {
        Task task = mockTask("task-3", null, null, "pi-3");
        mockTaskQuery(List.of(task));

        mockMvc.perform(get("/processes/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(""))
                .andExpect(jsonPath("$[0].assignee").value(""));
    }

    @Test
    void completeTask_noBody_completesWithEmptyVariables() throws Exception {
        mockMvc.perform(post("/processes/tasks/task-1/complete"))
                .andExpect(status().isNoContent());

        verify(taskService).complete("task-1", Map.of());
    }

    @Test
    void completeTask_withVariables_forwardsVariables() throws Exception {
        Map<String, Object> vars = Map.of("approved", true, "comments", "Looks good");

        mockMvc.perform(post("/processes/tasks/task-1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vars)))
                .andExpect(status().isNoContent());

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    private ProcessInstance mockInstance(String id, String definitionId) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getProcessDefinitionId()).thenReturn(definitionId);
        return instance;
    }

    private Task mockTask(String id, String name, String assignee, String processInstanceId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getName()).thenReturn(name);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        return task;
    }

    private TaskQuery mockTaskQuery(List<Task> results) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.list()).thenReturn(results);
        return query;
    }
}
