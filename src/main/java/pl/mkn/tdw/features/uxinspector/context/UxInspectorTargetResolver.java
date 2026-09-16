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
import java.util.LinkedHashMap;
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
    private static final int MAX_AMBIGUOUS_CANDIDATES = 3;
    private static final int MAX_AMBIGUOUS_CANDIDATE_EVIDENCE = 8_000;
    private static final List<String> SELECTOR_ATTRIBUTE_PRIORITY = List.of(
            "id", "data-testid", "data-test", "data-cy", "formcontrolname", "name", "aria-label"
    );
    private static final Pattern ID_SELECTOR = Pattern.compile("^#([A-Za-z][A-Za-z0-9_.:-]*)$");
    private static final Pattern ATTRIBUTE_SELECTOR = Pattern.compile(
            "^[a-z][a-z0-9-]{0,39}\\[(data-testid|data-test|data-cy|formcontrolname|name|aria-label|class)(~=|=)\"([A-Za-z][A-Za-z0-9_.:-]*)\"\\]$");
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
        var selectorSignals = selectorSignals(capture);
        var candidates = graph.componentLevels().stream().flatMap(level -> level.components().stream())
                .map(component -> candidate(component, capture, graph, selectorSignals)).filter(value -> value.score() > 0)
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
        var focused = "";
        UxInspectorSourceBinding sourceBinding = null;
        if (status == UxInspectorTargetResolutionStatus.RESOLVED && !candidates.isEmpty()) {
            focused = candidates.get(0).sourceSlice();
            var component = component(graph, candidates.get(0).componentId());
            if (component != null) {
                sourceBinding = sourceBinding(component, capture, candidates.get(0).templateLine());
            }
        } else if (status == UxInspectorTargetResolutionStatus.AMBIGUOUS) {
            focused = ambiguousCandidateEvidence(candidates, graph, capture);
        }
        return new UxInspectorTargetContext(frontend.systemId(), frontend.label(),
                new UxInspectorSourceScope(scope.group(), scope.projectName(), scope.ref(), scope.pathPrefixes()),
                new UxInspectorViewIdentity(viewId,
                        StringUtils.hasText(graph.screenNode().label()) ? graph.screenNode().label() : graph.screenNode().routePattern(),
                        graph.screenNode().routePattern()),
                new UxInspectorSourceRevision(graph.sourceRevision().ref(), graph.sourceRevision().commitId()),
                status, candidates, sourceBinding, focused, List.copyOf(limitations), graph);
    }

    private UxInspectorTargetCandidate candidate(GitLabFrontendReachabilityComponent component,
                                                  UxInspectorCapture capture,
                                                  GitLabFrontendScreenReachabilityGraph graph,
                                                  List<SelectorSignal> selectorSignals) {
        var reasons = new ArrayList<String>();
        var searchable = (component.templateContent() + "\n" + component.sliceContent()).toLowerCase(Locale.ROOT);
        var score = 0;
        var matchedValues = new LinkedHashSet<String>();
        for (var signal : selectorSignals) {
            if (containsStableValue(searchable, signal.value())) {
                score += signal.weight();
                matchedValues.add(signal.value());
                reasons.add("selector " + signal.kind() + " matched");
            }
        }
        var name = normalized(capture.target().accessibleName());
        if (StringUtils.hasText(name) && !matchedValues.contains(name) && searchable.contains(name)) {
            score += 28;
            matchedValues.add(name);
            reasons.add("accessible name matched");
        }
        var text = normalized(capture.target().text());
        if (StringUtils.hasText(text) && text.length() >= 3 && !matchedValues.contains(text)
                && searchable.contains(text)) {
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
        var boundaries = orderedComponentBoundaries(capture);
        var boundaryIndex = StringUtils.hasText(component.selector())
                ? boundaries.indexOf(component.selector().toLowerCase(Locale.ROOT)) : -1;
        if (boundaryIndex >= 0) {
            var boundaryScore = componentBoundaryScore(boundaryIndex);
            score += boundaryScore;
            reasons.add("component boundary matched at distance " + (boundaryIndex + 1));
        }
        if (routeMatches(graph.screenNode().routePattern(), capture.page().path())) {
            score += 6;
            reasons.add("runtime path matches selected view route");
        }
        var line = matchingLine(component.templateContent(), capture, selectorSignals);
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

    private List<SelectorSignal> selectorSignals(UxInspectorCapture capture) {
        var signalsByValue = new LinkedHashMap<String, SelectorSignal>();
        var attributes = capture.target().domFingerprint().stableAttributes();
        for (var attribute : SELECTOR_ATTRIBUTE_PRIORITY) {
            addSelectorSignal(signalsByValue, attribute, attributes.get(attribute));
        }
        for (var selector : capture.target().domFingerprint().selectorCandidates()) {
            var id = ID_SELECTOR.matcher(selector);
            if (id.matches()) {
                addSelectorSignal(signalsByValue, "id", id.group(1));
                continue;
            }
            var attribute = ATTRIBUTE_SELECTOR.matcher(selector);
            if (!attribute.matches()) continue;
            var kind = attribute.group(1);
            var operator = attribute.group(2);
            if (("class".equals(kind) && !"~=".equals(operator))
                    || (!"class".equals(kind) && !"=".equals(operator))) continue;
            addSelectorSignal(signalsByValue, kind, attribute.group(3));
        }
        return signalsByValue.values().stream()
                .sorted(Comparator.comparingInt(SelectorSignal::weight).reversed()
                        .thenComparing(SelectorSignal::kind).thenComparing(SelectorSignal::value))
                .toList();
    }

    private void addSelectorSignal(LinkedHashMap<String, SelectorSignal> signalsByValue, String kind, String rawValue) {
        var value = normalized(rawValue);
        var weight = selectorSignalWeight(kind);
        if (!StringUtils.hasText(value) || weight <= 0) return;
        var signal = new SelectorSignal(kind, value, weight);
        signalsByValue.merge(value, signal, (current, candidate) ->
                candidate.weight() > current.weight() ? candidate : current);
    }

    private int selectorSignalWeight(String kind) {
        return switch (kind) {
            case "id", "data-testid", "data-test", "data-cy", "formcontrolname" -> 55;
            case "name" -> 36;
            case "aria-label" -> 28;
            case "class" -> 18;
            default -> 0;
        };
    }

    private List<String> orderedComponentBoundaries(UxInspectorCapture capture) {
        var boundaries = new ArrayList<String>();
        capture.target().domFingerprint().componentBoundaryTags().stream()
                .map(this::normalized).filter(StringUtils::hasText)
                .forEach(value -> addDistinct(boundaries, value));
        capture.ancestors().stream().sorted(Comparator.comparingInt(UxInspectorCapture.Ancestor::depth))
                .map(UxInspectorCapture.Ancestor::tag).filter(tag -> tag != null && tag.contains("-"))
                .map(this::normalized).filter(StringUtils::hasText)
                .forEach(value -> addDistinct(boundaries, value));
        return List.copyOf(boundaries);
    }

    private void addDistinct(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private int componentBoundaryScore(int boundaryIndex) {
        return switch (boundaryIndex) {
            case 0 -> 36;
            case 1 -> 18;
            case 2 -> 12;
            case 3 -> 8;
            default -> Math.max(2, 7 - boundaryIndex);
        };
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

    private String ambiguousCandidateEvidence(
            List<UxInspectorTargetCandidate> candidates,
            GitLabFrontendScreenReachabilityGraph graph,
            UxInspectorCapture capture
    ) {
        var builder = new StringBuilder();
        var selected = candidates.stream().limit(MAX_AMBIGUOUS_CANDIDATES).toList();
        for (var index = 0; index < selected.size(); index++) {
            var candidate = selected.get(index);
            var component = component(graph, candidate.componentId());
            if (component == null) continue;
            var binding = sourceBinding(component, capture, candidate.templateLine());
            var evidence = renderCandidateEvidence(index + 1, candidate, binding);
            if (!builder.isEmpty()) builder.append("\n\n");
            builder.append(evidence, 0, Math.min(evidence.length(), MAX_AMBIGUOUS_CANDIDATE_EVIDENCE));
        }
        return builder.toString();
    }

    private String renderCandidateEvidence(
            int rank,
            UxInspectorTargetCandidate candidate,
            UxInspectorSourceBinding binding
    ) {
        var builder = new StringBuilder();
        builder.append("AMBIGUOUS_CANDIDATE ").append(rank).append('\n');
        builder.append("component: ").append(candidate.componentId()).append('\n');
        builder.append("score: ").append(candidate.score()).append('\n');
        builder.append("matchReasons: ").append(String.join(", ", candidate.matchReasons())).append('\n');
        builder.append("sourceReference: ").append(binding.sourceReference()).append('\n');
        builder.append("elementBindings:");
        if (binding.elementBindings().isEmpty()) {
            builder.append(" []\n");
        } else {
            builder.append('\n');
            binding.elementBindings().forEach(value -> builder.append("- ").append(value.kind()).append(' ')
                    .append(value.target()).append(" = ").append(value.expression()).append(" @L")
                    .append(value.templateLine()).append('\n'));
        }
        builder.append("formSubmitBinding: ");
        if (binding.formSubmitBinding() == null) {
            builder.append("null\n");
        } else {
            var submit = binding.formSubmitBinding();
            builder.append(submit.target()).append(" = ").append(submit.expression()).append(" @L")
                    .append(submit.templateLine()).append('\n');
        }
        builder.append("referencedSymbols: ").append(String.join(", ", binding.referencedSymbols())).append('\n');
        builder.append("SOURCE_SLICE\n").append(candidate.sourceSlice());
        return builder.toString();
    }

    private GitLabFrontendReachabilityComponent component(
            GitLabFrontendScreenReachabilityGraph graph,
            String componentId
    ) {
        return graph.componentLevels().stream().flatMap(level -> level.components().stream())
                .filter(value -> value.componentId().equals(componentId)).findFirst().orElse(null);
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

    private Integer matchingLine(
            String template,
            UxInspectorCapture capture,
            List<SelectorSignal> selectorSignals
    ) {
        if (!StringUtils.hasText(template)) return null;
        var lines = template.split("\\R", -1);
        for (var signal : selectorSignals) {
            var line = uniqueMatchingLine(lines, signal.value());
            if (line != null) return line;
        }
        var accessibleName = normalized(capture.target().accessibleName());
        var line = uniqueMatchingLine(lines, accessibleName);
        if (line != null) return line;
        var text = normalized(capture.target().text());
        if (StringUtils.hasText(text) && !text.equals(accessibleName)) {
            line = uniqueMatchingLine(lines, text);
            if (line != null) return line;
        }
        var role = normalized(capture.target().role());
        if (StringUtils.hasText(role)) return uniqueMatchingLine(lines, "role=\"" + role + "\"");
        return null;
    }

    private Integer uniqueMatchingLine(String[] lines, String needle) {
        if (!StringUtils.hasText(needle)) return null;
        Integer match = null;
        for (var index = 0; index < lines.length; index++) {
            if (!containsStableValue(lines[index].toLowerCase(Locale.ROOT), needle)) continue;
            if (match != null) return null;
            match = index + 1;
        }
        return match;
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
        if (!StringUtils.hasText(template) || !StringUtils.hasText(targetTag) || matchingLine == null) return null;
        var lines = template.split("\\R", -1);
        var anchor = Math.max(0, Math.min(lines.length - 1, matchingLine - 1));
        var tagNeedle = "<" + targetTag.toLowerCase(Locale.ROOT);
        var start = -1;
        var bestDistance = Integer.MAX_VALUE;
        var tied = false;
        for (var index = Math.max(0, anchor - 12); index <= Math.min(lines.length - 1, anchor + 12); index++) {
            if (!lines[index].toLowerCase(Locale.ROOT).contains(tagNeedle)) continue;
            var distance = Math.abs(index - anchor);
            if (distance < bestDistance) {
                start = index;
                bestDistance = distance;
                tied = false;
            } else if (distance == bestDistance) {
                tied = true;
            }
        }
        if (start < 0 || tied) return null;
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
    private record SelectorSignal(String kind, String value, int weight) {}
    private record SourceRange(int startLine, int endLine, String snippet) {}
}
