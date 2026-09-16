package pl.mkn.tdw.features.uxinspector.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;
import pl.mkn.tdw.integrations.gitlab.frontend.*;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UxInspectorTargetResolver {
    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_SOURCE_SLICE = 16_000;
    private final FrontendApplicationCatalogService applicationCatalogService;
    private final GitLabFrontendScreenReachabilityService screenReachabilityService;

    public UxInspectorTargetContext resolve(String systemId, String branch, String viewId,
                                            String expectedRevision, UxInspectorCapture capture) {
        var frontend = applicationCatalogService.loadCatalog().findFrontend(systemId)
                .orElseThrow(() -> error("UX_INSPECTOR_FRONTEND_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                        "Selected frontend is not registered for source analysis."));
        var scope = new GitLabFrontendRepositoryScope(frontend.gitLabGroup(), frontend.gitLabProjectName(),
                branch, frontend.pathPrefixes());
        GitLabFrontendScreenReachabilityGraph graph;
        try {
            graph = screenReachabilityService.build(new GitLabFrontendScreenSelectionRequest(
                    scope, viewId, expectedRevision, GitLabFrontendGraphLimits.defaults()));
        } catch (GitLabFrontendDiscoveryException exception) {
            throw mapFailure(exception);
        }
        if (!StringUtils.hasText(expectedRevision)
                || !expectedRevision.trim().equals(graph.sourceRevision().commitId())) {
            throw error("UX_INSPECTOR_SOURCE_REVISION_CHANGED", UserFacingErrorType.CONFLICT,
                    "Source revision changed. Reload views and select the target again.");
        }
        var candidates = graph.componentLevels().stream().flatMap(level -> level.components().stream())
                .map(component -> candidate(component, capture, graph)).filter(value -> value.score() > 0)
                .sorted(Comparator.comparingInt(UxInspectorTargetCandidate::score).reversed()
                        .thenComparing(UxInspectorTargetCandidate::componentId))
                .limit(MAX_CANDIDATES).toList();
        var status = resolutionStatus(candidates);
        var limitations = new LinkedHashSet<String>(graph.limitations());
        if (status == UxInspectorTargetResolutionStatus.AMBIGUOUS) {
            limitations.add("Several source targets match the runtime observation; the answer must preserve this ambiguity.");
        } else if (status == UxInspectorTargetResolutionStatus.NOT_FOUND) {
            limitations.add("No source target could be verified in the selected view and pinned revision.");
        }
        var paths = new LinkedHashSet<String>();
        graph.componentLevels().stream().flatMap(level -> level.components().stream()).forEach(component -> {
            addPath(paths, component.sourcePath());
            addPath(paths, component.templatePath());
        });
        graph.dependencies().forEach(dependency -> addPath(paths, dependency.sourcePath()));
        addPath(paths, graph.screenNode().routeSource().path());
        var focused = status == UxInspectorTargetResolutionStatus.RESOLVED && !candidates.isEmpty()
                ? candidates.get(0).sourceSlice() : "";
        var sourceBinding = status == UxInspectorTargetResolutionStatus.RESOLVED && !candidates.isEmpty()
                ? graph.componentLevels().stream().flatMap(level -> level.components().stream())
                    .filter(component -> component.componentId().equals(candidates.get(0).componentId()))
                    .findFirst().map(component -> sourceBinding(component, capture, candidates.get(0).templateLine()))
                    .orElse(null)
                : null;
        return new UxInspectorTargetContext(frontend.systemId(), frontend.label(),
                new UxInspectorSourceScope(scope.group(), scope.projectName(), scope.ref(), scope.pathPrefixes()),
                new UxInspectorViewIdentity(viewId,
                        StringUtils.hasText(graph.screenNode().label()) ? graph.screenNode().label() : graph.screenNode().routePattern(),
                        graph.screenNode().routePattern()),
                new UxInspectorSourceRevision(graph.sourceRevision().ref(), graph.sourceRevision().commitId()),
                status, candidates, sourceBinding, focused, paths, List.copyOf(limitations), graph);
    }

    private UxInspectorTargetCandidate candidate(GitLabFrontendReachabilityComponent component,
                                                  UxInspectorCapture capture,
                                                  GitLabFrontendScreenReachabilityGraph graph) {
        var reasons = new ArrayList<String>();
        var searchable = (component.templateContent() + "\n" + component.sliceContent()).toLowerCase(Locale.ROOT);
        var score = 0;
        for (var entry : capture.target().domFingerprint().stableAttributes().entrySet()) {
            var value = entry.getValue().toLowerCase(Locale.ROOT);
            if (containsStableValue(searchable, value)) {
                score += switch (entry.getKey()) {
                    case "data-testid", "data-test", "data-cy", "id", "formcontrolname" -> 55;
                    default -> 35;
                };
                reasons.add("stable attribute " + entry.getKey() + " matched");
            }
        }
        var name = normalized(capture.target().accessibleName());
        if (StringUtils.hasText(name) && searchable.contains(name)) {
            score += 28;
            reasons.add("accessible name matched");
        }
        var text = normalized(capture.target().text());
        if (StringUtils.hasText(text) && text.length() >= 3 && searchable.contains(text)) {
            score += 20;
            reasons.add("visible text matched");
        }
        var tagNeedle = "<" + capture.target().tag().toLowerCase(Locale.ROOT);
        if (component.templateContent().toLowerCase(Locale.ROOT).contains(tagNeedle)) {
            score += 4;
            reasons.add("element tag is present in template");
        }
        if (StringUtils.hasText(capture.target().role())
                && searchable.contains("role=\"" + capture.target().role().toLowerCase(Locale.ROOT))) {
            score += 9;
            reasons.add("explicit role matched");
        }
        var customTags = new LinkedHashSet<String>();
        capture.target().domFingerprint().componentBoundaryTags().stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(customTags::add);
        capture.ancestors().stream().map(UxInspectorCapture.Ancestor::tag)
                .filter(tag -> tag != null && tag.contains("-"))
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(customTags::add);
        if (StringUtils.hasText(component.selector()) && customTags.contains(component.selector().toLowerCase(Locale.ROOT))) {
            score += 18;
            reasons.add("custom-element ancestry matched component selector");
        }
        if (routeMatches(graph.screenNode().routePattern(), capture.page().path())) {
            score += 6;
            reasons.add("runtime path matches selected view route");
        }
        var line = matchingLine(component.templateContent(), capture);
        var related = new LinkedHashSet<String>();
        addPath(related, component.templatePath());
        addPath(related, component.sourcePath());
        component.dependencyIds().forEach(id -> graph.dependencies().stream()
                .filter(dependency -> id.equals(dependency.dependencyId()))
                .forEach(dependency -> addPath(related, dependency.sourcePath())));
        return new UxInspectorTargetCandidate(component.componentId(), score, reasons, component.componentId(),
                component.symbol(), component.selector(), component.sourcePath(), component.templatePath(), line,
                sourceSlice(component, line), List.copyOf(related));
    }

    private UxInspectorTargetResolutionStatus resolutionStatus(List<UxInspectorTargetCandidate> candidates) {
        if (candidates.isEmpty() || candidates.get(0).score() < 20) return UxInspectorTargetResolutionStatus.NOT_FOUND;
        if (candidates.get(0).score() >= 40
                && (candidates.size() == 1 || candidates.get(0).score() - candidates.get(1).score() >= 15)) {
            return UxInspectorTargetResolutionStatus.RESOLVED;
        }
        return UxInspectorTargetResolutionStatus.AMBIGUOUS;
    }

    private String sourceSlice(GitLabFrontendReachabilityComponent component, Integer line) {
        var builder = new StringBuilder();
        if (StringUtils.hasText(component.templateContent())) {
            var externalTemplate = StringUtils.hasText(component.templatePath());
            builder.append(externalTemplate ? "FILE " : "INLINE_TEMPLATE FROM ")
                    .append(externalTemplate ? component.templatePath() : component.sourcePath());
            if (externalTemplate && line != null) builder.append("#L").append(line);
            builder.append("\n").append(excerpt(component.templateContent(), line)).append("\n\n");
        }
        if (StringUtils.hasText(component.sourcePath())) {
            builder.append("FILE ").append(component.sourcePath()).append("\n").append(component.sliceContent());
        }
        var value = builder.toString().trim();
        return value.length() <= MAX_SOURCE_SLICE ? value : value.substring(0, MAX_SOURCE_SLICE);
    }

    private String excerpt(String content, Integer line) {
        if (!StringUtils.hasText(content)) return "";
        var lines = content.split("\\R", -1);
        var center = line != null ? Math.max(0, line - 1) : 0;
        var start = Math.max(0, center - 12);
        var end = Math.min(lines.length, center + 18);
        var builder = new StringBuilder();
        for (var index = start; index < end; index++) {
            builder.append(index + 1).append(": ").append(lines[index]).append('\n');
        }
        return builder.toString().trim();
    }

    private Integer matchingLine(String template, UxInspectorCapture capture) {
        if (!StringUtils.hasText(template)) return null;
        var needles = new ArrayList<String>();
        needles.addAll(capture.target().domFingerprint().stableAttributes().values());
        if (StringUtils.hasText(capture.target().accessibleName())) needles.add(capture.target().accessibleName());
        if (StringUtils.hasText(capture.target().text())) needles.add(capture.target().text());
        var lines = template.split("\\R", -1);
        for (var index = 0; index < lines.length; index++) {
            var lower = lines[index].toLowerCase(Locale.ROOT);
            if (needles.stream().filter(StringUtils::hasText)
                    .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(lower::contains)) return index + 1;
        }
        return null;
    }

    private UxInspectorSourceBinding sourceBinding(
            GitLabFrontendReachabilityComponent component,
            UxInspectorCapture capture,
            Integer matchingLine
    ) {
        var range = elementRange(component.templateContent(), capture.target().tag(), matchingLine);
        var bindings = component.templateBindings().stream()
                .filter(binding -> range != null && binding.lineStart() >= range.startLine()
                        && binding.lineStart() <= range.endLine())
                .limit(12).map(this::sourceBinding).toList();
        var formRange = range != null ? enclosingFormRange(component.templateContent(), range.startLine()) : null;
        var submitBinding = component.templateBindings().stream()
                .filter(binding -> formRange != null
                        && binding.kind() == GitLabTypeScriptTemplateBindingKind.EVENT
                        && Set.of("ngsubmit", "submit").contains(binding.target().toLowerCase(Locale.ROOT))
                        && binding.lineStart() >= formRange.startLine()
                        && binding.lineStart() <= formRange.endLine())
                .findFirst().map(this::sourceBinding).orElse(null);
        var referenced = new LinkedHashSet<String>();
        bindings.forEach(binding -> referenced.addAll(binding.referencedSymbols()));
        if (submitBinding != null) referenced.addAll(submitBinding.referencedSymbols());
        var external = StringUtils.hasText(component.templatePath());
        var referencePath = external ? component.templatePath() : component.sourcePath();
        var sourceReference = referencePath;
        if (external && range != null) {
            sourceReference += "#L" + range.startLine() + "-L" + range.endLine();
        }
        return new UxInspectorSourceBinding(component.componentId(), component.symbol(), component.selector(),
                component.sourcePath(), component.templatePath(), external ? "EXTERNAL" : "INLINE",
                range != null ? range.startLine() : null, range != null ? range.endLine() : null,
                sourceReference, range != null ? range.snippet() : "", bindings, submitBinding,
                List.copyOf(referenced));
    }

    private UxInspectorSourceBinding.TemplateBinding sourceBinding(GitLabTypeScriptTemplateBinding binding) {
        return new UxInspectorSourceBinding.TemplateBinding(binding.kind().name(), binding.target(),
                binding.expression(), binding.referencedSymbols(), binding.lineStart());
    }

    private SourceRange elementRange(String template, String targetTag, Integer matchingLine) {
        if (!StringUtils.hasText(template) || !StringUtils.hasText(targetTag)) return null;
        var lines = template.split("\\R", -1);
        var anchor = matchingLine != null ? Math.max(0, Math.min(lines.length - 1, matchingLine - 1)) : 0;
        var tagNeedle = "<" + targetTag.toLowerCase(Locale.ROOT);
        var start = -1;
        for (var index = anchor; index >= Math.max(0, anchor - 12); index--) {
            if (lines[index].toLowerCase(Locale.ROOT).contains(tagNeedle)) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            for (var index = 0; index < lines.length; index++) {
                if (lines[index].toLowerCase(Locale.ROOT).contains(tagNeedle)) {
                    start = index;
                    break;
                }
            }
        }
        if (start < 0) return null;
        var end = start;
        var snippet = new StringBuilder();
        while (end < lines.length && end <= start + 16 && snippet.length() < 2_000) {
            if (!snippet.isEmpty()) snippet.append('\n');
            snippet.append(lines[end]);
            if (lines[end].contains(">")) break;
            end++;
        }
        var value = snippet.toString();
        if (value.length() > 2_000) value = value.substring(0, 2_000);
        return new SourceRange(start + 1, Math.min(lines.length, end + 1), value);
    }

    private SourceRange enclosingFormRange(String template, int targetLine) {
        if (!StringUtils.hasText(template)) return null;
        var offset = offsetAtLine(template, targetLine);
        var lower = template.toLowerCase(Locale.ROOT);
        var formStart = lower.lastIndexOf("<form", offset);
        var closedBefore = lower.lastIndexOf("</form", offset);
        if (formStart < 0 || closedBefore > formStart) return null;
        var formEnd = lower.indexOf("</form", offset);
        if (formEnd < 0) formEnd = template.length();
        var startLine = lineAtOffset(template, formStart);
        var endLine = lineAtOffset(template, formEnd);
        return new SourceRange(startLine, endLine, "");
    }

    private int offsetAtLine(String value, int line) {
        var currentLine = 1;
        for (var index = 0; index < value.length(); index++) {
            if (currentLine == line) return index;
            if (value.charAt(index) == '\n') currentLine++;
        }
        return value.length();
    }

    private int lineAtOffset(String value, int offset) {
        var line = 1;
        for (var index = 0; index < Math.min(offset, value.length()); index++) {
            if (value.charAt(index) == '\n') line++;
        }
        return line;
    }

    private boolean routeMatches(String pattern, String path) {
        if (!StringUtils.hasText(pattern) || !StringUtils.hasText(path)) return false;
        var normalizedPattern = normalizeRoute(pattern);
        var normalizedPath = normalizeRoute(path);
        if (normalizedPattern.isEmpty()) return normalizedPath.isEmpty();
        var segments = normalizedPattern.split("/");
        var regex = new StringBuilder("^");
        for (var index = 0; index < segments.length; index++) {
            if (index > 0) regex.append('/');
            var segment = segments[index];
            if ("**".equals(segment)) regex.append(".*");
            else if (segment.startsWith(":")) regex.append("[^/]+");
            else regex.append(Pattern.quote(segment));
        }
        regex.append("/?$");
        return normalizedPath.matches(regex.toString());
    }

    private String normalizeRoute(String value) {
        var normalized = value.trim().replace('\\', '/');
        var query = normalized.indexOf('?');
        if (query >= 0) normalized = normalized.substring(0, query);
        var fragment = normalized.indexOf('#');
        if (fragment >= 0) normalized = normalized.substring(0, fragment);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private RuntimeException mapFailure(GitLabFrontendDiscoveryException exception) {
        return switch (exception.code()) {
            case "FRONTEND_SOURCE_REVISION_CHANGED" -> error("UX_INSPECTOR_SOURCE_REVISION_CHANGED",
                    UserFacingErrorType.CONFLICT, "Source revision changed. Reload views and select the target again.");
            case "FRONTEND_REF_NOT_FOUND" -> error("UX_INSPECTOR_SOURCE_REF_NOT_FOUND",
                    UserFacingErrorType.NOT_FOUND, "Selected frontend branch or ref does not exist.");
            case "FRONTEND_SCREEN_NOT_FOUND" -> error("UX_INSPECTOR_VIEW_NOT_FOUND",
                    UserFacingErrorType.CONFLICT, "Selected view is no longer present in the pinned source revision.");
            default -> error("UX_INSPECTOR_SOURCE_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Frontend source could not be prepared for the selected view.");
        };
    }

    private UxInspectorContextException error(String code, UserFacingErrorType type, String message) {
        return new UxInspectorContextException(code, type, message);
    }
    private String normalized(String value) { return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null; }
    private boolean containsStableValue(String searchable, String value) {
        if (!StringUtils.hasText(value)) return false;
        return Pattern.compile("(?<![a-z0-9_-])" + Pattern.quote(value) + "(?![a-z0-9_-])")
                .matcher(searchable).find();
    }
    private void addPath(Set<String> paths, String path) { if (StringUtils.hasText(path)) paths.add(path.trim().replace('\\', '/')); }
    private record SourceRange(int startLine, int endLine, String snippet) {}
}
