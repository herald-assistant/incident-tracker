package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendScreenSelectionRequest;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptTemplateBinding;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptTemplateBindingKind;
import pl.mkn.tdw.integrations.gitlab.GitLabFrontendScreenReachabilityPort;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.common.GitLabPathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;
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
            "^([a-z][a-z0-9-]{0,39})\\[(data-testid|data-test|data-cy|formcontrolname|name|aria-label|class)(~=|=)\"([A-Za-z][A-Za-z0-9_.:-]*)\"\\]$");
    private final FrontendApplicationCatalogService applicationCatalogService;
    private final GitLabFrontendScreenReachabilityPort screenReachabilityService;
    private final GitLabSettingsPort gitLabSettings;

    public UxInspectorTargetContext resolve(String systemId, String branch, String viewId,
                                            UxInspectorCapture capture) {
        var frontend = applicationCatalogService.loadCatalog().findFrontend(systemId)
                .orElseThrow(() -> error("UX_INSPECTOR_FRONTEND_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                        "Selected frontend is not registered for source analysis."));
        var mainGroup = gitLabSettings.getGroup();
        if (!GitLabPathUtils.isSameOrNestedPath(mainGroup, frontend.gitLabGroup())) {
            throw error("UX_INSPECTOR_REPOSITORY_SCOPE_UNAVAILABLE", UserFacingErrorType.CONFLICT,
                    "The selected frontend must belong to the configured GitLab group.");
        }
        var scope = new GitLabFrontendRepositoryScope(frontend.gitLabGroup(), frontend.gitLabProjectName(),
                branch, frontend.pathPrefixes());
        GitLabFrontendScreenReachabilityGraph graph;
        try {
            graph = screenReachabilityService.buildFocused(new GitLabFrontendScreenSelectionRequest(
                            scope, viewId, null, GitLabFrontendGraphLimits.defaults()),
                    orderedComponentBoundaries(capture));
        } catch (GitLabFrontendDiscoveryException exception) {
            throw mapFailure(exception);
        }
        var selectorSignals = selectorSignals(capture);
        var matches = graph.componentLevels().stream().flatMap(level -> level.components().stream())
                .map(component -> candidate(component, capture, graph, selectorSignals))
                .filter(match -> match.candidate().score() > 0)
                .sorted(Comparator.comparing(CandidateMatch::candidate,
                        Comparator.comparingInt(UxInspectorTargetCandidate::score).reversed()
                                .thenComparing(UxInspectorTargetCandidate::componentId)))
                .limit(MAX_CANDIDATES).toList();
        var candidates = matches.stream().map(CandidateMatch::candidate).toList();
        var status = resolutionStatus(candidates);
        if (status == UxInspectorTargetResolutionStatus.RESOLVED && matches.get(0).ambiguousElement()) {
            status = UxInspectorTargetResolutionStatus.AMBIGUOUS;
        }
        var limitations = new LinkedHashSet<String>(graph.limitations());
        if (status == UxInspectorTargetResolutionStatus.AMBIGUOUS) {
            limitations.add("Several source targets match the runtime observation; the answer must preserve this ambiguity.");
        } else if (status == UxInspectorTargetResolutionStatus.NOT_FOUND) {
            limitations.add("No source target could be verified in the selected view on the chosen branch.");
        }
        var focused = "";
        UxInspectorSourceBinding sourceBinding = null;
        if (status == UxInspectorTargetResolutionStatus.RESOLVED && !candidates.isEmpty()) {
            focused = candidates.get(0).sourceSlice();
            sourceBinding = matches.get(0).binding();
        } else if (status == UxInspectorTargetResolutionStatus.AMBIGUOUS) {
            focused = ambiguousCandidateEvidence(matches);
        }
        return new UxInspectorTargetContext(frontend.systemId(), frontend.label(),
                new UxInspectorSourceScope(GitLabPathUtils.trimSlashes(mainGroup.trim()),
                        scope.group(), scope.projectName(), scope.ref(), scope.pathPrefixes()),
                new UxInspectorViewIdentity(viewId,
                        StringUtils.hasText(graph.screenNode().label()) ? graph.screenNode().label() : graph.screenNode().routePattern(),
                        graph.screenNode().routePattern()),
                new UxInspectorSourceRevision(scope.ref()),
                status, candidates, sourceBinding, focused, List.copyOf(limitations), graph);
    }

    private CandidateMatch candidate(GitLabFrontendReachabilityComponent component,
                                                  UxInspectorCapture capture,
                                                  GitLabFrontendScreenReachabilityGraph graph,
                                                  List<SelectorSignal> selectorSignals) {
        var elements = UxInspectorTemplateElement.find(component.templateContent(), capture.target().tag()).stream()
                .map(element -> matchElement(element, capture.target(), selectorSignals))
                .sorted(Comparator.comparingInt(ElementMatch::score).reversed()).toList();
        var best = elements.isEmpty() ? null : elements.get(0);
        var reasons = new ArrayList<String>();
        var score = 0;
        if (best != null) {
            score = best.score();
            reasons.addAll(best.reasons());
            score += 4;
            reasons.add("element tag is present in template");
        }
        var boundaries = orderedComponentBoundaries(capture);
        for (var index = 0; index < boundaries.size(); index++) {
            if (GitLabFrontendReachabilityComponent.selectorMatchesTag(component.selector(), boundaries.get(index))) {
                score += componentBoundaryScore(index);
                reasons.add("component boundary matched at distance " + (index + 1));
                break;
            }
        }
        if (routeMatches(graph.screenNode().routePattern(), capture.page().path())) {
            score += 6;
            reasons.add("runtime path matches selected view route");
        }
        var ambiguousElement = best != null && best.score() > 0 && elements.size() > 1
                && best.score() == elements.get(1).score();
        var element = best != null && best.score() > 0 && !ambiguousElement ? best.element() : null;
        var binding = sourceBinding(component, element);
        var line = binding.templateLineStart();
        var related = new LinkedHashSet<String>();
        addPath(related, component.templatePath());
        addPath(related, component.sourcePath());
        component.dependencyIds().forEach(id -> graph.dependencies().stream()
                .filter(dependency -> id.equals(dependency.dependencyId()))
                .forEach(dependency -> addPath(related, dependency.sourcePath())));
        var candidate = new UxInspectorTargetCandidate(component.componentId(), score, reasons, component.componentId(),
                component.symbol(), component.selector(), component.sourcePath(), component.templatePath(), line,
                sourceSlice(component, line), List.copyOf(related));
        return new CandidateMatch(candidate, binding, ambiguousElement);
    }

    private ElementMatch matchElement(UxInspectorTemplateElement element, UxInspectorCapture.Target target,
                                      List<SelectorSignal> selectorSignals) {
        var reasons = new ArrayList<String>();
        var matchedValues = new LinkedHashSet<String>();
        var score = 0;
        for (var signal : selectorSignals) {
            if (element.matches(signal.kind(), signal.value()) && matchedValues.add(normalized(signal.value()))) {
                score += signal.weight();
                reasons.add("selector " + signal.kind() + " matched");
            }
        }
        var content = element.text();
        var name = normalized(target.accessibleName());
        if (name != null && !matchedValues.contains(name)
                && (element.matches("aria-label", target.accessibleName()) || content.contains(name))) {
            score += 28;
            matchedValues.add(name);
            reasons.add("accessible name matched");
        }
        var text = normalized(target.text());
        if (text != null && text.length() >= 3 && !matchedValues.contains(text) && content.contains(text)) {
            score += 20;
            reasons.add("visible text matched");
        }
        if (target.role() != null && element.matches("role", target.role())) {
            score += 9;
            reasons.add("explicit role matched");
        }
        return new ElementMatch(element, score, reasons);
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
            if (!attribute.matches() || !capture.target().tag().equalsIgnoreCase(attribute.group(1))) continue;
            var kind = attribute.group(2);
            var operator = attribute.group(3);
            if (("class".equals(kind) && !"~=".equals(operator))
                    || (!"class".equals(kind) && !"=".equals(operator))) continue;
            addSelectorSignal(signalsByValue, kind, attribute.group(4));
        }
        return signalsByValue.values().stream()
                .sorted(Comparator.comparingInt(SelectorSignal::weight).reversed()
                        .thenComparing(SelectorSignal::kind).thenComparing(SelectorSignal::value))
                .toList();
    }

    private void addSelectorSignal(LinkedHashMap<String, SelectorSignal> signalsByValue, String kind, String rawValue) {
        var value = rawValue == null ? null : rawValue.strip();
        var weight = selectorSignalWeight(kind);
        if (!StringUtils.hasText(value) || weight <= 0) return;
        var signal = new SelectorSignal(kind, value, weight);
        signalsByValue.putIfAbsent(kind + "=" + value, signal);
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

    private String ambiguousCandidateEvidence(List<CandidateMatch> matches) {
        var builder = new StringBuilder();
        var selected = matches.stream().limit(MAX_AMBIGUOUS_CANDIDATES).toList();
        for (var index = 0; index < selected.size(); index++) {
            var match = selected.get(index);
            var evidence = renderCandidateEvidence(index + 1, match.candidate(), match.binding());
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

    private UxInspectorSourceBinding sourceBinding(GitLabFrontendReachabilityComponent component,
                                                    UxInspectorTemplateElement element) {
        var template = component.templateContent();
        Integer start = element == null ? null : lineAtOffset(template, element.offset);
        Integer end = element == null ? null : lineAtOffset(template, element.offset + element.openingTag.length() - 1);
        var bindings = component.templateBindings().stream()
                .filter(binding -> element != null && binding.lineStart() >= start && binding.lineStart() <= end
                        && element.hasBinding(binding))
                .limit(12).map(this::sourceBinding).toList();
        var form = element == null ? null : element.form;
        var formLine = form == null ? 0 : lineAtOffset(template, form.offset);
        var formEnd = form == null ? 0 : lineAtOffset(template, form.offset + form.openingTag.length() - 1);
        var submitBinding = component.templateBindings().stream()
                .filter(binding -> form != null && binding.lineStart() >= formLine && binding.lineStart() <= formEnd
                        && binding.kind() == GitLabTypeScriptTemplateBindingKind.EVENT
                        && Set.of("ngsubmit", "submit").contains(binding.target().toLowerCase(Locale.ROOT))
                        && form.hasBinding(binding))
                .findFirst().map(this::sourceBinding).orElse(null);
        var referenced = new LinkedHashSet<String>();
        bindings.forEach(binding -> referenced.addAll(binding.referencedSymbols()));
        if (submitBinding != null) referenced.addAll(submitBinding.referencedSymbols());
        var external = StringUtils.hasText(component.templatePath());
        var sourceReference = external ? component.templatePath() : component.sourcePath();
        if (external && element != null) sourceReference += "#L" + start + "-L" + end;
        var snippet = element == null ? "" : element.openingTag.substring(0, Math.min(2_000, element.openingTag.length()));
        return new UxInspectorSourceBinding(component.componentId(), component.symbol(), component.selector(),
                component.sourcePath(), component.templatePath(), external ? "EXTERNAL" : "INLINE",
                start, end, sourceReference, snippet, bindings, submitBinding, List.copyOf(referenced));
    }

    private UxInspectorSourceBinding.TemplateBinding sourceBinding(GitLabTypeScriptTemplateBinding binding) {
        return new UxInspectorSourceBinding.TemplateBinding(binding.kind().name(), binding.target(),
                binding.expression(), binding.referencedSymbols(), binding.lineStart());
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
            case "FRONTEND_REF_NOT_FOUND" -> error("UX_INSPECTOR_SOURCE_REF_NOT_FOUND",
                    UserFacingErrorType.NOT_FOUND, "Selected frontend branch or ref does not exist.");
            case "FRONTEND_SCREEN_NOT_FOUND" -> error("UX_INSPECTOR_VIEW_NOT_FOUND",
                    UserFacingErrorType.CONFLICT, "Selected view is no longer present on the chosen branch.");
            default -> error("UX_INSPECTOR_SOURCE_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Frontend source could not be prepared for the selected view.");
        };
    }

    private UxInspectorContextException error(String code, UserFacingErrorType type, String message) {
        return new UxInspectorContextException(code, type, message);
    }
    private String normalized(String value) { return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null; }
    private void addPath(Set<String> paths, String path) { if (StringUtils.hasText(path)) paths.add(path.trim().replace('\\', '/')); }
    private record SelectorSignal(String kind, String value, int weight) {}
    private record ElementMatch(UxInspectorTemplateElement element, int score, List<String> reasons) {}
    private record CandidateMatch(UxInspectorTargetCandidate candidate, UxInspectorSourceBinding binding,
                                  boolean ambiguousElement) {}
}
