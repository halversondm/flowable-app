package com.halversondm.flowableapp.deployment;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/deployments")
public class DeploymentController {

    private final RepositoryService repositoryService;

    public DeploymentController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Deploy BPMN from a raw XML body.
     * Example: POST /deployments/xml?name=my-process
     * Content-Type: application/xml
     */
    @PostMapping(path = "/xml", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<DeploymentResponse> deployXml(
            @RequestParam(defaultValue = "api-deployment") String name,
            @RequestBody String bpmnXml) {

        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addString(name + ".bpmn20.xml", bpmnXml)
                .deploy();

        return ResponseEntity.ok(toResponse(deployment));
    }

    /**
     * Deploy BPMN from a multipart file upload.
     * Example: POST /deployments  (multipart/form-data, field "file")
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeploymentResponse> deployFile(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String name) throws IOException {

        String deploymentName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();

        Deployment deployment = repositoryService.createDeployment()
                .name(deploymentName)
                .addInputStream(file.getOriginalFilename(), file.getInputStream())
                .deploy();

        return ResponseEntity.ok(toResponse(deployment));
    }

    /**
     * List all deployments.
     */
    @GetMapping
    public ResponseEntity<List<DeploymentResponse>> listDeployments() {
        List<DeploymentResponse> responses = repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Delete a deployment and all its process instances.
     */
    @DeleteMapping("/{deploymentId}")
    public ResponseEntity<Void> deleteDeployment(
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "false") boolean cascade) {

        repositoryService.deleteDeployment(deploymentId, cascade);
        return ResponseEntity.noContent().build();
    }

    private DeploymentResponse toResponse(Deployment deployment) {
        List<DeploymentResponse.ProcessDefinitionSummary> definitions =
                repositoryService.createProcessDefinitionQuery()
                        .deploymentId(deployment.getId())
                        .list()
                        .stream()
                        .map(this::toSummary)
                        .toList();

        return new DeploymentResponse(
                deployment.getId(),
                deployment.getName(),
                deployment.getDeploymentTime().toInstant(),
                definitions
        );
    }

    private DeploymentResponse.ProcessDefinitionSummary toSummary(ProcessDefinition pd) {
        return new DeploymentResponse.ProcessDefinitionSummary(
                pd.getId(),
                pd.getKey(),
                pd.getName(),
                pd.getVersion()
        );
    }
}
