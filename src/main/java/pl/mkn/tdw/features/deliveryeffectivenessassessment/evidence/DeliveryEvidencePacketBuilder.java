package pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DeliveryEvidencePacketBuilder {

    private final DeliveryEffectivenessAssessmentProperties properties;

    public DeliveryEvidencePacket build(DeliveryUnit unit) {
        var visibilityLimits = new LinkedHashSet<>(unit.limitations());
        if (unit.issues().size() > properties.getMaxIssuesPerUnit()) {
            visibilityLimits.add("Delivery Unit issue count exceeded configured evidence limit.");
        }
        if (unit.mergeRequests().size() > properties.getMaxMergeRequestsPerUnit()) {
            visibilityLimits.add("Delivery Unit merge request count exceeded configured evidence limit.");
        }

        var boundedIssues = unit.issues().stream().limit(properties.getMaxIssuesPerUnit()).toList();
        var boundedMergeRequests = unit.mergeRequests().stream()
                .limit(properties.getMaxMergeRequestsPerUnit())
                .toList();
        var scorable = !boundedMergeRequests.isEmpty()
                && boundedMergeRequests.stream().anyMatch(mergeRequest -> !mergeRequest.changedFiles().isEmpty());
        var mechanicallyExcluded = scorable && allFilesMechanical(boundedMergeRequests);

        var artifacts = new LinkedHashMap<String, String>();
        artifacts.put("delivery-effectiveness/issues.md", renderIssues(boundedIssues, visibilityLimits));
        artifacts.put("delivery-effectiveness/merge-requests.md",
                renderMergeRequests(boundedMergeRequests, visibilityLimits));
        artifacts.put("delivery-effectiveness/diffs.md", renderDiffs(boundedMergeRequests, visibilityLimits));
        artifacts.put("delivery-effectiveness/visibility.md", renderVisibility(visibilityLimits));
        return new DeliveryEvidencePacket(
                unit,
                artifacts,
                scorable,
                mechanicallyExcluded,
                List.copyOf(visibilityLimits)
        );
    }

    private String renderIssues(
            List<pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentIssue> issues,
            LinkedHashSet<String> visibilityLimits
    ) {
        var text = new StringBuilder("# Jira delivery scope\n\n");
        var documentCount = 0;
        for (var issue : issues) {
            var material = issue.material();
            text.append("## ").append(issue.issueKey()).append("\n\n")
                    .append("- Done at: ").append(issue.doneAt()).append("\n")
                    .append("- Type: ").append(value(material.issueType())).append("\n")
                    .append("- Summary: ").append(value(material.summary())).append("\n")
                    .append("- Labels: ").append(String.join(", ", material.labels())).append("\n\n")
                    .append("### Description\n\n")
                    .append(limit(material.description(), properties.getMaxJiraDescriptionCharacters())).append("\n\n")
                    .append("### Acceptance criteria\n\n");
            material.acceptanceCriteria().forEach(criterion -> text.append("- ").append(criterion).append("\n"));
            for (var page : material.confluencePages()) {
                if (documentCount >= properties.getMaxDocumentsPerUnit()) {
                    visibilityLimits.add("Linked documents were truncated by the evidence budget.");
                    break;
                }
                text.append("\n### Linked document: ").append(value(page.title())).append("\n\n")
                        .append(limit(page.content(), properties.getMaxDocumentCharactersPerUnit())).append("\n");
                documentCount++;
            }
        }
        return text.toString();
    }

    private String renderMergeRequests(List<GitLabMergeRequest> mergeRequests, LinkedHashSet<String> visibilityLimits) {
        var text = new StringBuilder("# Merged GitLab changes\n\n");
        for (var mergeRequest : mergeRequests) {
            text.append("## ").append(value(mergeRequest.projectPath())).append("!")
                    .append(mergeRequest.iid()).append("\n\n")
                    .append("- Title: ").append(limit(mergeRequest.title(),
                            properties.getMaxMergeRequestDescriptionCharacters())).append("\n")
                    .append("- Merged at: ").append(value(mergeRequest.mergedAt())).append("\n")
                    .append("- Source -> target: ").append(value(mergeRequest.sourceBranch()))
                    .append(" -> ").append(value(mergeRequest.targetBranch())).append("\n")
                    .append("- Changed paths:\n");
            var files = mergeRequest.changedFiles().stream()
                    .limit(properties.getMaxChangedFilesPerMergeRequest())
                    .toList();
            files.forEach(file -> text.append("  - ").append(path(file)).append("\n"));
            if (mergeRequest.changedFiles().size() > files.size()) {
                visibilityLimits.add("Changed files were truncated for " + value(mergeRequest.webUrl()) + ".");
            }
            visibilityLimits.addAll(mergeRequest.limitations());
        }
        return text.toString();
    }

    private String renderDiffs(List<GitLabMergeRequest> mergeRequests, LinkedHashSet<String> visibilityLimits) {
        var remaining = properties.getMaxDiffCharactersPerUnit();
        var text = new StringBuilder("# Bounded implementation diffs\n\n");
        for (var mergeRequest : mergeRequests) {
            var files = mergeRequest.changedFiles().stream()
                    .limit(properties.getMaxChangedFilesPerMergeRequest())
                    .toList();
            for (var file : files) {
                if (!StringUtils.hasText(file.diff())) {
                    continue;
                }
                var header = "## " + value(mergeRequest.projectPath()) + "!" + mergeRequest.iid()
                        + " - " + path(file) + "\n\n";
                if (remaining <= header.length()) {
                    visibilityLimits.add("Diff content was truncated by the Delivery Unit character budget.");
                    return text.toString();
                }
                text.append(header);
                remaining -= header.length();
                var diff = limit(file.diff(), remaining);
                text.append("```diff\n").append(diff).append("\n```\n\n");
                remaining -= diff.length();
                if (diff.length() < file.diff().trim().length()) {
                    visibilityLimits.add("Diff content was truncated by the Delivery Unit character budget.");
                    return text.toString();
                }
            }
        }
        if (text.toString().equals("# Bounded implementation diffs\n\n")) {
            visibilityLimits.add("GitLab returned changed paths without diff content.");
        }
        return text.toString();
    }

    private String renderVisibility(LinkedHashSet<String> visibilityLimits) {
        var text = new StringBuilder("# Visibility limits\n\n");
        visibilityLimits.forEach(limit -> text.append("- ").append(limit).append("\n"));
        return text.toString();
    }

    private boolean allFilesMechanical(List<GitLabMergeRequest> mergeRequests) {
        var files = mergeRequests.stream().flatMap(mergeRequest -> mergeRequest.changedFiles().stream()).toList();
        return !files.isEmpty() && files.stream().allMatch(file -> isMechanical(path(file)));
    }

    private boolean isMechanical(String path) {
        var normalized = value(path).replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/generated/")
                || normalized.contains("/build/")
                || normalized.contains("/dist/")
                || normalized.endsWith("package-lock.json")
                || normalized.endsWith("yarn.lock")
                || normalized.endsWith(".min.js")
                || normalized.endsWith(".map")
                || normalized.endsWith(".class")
                || normalized.endsWith(".jar");
    }

    private String path(GitLabMergeRequestChangedFile file) {
        return StringUtils.hasText(file.newPath()) ? file.newPath() : file.oldPath();
    }

    private String limit(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        var safeLimit = Math.max(0, maxCharacters);
        return trimmed.length() > safeLimit ? trimmed.substring(0, safeLimit) : trimmed;
    }

    private String value(Object value) {
        return value != null ? value.toString() : "";
    }
}
