package pl.mkn.tdw.features.systemassistant.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;

public record SystemAssistantConfiguration(
        @NotNull @Size(max = 12) List<@Valid ApiDefinition> apis,
        @NotNull @Size(max = 20) List<@Valid Skill> skills,
        @NotNull @Size(max = 20) List<@Valid Profile> profiles,
        @NotNull @Size(max = 20) List<@Valid HttpAuthProfile> authProfiles
) {
    public record ApiDefinition(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,23}") String id,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100_000) String yaml) {
    }

    public record Skill(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,39}") String id,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20_000) String content) {
    }

    public record Profile(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,100}") String id,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 2300) String urlPattern,
            @NotNull @Size(max = 12) List<@Valid ApiBinding> apiBindings,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> skillIds) {
        public Profile {
            if (apiBindings != null) apiBindings = List.copyOf(apiBindings);
            if (skillIds != null) skillIds = List.copyOf(skillIds);
        }
    }

    public record ApiBinding(
            @NotBlank @Size(max = 100) String apiId,
            @com.fasterxml.jackson.annotation.JsonProperty(required = true) @Size(max = 100) String authProfileId) { }

    public SystemAssistantConfiguration {
        if (apis != null) apis = List.copyOf(apis);
        if (skills != null) skills = List.copyOf(skills);
        if (profiles != null) profiles = List.copyOf(profiles);
        if (authProfiles != null) authProfiles = List.copyOf(authProfiles);
    }

    public static SystemAssistantConfiguration empty() {
        return new SystemAssistantConfiguration(List.of(), List.of(), List.of(), List.of());
    }
}
