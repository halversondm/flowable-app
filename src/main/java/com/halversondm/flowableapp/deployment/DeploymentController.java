package com.halversondm.flowableapp.deployment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "Deployments", description = "Deploy and manage BPMN process definitions. Flowable stores the raw XML and resolved process definition metadata in the database on each successful deployment.")
@RestController
@RequestMapping("/deployments")
public class DeploymentController {

    private final RepositoryService repositoryService;

    public DeploymentController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Operation(
        summary = "Deploy BPMN from raw XML",
        description = "Accepts a BPMN 2.0 XML document in the request body and deploys it to the Flowable engine. " +
                      "The XML is stored in the database and all process definitions contained in the document are parsed and registered."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deployment successful",
            content = @Content(schema = @Schema(implementation = DeploymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid BPMN XML", content = @Content),
        @ApiResponse(responseCode = "500", description = "Flowable engine error", content = @Content)
    })
    @PostMapping(path = "/xml", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<DeploymentResponse> deployXml(
            @Parameter(description = "Logical name for this deployment. Used as the filename stored in the database.", example = "order-process")
            @RequestParam(defaultValue = "api-deployment") String name,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "BPMN 2.0 XML document", required = true,
                content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE))
            @RequestBody String bpmnXml) {

        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addString(name + ".bpmn20.xml", bpmnXml)
                .deploy();

        return ResponseEntity.ok(toResponse(deployment));
    }

    @Operation(
        summary = "Deploy BPMN from file upload",
        description = "Accepts a .bpmn20.xml or .bpmn file via multipart upload and deploys it to the Flowable engine. " +
                      "The original filename is used as the deployment name when no explicit name is provided."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deployment successful",
            content = @Content(schema = @Schema(implementation = DeploymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid file", content = @Content),
        @ApiResponse(responseCode = "500", description = "Flowable engine error", content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeploymentResponse> deployFile(
            @Parameter(description = "BPMN file to deploy (.bpmn20.xml or .bpmn)", required = true)
            @RequestParam MultipartFile file,
            @Parameter(description = "Optional deployment name. Defaults to the uploaded filename.")
            @RequestParam(required = false) String name) throws IOException {

        String deploymentName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();

        Deployment deployment = repositoryService.createDeployment()
                .name(deploymentName)
                .addInputStream(file.getOriginalFilename(), file.getInputStream())
                .deploy();

        return ResponseEntity.ok(toResponse(deployment));
    }

    @Operation(
        summary = "List all deployments",
        description = "Returns all deployments stored in the Flowable engine, ordered by deployment time descending. " +
                      "Each entry includes the resolved process definitions that were parsed from the deployed BPMN."
    )
    @ApiResponse(responseCode = "200", description = "List of deployments",
        content = @Content(schema = @Schema(implementation = DeploymentResponse.class)))
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

    @Operation(
        summary = "Delete a deployment",
        description = "Deletes the specified deployment from the Flowable engine. " +
                      "By default the delete is non-cascading and will fail if active process instances exist. " +
                      "Set cascade=true to also delete all related process instances, history, and tasks."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deployment deleted"),
        @ApiResponse(responseCode = "500", description = "Active instances exist and cascade=false", content = @Content)
    })
    @DeleteMapping("/{deploymentId}")
    public ResponseEntity<Void> deleteDeployment(
            @Parameter(description = "ID of the deployment to delete", required = true)
            @PathVariable String deploymentId,
            @Parameter(description = "When true, also deletes all running process instances, history, and tasks for this deployment")
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
