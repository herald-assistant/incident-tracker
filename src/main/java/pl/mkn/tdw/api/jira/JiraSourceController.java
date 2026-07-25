package pl.mkn.tdw.api.jira;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;

import java.util.Locale;
import java.util.regex.Pattern;

import static pl.mkn.tdw.api.jira.JiraSourceDtos.JiraIssueMaterialRequest;

@RestController
@RequestMapping("/api/jira")
@RequiredArgsConstructor
public class JiraSourceController {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");

    private final JiraIssuePort jiraIssuePort;

    @PostMapping("/issue/material")
    public JiraIssueMaterial getIssueMaterial(@Valid @RequestBody JiraIssueMaterialRequest request) {
        var issueKey = resolveIssueKey(request.issueRef());

        try {
            return jiraIssuePort.getIssueMaterial(issueKey);
        }
        catch (IllegalArgumentException exception) {
            throw JiraSourceApiException.badRequest(safeMessage(exception));
        }
        catch (IllegalStateException exception) {
            throw JiraSourceApiException.unavailable(safeMessage(exception));
        }
    }

    private String resolveIssueKey(String issueRef) {
        if (!StringUtils.hasText(issueRef)) {
            throw JiraSourceApiException.badRequest("issueRef must not be blank");
        }

        var matcher = ISSUE_KEY_PATTERN.matcher(issueRef.trim().toUpperCase(Locale.ROOT));
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw JiraSourceApiException.badRequest("issueRef must contain a Jira issue key, for example CRM-123.");
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
