package pl.mkn.tdw.features.deliverycomplexityassessment.evidence;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentIssue;
import pl.mkn.tdw.integrations.jira.contract.JiraConfluencePage;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestChangedFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.stream.Collectors.joining;

@Component
public class DeliveryEvidencePacketBuilder {

    public DeliveryEvidencePacket build(DeliveryUnit unit) {
        var visibilityLimits = new LinkedHashSet<>(unit.limitations());
        var scorable = !unit.mergeRequests().isEmpty()
                && unit.mergeRequests().stream().anyMatch(mergeRequest -> !mergeRequest.changedFiles().isEmpty());
        var mechanicallyExcluded = scorable && allFilesMechanical(unit.mergeRequests());

        var artifacts = new LinkedHashMap<String, String>();
        artifacts.put("delivery-complexity/issues.md", renderIssues(unit, visibilityLimits));
        artifacts.put("delivery-complexity/merge-requests.md",
                renderMergeRequests(unit.mergeRequests(), visibilityLimits));
        artifacts.put("delivery-complexity/diffs.md", renderDiffs(unit.mergeRequests(), visibilityLimits));
        artifacts.put("delivery-complexity/visibility.md", renderVisibility(visibilityLimits));
        return new DeliveryEvidencePacket(
                unit,
                artifacts,
                scorable,
                mechanicallyExcluded,
                List.copyOf(visibilityLimits)
        );
    }

    private String renderIssues(DeliveryUnit unit, LinkedHashSet<String> visibilityLimits) {
        var assessedIssues = new LinkedHashMap<String, DeliveryAssessmentIssue>();
        unit.issues().forEach(issue -> assessedIssues.put(issue.issueKey(), issue));
        var materials = new LinkedHashMap<String, List<JiraIssueMaterial>>();
        var parentsByChild = new LinkedHashMap<String, LinkedHashSet<String>>();
        var childrenByParent = new LinkedHashMap<String, LinkedHashSet<String>>();
        unit.issues().forEach(issue -> addMaterial(materials, issue.material()));
        for (var issue : unit.issues()) {
            var material = issue.material();
            if (material.parentIssue() != null) {
                addMaterial(materials, material.parentIssue());
                addRelation(parentsByChild, childrenByParent, material.parentIssue().issueKey(), issue.issueKey());
            }
            for (var child : material.subTasks()) {
                addMaterial(materials, child);
                addRelation(parentsByChild, childrenByParent, issue.issueKey(), child.issueKey());
            }
        }

        var documents = new LinkedHashMap<JiraConfluencePage, LinkedHashSet<String>>();
        var text = new StringBuilder("# Jira delivery scope\n\n")
                .append("- Oceniane zadania: ").append(String.join(", ", assessedIssues.keySet())).append("\n")
                .append("- Pozostale zadania sa kontekstem poza zakresem oceny.\n\n");
        materials.forEach((key, variants) -> {
            var assessed = assessedIssues.get(key);
            text.append("## ").append(key).append("\n\n")
                    .append("- Zakres oceny: ").append(assessed != null
                            ? "OCENIANE ZADANIE" : "KONTEKST POZA ZAKRESEM OCENY").append("\n");
            if (assessed != null) {
                text.append("- Done at: ").append(assessed.doneAt()).append("\n");
            }
            var parents = parentsByChild.getOrDefault(key, new LinkedHashSet<>());
            var children = childrenByParent.getOrDefault(key, new LinkedHashSet<>());
            if (!parents.isEmpty()) {
                text.append("- Zadanie podrzedne wobec: ").append(String.join(", ", parents)).append("\n");
            }
            if (!children.isEmpty()) {
                text.append("- Zadanie nadrzedne wobec: ").append(String.join(", ", children)).append("\n");
            }
            if (parents.isEmpty() && children.isEmpty()) {
                text.append("- Relacje: brak potwierdzonego parent/child w dostepnym materiale.\n");
            }
            text.append("- Summary: ").append(variants.stream().map(JiraIssueMaterial::summary)
                            .filter(StringUtils::hasText).distinct().collect(joining(" | ")))
                    .append("\n")
                    .append("- Labels: ").append(variants.stream().flatMap(variant -> variant.labels().stream())
                            .distinct().collect(joining(", ")))
                    .append("\n\n### Description\n\n");
            variants.stream().map(JiraIssueMaterial::description).filter(StringUtils::hasText).distinct()
                    .forEach(description -> text.append(text(description)).append("\n\n"));
            text.append("### Acceptance criteria\n\n");
            variants.stream().flatMap(variant -> variant.acceptanceCriteria().stream()).distinct()
                    .forEach(criterion -> text.append("- ").append(criterion).append("\n"));
            for (var variant : variants) {
                variant.limitations().forEach(limit -> visibilityLimits.add("Jira issue " + key + ": " + limit));
                for (var page : variant.confluencePages()) {
                    documents.computeIfAbsent(page, ignored -> new LinkedHashSet<>()).add(key);
                    page.limitations().forEach(limit -> visibilityLimits.add(
                            "Jira issue " + key + ", document " + value(page.title()) + ": " + limit));
                }
            }
            text.append("\n");
        });
        documents.forEach((page, keys) -> text.append("## Linked document: ").append(value(page.title()))
                .append("\n\n- Referenced by tasks: ").append(String.join(", ", keys)).append("\n\n")
                .append(text(page.content())).append("\n\n"));
        return text.toString();
    }

    private void addMaterial(Map<String, List<JiraIssueMaterial>> materials, JiraIssueMaterial material) {
        var variants = materials.computeIfAbsent(material.issueKey(), ignored -> new ArrayList<>());
        if (!variants.contains(material)) {
            variants.add(material);
        }
    }

    private void addRelation(
            Map<String, LinkedHashSet<String>> parentsByChild,
            Map<String, LinkedHashSet<String>> childrenByParent,
            String parent,
            String child
    ) {
        parentsByChild.computeIfAbsent(child, ignored -> new LinkedHashSet<>()).add(parent);
        childrenByParent.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(child);
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
