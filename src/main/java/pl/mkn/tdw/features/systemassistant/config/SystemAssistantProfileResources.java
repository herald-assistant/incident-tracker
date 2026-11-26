package pl.mkn.tdw.features.systemassistant.config;

import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration.Skill;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;

import java.util.List;

/** The current skills and authenticated operations captured together for one profile. */
public record SystemAssistantProfileResources(List<Skill> skills, List<OpenApiExecution> executions) {
    public SystemAssistantProfileResources {
        skills = List.copyOf(skills);
        executions = List.copyOf(executions);
    }
}
