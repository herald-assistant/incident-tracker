package pl.mkn.tdw.integrations.gitlab.instructions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstructionContextDiscoveryService {

    private static final String ROOT_AGENTS = "AGENTS.md";
    private static final String COPILOT_INSTRUCTIONS = ".github/copilot-instructions.md";
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[[^]]*]\\(([^)#?]+)(?:[#?][^)]*)?\\)");
    private static final Pattern BACKTICK_PATH_PATTERN = Pattern.compile("`([^`]+\\.(?:md|markdown|txt|ya?ml))`", Pattern.CASE_INSENSITIVE);

    private final GitLabRepositoryPort repositoryPort;
    private final InstructionDiscoveryProperties properties;

    public InstructionContextResult discover(InstructionContextRequest request) {
        var limitations = new ArrayList<String>();
        var sources = new ArrayList<InstructionSource>();

        for (var scope : request.scopes()) {
            sources.addAll(discoverScope(scope, limitations));
        }

        return new InstructionContextResult(sources, limitations.stream().distinct().toList());
    }

    private List<InstructionSource> discoverScope(InstructionRepositoryScope scope, List<String> limitations) {
        if (!StringUtils.hasText(scope.repositoryKey()) || !StringUtils.hasText(scope.ref())) {
            limitations.add("Instruction discovery skipped repository with missing key or ref.");
            return List.of();
        }

        var inventory = repositoryPort.loadFileInventory(new InstructionRepositoryInventoryRequest(
                scope.repositoryKey(),
                scope.ref()
        ));
        var existingPaths = inventory != null && inventory.available()
                ? normalizedPaths(inventory.paths())
                : Set.<String>of();
        if (inventory != null && !inventory.available() && StringUtils.hasText(inventory.limitation())) {
            limitations.add(inventory.limitation());
        }

        var candidates = candidateInstructionPaths(scope.changedFilePaths());
        if (inventory != null && inventory.available()) {
            candidates = candidates.stream()
                    .filter(existingPaths::contains)
                    .toList();
        }
        if (inventory != null
                && inventory.available()
                && candidates.size() > properties.getMaxInstructionFiles()) {
            limitations.add("Applicable instruction file limit reached for %s: discovered %d, included %d."
                    .formatted(scope.repositoryKey(), candidates.size(), properties.getMaxInstructionFiles()));
            candidates = candidates.subList(0, properties.getMaxInstructionFiles());
        } else if ((inventory == null || !inventory.available())
                && candidates.size() > properties.getMaxInstructionFiles()) {
            candidates = candidates.subList(0, properties.getMaxInstructionFiles());
        }

        var discovered = new LinkedHashMap<String, InstructionSource>();
        for (var path : candidates) {
            readInstruction(scope, path, null, discovered, limitations);
        }

        var referenced = referencedInstructionPaths(discovered);
        if (inventory != null && inventory.available()) {
            referenced = referenced.stream()
                    .filter(reference -> existingPaths.contains(reference.path()))
                    .toList();
        }
        if (referenced.size() > properties.getMaxReferencedFiles()) {
            limitations.add("Referenced instruction file limit reached for %s: discovered %d, included %d."
                    .formatted(scope.repositoryKey(), referenced.size(), properties.getMaxReferencedFiles()));
            referenced = referenced.subList(0, properties.getMaxReferencedFiles());
        }
        for (var reference : referenced) {
            readInstruction(scope, reference.path(), reference.referencedBy(), discovered, limitations);
        }

        return List.copyOf(discovered.values());
    }

    private void readInstruction(
            InstructionRepositoryScope scope,
            String path,
            String referencedBy,
            Map<String, InstructionSource> discovered,
            List<String> limitations
    ) {
        var normalizedPath = normalizePath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            return;
        }
        var key = scope.repositoryKey() + "@" + scope.ref() + "::" + normalizedPath;
        if (discovered.containsKey(key)) {
            return;
        }

        var file = repositoryPort.readFile(new InstructionRepositoryFileRequest(
                scope.repositoryKey(),
                scope.ref(),
                normalizedPath,
                properties.getMaxFileCharacters()
        ));
        if (file == null || !file.exists()) {
            if (file != null && StringUtils.hasText(file.limitation())) {
                limitations.add(file.limitation());
            }
            return;
        }
        if (file.truncated()) {
            limitations.add("Instruction file was truncated: " + scope.repositoryKey() + "@" + scope.ref() + ":" + normalizedPath);
        }

        discovered.put(key, new InstructionSource(
                scope.repositoryKey(),
                scope.ref(),
                normalizedPath,
                instructionKind(normalizedPath, referencedBy),
                file.content(),
                file.truncated(),
                referencedBy,
                applicableChangedFiles(scope.changedFilePaths(), normalizedPath)
        ));
    }

    private List<String> candidateInstructionPaths(List<String> changedFilePaths) {
        var paths = new LinkedHashSet<String>();
        paths.add(ROOT_AGENTS);
        paths.add(COPILOT_INSTRUCTIONS);

        for (var changedFilePath : changedFilePaths) {
            var normalized = normalizePath(changedFilePath);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            var slash = normalized.lastIndexOf('/');
            while (slash > 0) {
                var directory = normalized.substring(0, slash);
                paths.add(directory + "/" + ROOT_AGENTS);
                slash = directory.lastIndexOf('/');
            }
        }

        return List.copyOf(paths);
    }

    private List<ReferencedInstructionPath> referencedInstructionPaths(Map<String, InstructionSource> discovered) {
        var references = new LinkedHashMap<String, ReferencedInstructionPath>();
        for (var source : discovered.values()) {
            extractReferences(source.content()).forEach(path -> references.putIfAbsent(
                    source.repositoryKey() + "::" + path,
                    new ReferencedInstructionPath(path, source.path())
            ));
        }
        return List.copyOf(references.values());
    }

    private List<String> extractReferences(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        var references = new LinkedHashSet<String>();
        var markdownMatcher = MARKDOWN_LINK_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            addReference(references, markdownMatcher.group(1));
        }

        var backtickMatcher = BACKTICK_PATH_PATTERN.matcher(content);
        while (backtickMatcher.find()) {
            addReference(references, backtickMatcher.group(1));
        }

        return List.copyOf(references);
    }

    private void addReference(LinkedHashSet<String> references, String candidate) {
        var normalized = normalizePath(candidate);
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (!StringUtils.hasText(normalized)
                || normalized.startsWith("http:")
                || normalized.startsWith("https:")
                || normalized.startsWith("#")
                || normalized.equals("..")
                || normalized.startsWith("../")) {
            return;
        }
        var lower = normalized.toLowerCase();
        if (lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".txt")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || normalized.endsWith(ROOT_AGENTS)) {
            references.add(normalized);
        }
    }

    private List<String> applicableChangedFiles(List<String> changedFilePaths, String instructionPath) {
        if (ROOT_AGENTS.equals(instructionPath) || COPILOT_INSTRUCTIONS.equals(instructionPath)) {
            return List.copyOf(changedFilePaths);
        }
        if (!instructionPath.endsWith("/" + ROOT_AGENTS)) {
            return List.of();
        }

        var directory = instructionPath.substring(0, instructionPath.length() - ROOT_AGENTS.length() - 1);
        return changedFilePaths.stream()
                .filter(path -> normalizePath(path).startsWith(directory + "/"))
                .toList();
    }

    private String instructionKind(String path, String referencedBy) {
        if (StringUtils.hasText(referencedBy)) {
            return "REFERENCED";
        }
        if (ROOT_AGENTS.equals(path) || path.endsWith("/" + ROOT_AGENTS)) {
            return "AGENTS";
        }
        if (COPILOT_INSTRUCTIONS.equals(path)) {
            return "COPILOT";
        }
        return "INSTRUCTION";
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private Set<String> normalizedPaths(List<String> paths) {
        if (paths == null) {
            return Set.of();
        }
        return paths.stream()
                .map(this::normalizePath)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ReferencedInstructionPath(String path, String referencedBy) {
    }
}
