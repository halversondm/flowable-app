package com.halversondm.flowableapp.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.DeploymentQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentControllerTest {

    @Mock private RepositoryService repositoryService;
    @InjectMocks private DeploymentController controller;

    private MockMvc mockMvc;
    private DeploymentBuilder deploymentBuilder;
    private Deployment deployment;
    private ProcessDefinitionQuery pdQuery;

    @BeforeEach
    void setUp() {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter();
        stringConverter.setSupportedMediaTypes(List.of(MediaType.ALL));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(stringConverter, new MappingJackson2HttpMessageConverter())
                .build();

        deploymentBuilder = mock(DeploymentBuilder.class);
        deployment = mock(Deployment.class);
        pdQuery = mock(ProcessDefinitionQuery.class);

        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addString(anyString(), anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addInputStream(anyString(), any())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenReturn(deployment);

        when(deployment.getId()).thenReturn("dep-1");
        when(deployment.getName()).thenReturn("test-deployment");
        when(deployment.getDeploymentTime()).thenReturn(new Date());

        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.deploymentId(anyString())).thenReturn(pdQuery);
        when(pdQuery.list()).thenReturn(List.of());
    }

    @Test
    void deployXml_success() throws Exception {
        mockMvc.perform(post("/deployments/xml")
                        .param("name", "my-process")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<definitions/>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentId").value("dep-1"))
                .andExpect(jsonPath("$.deploymentName").value("test-deployment"))
                .andExpect(jsonPath("$.processDefinitions").isArray());

        verify(deploymentBuilder).name("my-process");
        verify(deploymentBuilder).addString("my-process.bpmn20.xml", "<definitions/>");
        verify(deploymentBuilder).deploy();
    }

    @Test
    void deployXml_usesDefaultName() throws Exception {
        mockMvc.perform(post("/deployments/xml")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<definitions/>"))
                .andExpect(status().isOk());

        verify(deploymentBuilder).name("api-deployment");
        verify(deploymentBuilder).addString("api-deployment.bpmn20.xml", "<definitions/>");
    }

    @Test
    void deployXml_includesProcessDefinitions() throws Exception {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd-id-1");
        when(pd.getKey()).thenReturn("my-process");
        when(pd.getName()).thenReturn("My Process");
        when(pd.getVersion()).thenReturn(1);
        when(pdQuery.list()).thenReturn(List.of(pd));

        mockMvc.perform(post("/deployments/xml")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<definitions/>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processDefinitions[0].id").value("pd-id-1"))
                .andExpect(jsonPath("$.processDefinitions[0].key").value("my-process"))
                .andExpect(jsonPath("$.processDefinitions[0].name").value("My Process"))
                .andExpect(jsonPath("$.processDefinitions[0].version").value(1));
    }

    @Test
    void deployFile_usesExplicitName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn20.xml", MediaType.APPLICATION_XML_VALUE, "<definitions/>".getBytes());

        mockMvc.perform(multipart("/deployments").file(file).param("name", "explicit-name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentId").value("dep-1"));

        verify(deploymentBuilder).name("explicit-name");
        verify(deploymentBuilder).addInputStream(eq("process.bpmn20.xml"), any());
    }

    @Test
    void deployFile_usesFilenameWhenNoName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn20.xml", MediaType.APPLICATION_XML_VALUE, "<definitions/>".getBytes());

        mockMvc.perform(multipart("/deployments").file(file))
                .andExpect(status().isOk());

        verify(deploymentBuilder).name("process.bpmn20.xml");
    }

    @Test
    void deployFile_usesFilenameWhenNameIsBlank() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn20.xml", MediaType.APPLICATION_XML_VALUE, "<definitions/>".getBytes());

        mockMvc.perform(multipart("/deployments").file(file).param("name", "   "))
                .andExpect(status().isOk());

        verify(deploymentBuilder).name("process.bpmn20.xml");
    }

    @Test
    void listDeployments_returnsAll() throws Exception {
        DeploymentQuery deploymentQuery = mockDeploymentQuery(List.of(deployment));

        mockMvc.perform(get("/deployments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].deploymentId").value("dep-1"))
                .andExpect(jsonPath("$[0].deploymentName").value("test-deployment"));

        verify(deploymentQuery).orderByDeploymentTime();
        verify(deploymentQuery).desc();
    }

    @Test
    void listDeployments_returnsEmpty() throws Exception {
        mockDeploymentQuery(List.of());

        mockMvc.perform(get("/deployments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteDeployment_defaultNoCascade() throws Exception {
        mockMvc.perform(delete("/deployments/dep-1"))
                .andExpect(status().isNoContent());

        verify(repositoryService).deleteDeployment("dep-1", false);
    }

    @Test
    void deleteDeployment_withCascade() throws Exception {
        mockMvc.perform(delete("/deployments/dep-1").param("cascade", "true"))
                .andExpect(status().isNoContent());

        verify(repositoryService).deleteDeployment("dep-1", true);
    }

    private DeploymentQuery mockDeploymentQuery(List<Deployment> results) {
        DeploymentQuery query = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(query);
        when(query.orderByDeploymentTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.list()).thenReturn(results);
        return query;
    }
}
