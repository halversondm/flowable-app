package com.halversondm.flowableapp.deployment;

import java.time.Instant;
import java.util.List;

public record DeploymentResponse(
        String deploymentId,
        String deploymentName,
        Instant deployedAt,
        List<ProcessDefinitionSummary> processDefinitions
) {
    public record ProcessDefinitionSummary(
            String id,
            String key,
            String name,
            int version
    ) {}
}
