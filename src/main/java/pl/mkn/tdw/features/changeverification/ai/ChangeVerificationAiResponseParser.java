package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChangeVerificationAiResponseParser {

    private final ObjectMapper objectMapper;

    public ChangeVerificationAiResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            return fallback("AI response did not contain JSON compliance result.");
        }

        try {
            var response = objectMapper.readValue(json, ChangeVerificationAiResponse.class);
            if (!StringUtils.hasText(response.status())) {
                return fallback("AI response did not contain compliance status.");
            }
            var validationError = validateChecks(response.verificationChecks());
            if (StringUtils.hasText(validationError)) {
                return fallback(validationError);
            }
            return response;
        } catch (JsonProcessingException exception) {
            return fallback("AI response JSON could not be parsed: " + exception.getMessage());
        }
    }

    private String validateChecks(List<ChangeVerificationVerificationCheckResponse> checks) {
        var allowedOrigins = Set.of("DEFINED", "INFERRED_CRITICAL");
        var allowedDefinedScopes = Set.of("STORY_COMPLIANCE", "INSTRUCTION_COMPLIANCE");
        var allowedStatuses = Set.of("PASSED", "WARNING", "FAILED", "NOT_VERIFIED");
        for (var check : checks) {
            if (check == null || !StringUtils.hasText(check.id())) {
                return "AI response contained a verification check without id.";
            }
            var origin = normalized(check.origin());
            if (!allowedOrigins.contains(origin)) {
                return "AI response contained a verification check without a supported origin.";
            }
            if ("DEFINED".equals(origin) && !allowedDefinedScopes.contains(normalized(check.scope()))) {
                return "AI response contained a defined verification check with unsupported scope.";
            }
            if (!allowedStatuses.contains(normalized(check.verificationStatus()))) {
                return "AI response contained a verification check with unsupported status.";
            }
            if ("INFERRED_CRITICAL".equals(origin)) {
                if (!"INFERRED_CRITICAL_CHECKS".equals(normalized(check.scope()))) {
                    return "AI response contained an inferred critical check with unsupported scope.";
                }
                if (!StringUtils.hasText(check.criticality())
                        || !StringUtils.hasText(check.inferenceRationale())
                        || check.inferenceSignals().isEmpty()
                        || !StringUtils.hasText(check.riskIfOmitted())
                        || !StringUtils.hasText(check.confidence())) {
                    return "AI response contained an incomplete inferred critical check.";
                }
                if (!Set.of("HIGH", "BLOCKER").contains(normalized(check.criticality()))
                        || !Set.of("HIGH", "MEDIUM", "LOW").contains(normalized(check.confidence()))) {
                    return "AI response contained inferred critical metadata outside the supported contract.";
                }
            }
        }
        return null;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : "";
    }

    public ChangeVerificationAiResponse fallback(String limitation) {
        return new ChangeVerificationAiResponse(
                "INCONCLUSIVE",
                List.of(),
                List.of(),
                List.of("Run compliance verification again or inspect collected evidence manually."),
                List.of(limitation),
                "low"
        );
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        var trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        var fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            var jsonStart = trimmed.indexOf('\n', fenceStart);
            var fenceEnd = trimmed.indexOf("```", jsonStart + 1);
            if (jsonStart >= 0 && fenceEnd > jsonStart) {
                return trimmed.substring(jsonStart + 1, fenceEnd).trim();
            }
        }

        var objectStart = trimmed.indexOf('{');
        var objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1).trim();
        }

        return null;
    }
}
