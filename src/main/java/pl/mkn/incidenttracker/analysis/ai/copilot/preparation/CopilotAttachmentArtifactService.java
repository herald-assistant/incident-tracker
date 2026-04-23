package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.Attachment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceReadableView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceReadableView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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

    public CopilotAttachmentArtifactBundle create(AnalysisAiAnalysisRequest request) {
        var descriptors = describe(request);
        Path stagingDirectory = null;

        try {
            stagingDirectory = createStagingDirectory(request.correlationId());
            var attachments = new ArrayList<Attachment>();

            writeJsonArtifact(
                    stagingDirectory.resolve("00-incident-manifest.json"),
                    buildManifestPayload(request, descriptors)
            );
            attachments.add(new Attachment(
                    "file",
                    stagingDirectory.resolve("00-incident-manifest.json").toAbsolutePath().toString(),
                    "00-incident-manifest.json"
            ));

            for (int index = 0; index < request.evidenceSections().size(); index++) {
                var section = request.evidenceSections().get(index);
                var descriptor = descriptors.get(index + 1);
                var artifactPath = stagingDirectory.resolve(descriptor.displayName());
                writeSectionArtifact(artifactPath, section);
                attachments.add(new Attachment(
                        "file",
                        artifactPath.toAbsolutePath().toString(),
                        descriptor.displayName()
                ));
            }

            return new CopilotAttachmentArtifactBundle(List.copyOf(attachments), stagingDirectory);
        } catch (IOException exception) {
            if (stagingDirectory != null) {
                new CopilotAttachmentArtifactBundle(List.of(), stagingDirectory).close();
            }
            throw new IllegalStateException("Failed to prepare Copilot attachment artifacts.", exception);
        }
    }

    private Path createStagingDirectory(String correlationId) throws IOException {
        var root = Path.of(properties.getAttachmentArtifactDirectory());
        Files.createDirectories(root);
        return Files.createTempDirectory(root, sanitize(correlationId) + "-");
    }

    private void writeJsonArtifact(Path artifactPath, Object payload) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(artifactPath.toFile(), payload);
    }

    private void writeSectionArtifact(Path artifactPath, AnalysisEvidenceSection section) throws IOException {
        var readableMarkdown = buildReadableMarkdown(section);
        if (readableMarkdown != null) {
            Files.writeString(artifactPath, readableMarkdown);
            return;
        }

        writeJsonArtifact(artifactPath, buildEvidenceSectionPayload(section));
    }

    private Map<String, Object> buildManifestPayload(
            AnalysisAiAnalysisRequest request,
            List<AttachmentArtifactDescriptor> descriptors
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
                "useToolsOnlyForGapFilling", true
        ));
        payload.put("artifacts", descriptors.stream().map(this::toManifestEntry).toList());
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
        return DynatraceRuntimeEvidenceReadableView.matches(section)
                || GitLabResolvedCodeEvidenceReadableView.matches(section);
    }

    private String buildReadableMarkdown(AnalysisEvidenceSection section) {
        if (DynatraceRuntimeEvidenceReadableView.matches(section)) {
            return DynatraceRuntimeEvidenceReadableView.from(section).toMarkdown();
        }

        if (GitLabResolvedCodeEvidenceReadableView.matches(section)) {
            return GitLabResolvedCodeEvidenceReadableView.from(section).toMarkdown();
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
