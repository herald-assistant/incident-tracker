package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.BlobAttachment;
import com.github.copilot.sdk.json.MessageAttachment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CopilotAttachmentArtifactService {

    private final CopilotSdkProperties properties;
    private final ObjectMapper objectMapper;

    public List<AttachmentArtifactDescriptor> describe(AnalysisAiAnalysisRequest request) {
        var descriptors = new ArrayList<AttachmentArtifactDescriptor>();
        descriptors.add(new AttachmentArtifactDescriptor(
                "00-incident-manifest.json",
                "Artifact index and analysis context",
                null,
                null,
                null
        ));

        var sections = request.evidenceSections();
        for (int index = 0; index < sections.size(); index++) {
            var section = sections.get(index);
            descriptors.add(new AttachmentArtifactDescriptor(
                    buildSectionFileName(index + 1, section),
                    "Evidence section from provider `%s` in category `%s`".formatted(
                            normalizeDescriptorValue(section.provider()),
                            normalizeDescriptorValue(section.category())
                    ),
                    section.provider(),
                    section.category(),
                    section.items().size()
            ));
        }

        return List.copyOf(descriptors);
    }

    public CopilotAttachmentArtifactBundle create(
            AnalysisAiAnalysisRequest request,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        var descriptors = describe(request);
        try {
            var attachments = new ArrayList<MessageAttachment>();
            attachments.add(createBlobAttachment(
                    "00-incident-manifest.json",
                    "application/json",
                    renderJson(buildManifestPayload(request, descriptors, toolAccessPolicy))
            ));

            for (int index = 0; index < request.evidenceSections().size(); index++) {
                var section = request.evidenceSections().get(index);
                var descriptor = descriptors.get(index + 1);
                attachments.add(createBlobAttachment(
                        descriptor.displayName(),
                        mimeTypeFor(descriptor.displayName()),
                        renderSectionArtifact(section)
                ));
            }

            return new CopilotAttachmentArtifactBundle(List.copyOf(attachments));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare Copilot attachment artifacts.", exception);
        }
    }

    private String renderSectionArtifact(AnalysisEvidenceSection section) throws IOException {
        var readableMarkdown = buildReadableMarkdown(section);
        if (readableMarkdown != null) {
            return readableMarkdown;
        }

        return renderJson(buildEvidenceSectionPayload(section));
    }

    private String renderJson(Object payload) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private BlobAttachment createBlobAttachment(String displayName, String mimeType, String content) {
        return new BlobAttachment()
                .setDisplayName(displayName)
                .setMimeType(mimeType)
                .setData(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
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
            List<AttachmentArtifactDescriptor> descriptors,
            CopilotToolAccessPolicy toolAccessPolicy
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("correlationId", request.correlationId());
        payload.put("environment", blankToNull(request.environment()));
        payload.put("gitLabBranch", blankToNull(request.gitLabBranch()));
        payload.put("gitLabGroup", blankToNull(request.gitLabGroup()));
        payload.put("generatedAt", Instant.now().toString());
        payload.put("readFirst", "00-incident-manifest.json");
        payload.put("artifactPolicy", Map.of(
                "attachmentsArePrimarySourceOfTruth", true,
                "useToolsOnlyForGapFilling", true,
                "deliveryMode", "inline-blob"
        ));
        payload.put("toolPolicy", buildToolPolicyPayload(toolAccessPolicy));
        payload.put("artifacts", descriptors.stream().map(this::toManifestEntry).toList());
        return payload;
    }

    private Map<String, Object> buildToolPolicyPayload(CopilotToolAccessPolicy toolAccessPolicy) {
        var policy = toolAccessPolicy != null
                ? toolAccessPolicy
                : new CopilotToolAccessPolicy(List.of(), List.of(), true, false, false, false, false, false);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("localWorkspaceAccessBlocked", policy.localWorkspaceAccessBlocked());
        payload.put("enabledToolNames", policy.availableToolNames());
        payload.put("enabledCapabilityGroups", policy.enabledCapabilityGroups());
        payload.put("disabledCapabilityGroups", policy.disabledCapabilityGroups());
        return payload;
    }

    private Map<String, Object> toManifestEntry(AttachmentArtifactDescriptor descriptor) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("displayName", descriptor.displayName());
        payload.put("role", descriptor.role());
        payload.put("provider", blankToNull(descriptor.provider()));
        payload.put("category", blankToNull(descriptor.category()));
        payload.put("itemCount", descriptor.itemCount());
        return payload;
    }

    private Map<String, Object> buildEvidenceSectionPayload(AnalysisEvidenceSection section) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("provider", section.provider());
        payload.put("category", section.category());
        payload.put("itemCount", section.items().size());
        payload.put("items", section.items().stream().map(this::toEvidenceItemPayload).toList());
        return payload;
    }

    private Map<String, Object> toEvidenceItemPayload(AnalysisEvidenceItem item) {
        var payload = new LinkedHashMap<String, Object>();
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

    public record AttachmentArtifactDescriptor(
            String displayName,
            String role,
            String provider,
            String category,
            Integer itemCount
    ) {
    }
}
