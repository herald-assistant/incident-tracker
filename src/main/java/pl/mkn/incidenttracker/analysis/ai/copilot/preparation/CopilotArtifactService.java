package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.EvidenceGap;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CopilotArtifactService {

    private static final String MANIFEST_ARTIFACT_ID = "00-incident-manifest.json";
    private static final String DIGEST_ARTIFACT_ID = "01-incident-digest.md";

    private final ObjectMapper objectMapper;
    private final CopilotIncidentDigestService incidentDigestService;
    private final CopilotArtifactItemIdGenerator itemIdGenerator;

    @Autowired
    public CopilotArtifactService(
            ObjectMapper objectMapper,
            CopilotIncidentDigestService incidentDigestService,
            CopilotArtifactItemIdGenerator itemIdGenerator
    ) {
        this.objectMapper = objectMapper;
        this.incidentDigestService = incidentDigestService;
        this.itemIdGenerator = itemIdGenerator;
    }

    public List<Artifact> renderArtifacts(
            AnalysisAiAnalysisRequest request,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        var descriptors = buildArtifactIndex(request);
        var artifacts = new ArrayList<Artifact>();
        artifacts.add(new Artifact(
                MANIFEST_ARTIFACT_ID,
                "Artifact index and analysis context",
                null,
                null,
                null,
                "application/json",
                renderManifestArtifact(request, descriptors, toolAccessPolicy)
        ));
        artifacts.add(new Artifact(
                DIGEST_ARTIFACT_ID,
                "Compressed incident digest for fast grounding",
                "copilot",
                "incident-digest",
                null,
                "text/markdown",
                incidentDigestService.renderDigest(
                        request,
                        toolAccessPolicy != null
                                ? toolAccessPolicy.evidenceCoverage()
                                : CopilotEvidenceCoverageReport.empty()
                )
        ));

        var sections = request.evidenceSections();
        for (int index = 0; index < sections.size(); index++) {
            var section = sections.get(index);
            var displayName = buildSectionFileName(index + 2, section);
            artifacts.add(new Artifact(
                    displayName,
                    "Evidence section from provider `%s` in category `%s`".formatted(
                            normalizeDescriptorValue(section.provider()),
                            normalizeDescriptorValue(section.category())
                    ),
                    section.provider(),
                    section.category(),
                    section.items().size(),
                    mimeTypeFor(displayName),
                    renderSectionArtifact(section)
            ));
        }

        return List.copyOf(artifacts);
    }

    public Map<String, String> toArtifactContentMap(
            AnalysisAiAnalysisRequest request,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        return toArtifactContentMap(renderArtifacts(request, toolAccessPolicy));
    }

    public Map<String, String> toArtifactContentMap(List<Artifact> artifacts) {
        var artifactContents = new LinkedHashMap<String, String>();
        for (var artifact : artifacts) {
            artifactContents.put(artifact.displayName(), artifact.content());
        }

        return Collections.unmodifiableMap(artifactContents);
    }

    private String renderManifestArtifact(
            AnalysisAiAnalysisRequest request,
            List<ArtifactDescriptor> descriptors,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        try {
            return renderJson(buildManifestPayload(request, descriptors, toolAccessPolicy));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render Copilot incident manifest.", exception);
        }
    }

    private String renderSectionArtifact(AnalysisEvidenceSection section) {
        try {
            var readableMarkdown = buildReadableMarkdown(section);
            if (readableMarkdown != null) {
                return addMarkdownItemIds(section, readableMarkdown);
            }

            return renderJson(buildEvidenceSectionPayload(section));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render Copilot evidence artifact.", exception);
        }
    }

    private String renderJson(Object payload) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private List<ArtifactDescriptor> buildArtifactIndex(AnalysisAiAnalysisRequest request) {
        var descriptors = new ArrayList<ArtifactDescriptor>();
        descriptors.add(new ArtifactDescriptor(
                MANIFEST_ARTIFACT_ID,
                "Artifact index and analysis context",
                null,
                null,
                null,
                List.of()
        ));
        descriptors.add(new ArtifactDescriptor(
                DIGEST_ARTIFACT_ID,
                "Compressed incident digest for fast grounding",
                "copilot",
                "incident-digest",
                null,
                List.of()
        ));

        var sections = request.evidenceSections();
        for (int index = 0; index < sections.size(); index++) {
            var section = sections.get(index);
            descriptors.add(new ArtifactDescriptor(
                    buildSectionFileName(index + 2, section),
                    "Evidence section from provider `%s` in category `%s`".formatted(
                            normalizeDescriptorValue(section.provider()),
                            normalizeDescriptorValue(section.category())
                    ),
                    section.provider(),
                    section.category(),
                    section.items().size(),
                    itemIdGenerator.itemIds(section)
            ));
        }

        return List.copyOf(descriptors);
    }

    private String mimeTypeFor(String displayName) {
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return "application/json";
        }
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }

    private Map<String, Object> buildManifestPayload(
            AnalysisAiAnalysisRequest request,
            List<ArtifactDescriptor> descriptors,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("correlationId", request.correlationId());
        payload.put("environment", blankToNull(request.environment()));
        payload.put("gitLabBranch", blankToNull(request.gitLabBranch()));
        payload.put("gitLabGroup", blankToNull(request.gitLabGroup()));
        payload.put("generatedAt", Instant.now().toString());
        payload.put("artifactFormatVersion", CopilotArtifactFormatVersion.V2.value());
        payload.put("readFirst", MANIFEST_ARTIFACT_ID);
        payload.put("readNext", DIGEST_ARTIFACT_ID);
        payload.put("readOrder", List.of(MANIFEST_ARTIFACT_ID, DIGEST_ARTIFACT_ID));
        payload.put("artifactPolicy", Map.of(
                "artifactsArePrimarySourceOfTruth", true,
                "useToolsOnlyForGapFilling", true,
                "deliveryMode", "embedded-prompt"
        ));
        payload.put("toolPolicy", buildToolPolicyPayload(toolAccessPolicy));
        payload.put("evidenceCoverage", buildEvidenceCoveragePayload(toolAccessPolicy));
        payload.put("artifacts", descriptors.stream().map(this::toManifestEntry).toList());
        return payload;
    }

    private Map<String, Object> buildToolPolicyPayload(CopilotToolAccessPolicy toolAccessPolicy) {
        var policy = toolAccessPolicy != null
                ? toolAccessPolicy
                : CopilotToolAccessPolicy.empty();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("localWorkspaceAccessBlocked", policy.localWorkspaceAccessBlocked());
        payload.put("enabledToolNames", policy.availableToolNames());
        payload.put("enabledCapabilityGroups", policy.enabledCapabilityGroups());
        payload.put("disabledCapabilityGroups", policy.disabledCapabilityGroups());
        return payload;
    }

    private Map<String, Object> buildEvidenceCoveragePayload(CopilotToolAccessPolicy toolAccessPolicy) {
        var coverage = toolAccessPolicy != null
                ? toolAccessPolicy.evidenceCoverage()
                : CopilotEvidenceCoverageReport.empty();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("elastic", coverage.elastic().name());
        payload.put("gitLab", coverage.gitLab().name());
        payload.put("runtime", coverage.runtime().name());
        payload.put("dataDiagnosticNeed", coverage.dataDiagnosticNeed().name());
        payload.put("operationalContext", coverage.operationalContext().name());
        payload.put("environmentResolved", coverage.environmentResolved());
        payload.put("gaps", coverage.gaps().stream().map(this::toEvidenceGapPayload).toList());
        return payload;
    }

    private Map<String, String> toEvidenceGapPayload(EvidenceGap gap) {
        var payload = new LinkedHashMap<String, String>();
        payload.put("code", gap.code());
        payload.put("description", gap.description());
        return payload;
    }

    private Map<String, Object> toManifestEntry(ArtifactDescriptor descriptor) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("displayName", descriptor.displayName());
        payload.put("role", descriptor.role());
        payload.put("provider", blankToNull(descriptor.provider()));
        payload.put("category", blankToNull(descriptor.category()));
        payload.put("itemCount", descriptor.itemCount());
        payload.put("itemIds", descriptor.itemIds());
        return payload;
    }

    private Map<String, Object> buildEvidenceSectionPayload(AnalysisEvidenceSection section) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("provider", section.provider());
        payload.put("category", section.category());
        payload.put("itemCount", section.items().size());
        var items = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < section.items().size(); index++) {
            items.add(toEvidenceItemPayload(section, section.items().get(index), index));
        }
        payload.put("items", items);
        return payload;
    }

    private Map<String, Object> toEvidenceItemPayload(
            AnalysisEvidenceSection section,
            AnalysisEvidenceItem item,
            int index
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("itemId", itemIdGenerator.itemId(section, index));
        payload.put("title", item.title());
        payload.put("attributes", item.attributes().stream().map(this::toAttributePayload).toList());
        payload.put("attributeMap", toAttributeMap(item.attributes()));
        return payload;
    }

    private Map<String, String> toAttributePayload(AnalysisEvidenceAttribute attribute) {
        var payload = new LinkedHashMap<String, String>();
        payload.put("name", attribute.name());
        payload.put("value", attribute.value());
        return payload;
    }

    private Map<String, String> toAttributeMap(List<AnalysisEvidenceAttribute> attributes) {
        var attributeMap = new LinkedHashMap<String, String>();
        for (var attribute : attributes) {
            attributeMap.put(attribute.name(), attribute.value());
        }
        return attributeMap;
    }

    private String buildSectionFileName(int index, AnalysisEvidenceSection section) {
        return "%02d-%s-%s.%s".formatted(
                index,
                sanitize(normalizeDescriptorValue(section.provider())),
                sanitize(normalizeDescriptorValue(section.category())),
                isReadableMarkdownSection(section) ? "md" : "json"
        );
    }

    private boolean isReadableMarkdownSection(AnalysisEvidenceSection section) {
        return ElasticLogEvidenceView.matches(section)
                || DynatraceRuntimeEvidenceView.matches(section)
                || GitLabResolvedCodeEvidenceView.matches(section);
    }

    private String buildReadableMarkdown(AnalysisEvidenceSection section) {
        if (ElasticLogEvidenceView.matches(section)) {
            return ElasticLogEvidenceView.from(section).toMarkdown();
        }

        if (DynatraceRuntimeEvidenceView.matches(section)) {
            return DynatraceRuntimeEvidenceView.from(section).toMarkdown();
        }

        if (GitLabResolvedCodeEvidenceView.matches(section)) {
            return GitLabResolvedCodeEvidenceView.from(section).toMarkdown();
        }

        return null;
    }

    private String addMarkdownItemIds(AnalysisEvidenceSection section, String markdown) {
        var lines = new ArrayList<String>();
        var heading = firstLine(markdown);
        if (heading != null) {
            lines.add(heading);
            lines.add("");
        }

        for (int index = 0; index < section.items().size(); index++) {
            var item = section.items().get(index);
            lines.add("## itemId: " + itemIdGenerator.itemId(section, index));
            lines.add("- title: `" + escapeInlineCode(normalizeDescriptorValue(item.title())) + "`");
            lines.add("");
        }

        lines.add("## Evidence details");
        lines.add("");
        lines.add(markdownWithoutFirstLine(markdown).strip());
        return String.join(System.lineSeparator(), lines).strip() + System.lineSeparator();
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.lines().findFirst().orElse(null);
    }

    private String markdownWithoutFirstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var lineSeparatorIndex = value.indexOf('\n');
        if (lineSeparatorIndex < 0) {
            return "";
        }
        return value.substring(lineSeparatorIndex + 1);
    }

    private String sanitize(String value) {
        var normalized = value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String normalizeDescriptorValue(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    private String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private String escapeInlineCode(String value) {
        return value.replace('`', '\'');
    }

    public record ArtifactDescriptor(
            String displayName,
            String role,
            String provider,
            String category,
            Integer itemCount,
            List<String> itemIds
    ) {
        public ArtifactDescriptor {
            itemIds = itemIds != null ? List.copyOf(itemIds) : List.of();
        }
    }

    public record Artifact(
            String displayName,
            String role,
            String provider,
            String category,
            Integer itemCount,
            String mimeType,
            String content
    ) {
        public ArtifactDescriptor descriptor() {
            return new ArtifactDescriptor(displayName, role, provider, category, itemCount, List.of());
        }
    }
}
