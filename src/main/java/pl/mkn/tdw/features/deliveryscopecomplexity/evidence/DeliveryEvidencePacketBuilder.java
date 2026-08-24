package pl.mkn.tdw.features.deliveryscopecomplexity.evidence;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component("deliveryScopeEvidencePacketBuilder")
public class DeliveryEvidencePacketBuilder {

    public DeliveryEvidencePacket build(DeliveryUnit unit) {
        var visibilityLimits = new LinkedHashSet<>(unit.limitations());
        var scorable = !unit.mergeRequests().isEmpty()
                && unit.mergeRequests().stream().anyMatch(mergeRequest -> !mergeRequest.changedFiles().isEmpty());
        var mechanicallyExcluded = scorable && allFilesMechanical(unit.mergeRequests());

        var artifacts = new LinkedHashMap<String, String>();
        artifacts.put("delivery-scope-complexity/issues.md", renderIssues(unit.issues()));
        artifacts.put("delivery-scope-complexity/merge-requests.md",
                renderMergeRequests(unit.mergeRequests(), visibilityLimits));
        artifacts.put("delivery-scope-complexity/diffs.md", renderDiffs(unit.mergeRequests(), visibilityLimits));
        artifacts.put("delivery-scope-complexity/visibility.md", renderVisibility(visibilityLimits));
        return new DeliveryEvidencePacket(
                unit,
                artifacts,
                scorable,
                mechanicallyExcluded,
                List.copyOf(visibilityLimits)
        );
    }

    private String renderIssues(List<pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeIssue> issues) {
        var text = new StringBuilder("# Jira delivery scope\n\n");
        for (var issue : issues) {
            var material = issue.material();
            text.append("## ").append(issue.issueKey()).append("\n\n")
                    .append("- Done at: ").append(issue.doneAt()).append("\n")
                    .append("- Type: ").append(value(material.issueType())).append("\n")
                    .append("- Summary: ").append(value(material.summary())).append("\n")
                    .append("- Labels: ").append(String.join(", ", material.labels())).append("\n\n")
                    .append("### Description\n\n")
                    .append(text(material.description())).append("\n\n")
                    .append("### Acceptance criteria\n\n");
            material.acceptanceCriteria().forEach(criterion -> text.append("- ").append(criterion).append("\n"));
            for (var page : material.confluencePages()) {
                text.append("\n### Linked document: ").append(value(page.title())).append("\n\n")
                        .append(text(page.content())).append("\n");
            }
        }
        return text.toString();
    }

    private String renderMergeRequests(List<GitLabMergeRequest> mergeRequests, LinkedHashSet<String> visibilityLimits) {
        var text = new StringBuilder("# Merged GitLab changes\n\n");
        for (var mergeRequest : mergeRequests) {
            text.append("## ").append(value(mergeRequest.projectPath())).append("!")
                    .append(mergeRequest.iid()).append("\n\n")
                    .append("- Title: ").append(text(mergeRequest.title())).append("\n")
                    .append("- Merged at: ").append(value(mergeRequest.mergedAt())).append("\n")
                    .append("- Source -> target: ").append(value(mergeRequest.sourceBranch()))
                    .append(" -> ").append(value(mergeRequest.targetBranch())).append("\n")
                    .append("- Changed paths:\n");
            mergeRequest.changedFiles().forEach(file -> text.append("  - ").append(path(file)).append("\n"));
            visibilityLimits.addAll(mergeRequest.limitations());
        }
        return text.toString();
    }

    private String renderDiffs(List<GitLabMergeRequest> mergeRequests, LinkedHashSet<String> visibilityLimits) {
        var text = new StringBuilder("# Implementation diffs\n\n");
        var hasDiff = false;
        for (var mergeRequest : mergeRequests) {
            for (var file : mergeRequest.changedFiles()) {
                if (!StringUtils.hasText(file.diff())) {
                    continue;
                }
                hasDiff = true;
                text.append("## ").append(value(mergeRequest.projectPath())).append("!")
                        .append(mergeRequest.iid()).append(" - ").append(path(file)).append("\n\n")
                        .append("```diff\n").append(text(file.diff())).append("\n```\n\n");
            }
        }
        if (!hasDiff) {
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

    private String text(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String value(Object value) {
        return value != null ? value.toString() : "";
    }
}
