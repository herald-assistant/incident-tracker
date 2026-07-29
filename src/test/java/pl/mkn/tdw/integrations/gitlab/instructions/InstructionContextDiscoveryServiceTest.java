package pl.mkn.tdw.integrations.gitlab.instructions;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionContextDiscoveryServiceTest {

    @Test
    void shouldDiscoverRootLocalCopilotAndReferencedInstructionFiles() {
        var properties = new InstructionDiscoveryProperties();
        var service = new InstructionContextDiscoveryService(new TestGitLabRepositoryPort(), properties);

        var result = service.discover(new InstructionContextRequest(List.of(new InstructionRepositoryScope(
                "CRM/runtime/customer-api",
                "feature/CRM-123",
                List.of("src/main/java/com/example/customer/CustomerController.java")
        ))));

        assertThat(result.sources())
                .extracting(InstructionSource::path)
                .containsExactly(
                        "AGENTS.md",
                        ".github/copilot-instructions.md",
                        "src/main/java/com/example/customer/AGENTS.md",
                        "docs/architecture-instructions.md"
                );
        assertThat(result.sources())
                .filteredOn(source -> "docs/architecture-instructions.md".equals(source.path()))
                .singleElement()
                .extracting(InstructionSource::referencedBy)
                .isEqualTo(".github/copilot-instructions.md");
        assertThat(result.limitations()).isEmpty();
    }

    @Test
    void shouldApplyInstructionLimitOnlyAfterRepositoryConfirmsExistingFiles() {
        var properties = new InstructionDiscoveryProperties();
        properties.setMaxInstructionFiles(3);
        var service = new InstructionContextDiscoveryService(new TestGitLabRepositoryPort(), properties);
        var changedFiles = Stream.concat(
                        IntStream.range(0, 100)
                                .mapToObj(index -> "src/main/java/com/example/module" + index + "/Handler.java"),
                        Stream.of("src/main/java/com/example/customer/CustomerController.java")
                )
                .toList();

        var result = service.discover(new InstructionContextRequest(List.of(new InstructionRepositoryScope(
                "CRM/runtime/customer-api",
                "main",
                changedFiles
        ))));

        assertThat(result.sources())
                .extracting(InstructionSource::path)
                .containsExactly(
                        "AGENTS.md",
                        ".github/copilot-instructions.md",
                        "src/main/java/com/example/customer/AGENTS.md",
                        "docs/architecture-instructions.md"
                );
        assertThat(result.limitations()).isEmpty();
    }

    @Test
    void shouldDescribeLimitForExistingApplicableInstructionFiles() {
        var properties = new InstructionDiscoveryProperties();
        properties.setMaxInstructionFiles(2);
        var service = new InstructionContextDiscoveryService(new TestGitLabRepositoryPort(), properties);

        var result = service.discover(new InstructionContextRequest(List.of(new InstructionRepositoryScope(
                "CRM/runtime/customer-api",
                "main",
                List.of("src/main/java/com/example/customer/CustomerController.java")
        ))));

        assertThat(result.sources())
                .extracting(InstructionSource::path)
                .containsExactly(
                        "AGENTS.md",
                        ".github/copilot-instructions.md",
                        "docs/architecture-instructions.md"
                );
        assertThat(result.limitations()).containsExactly(
                "Applicable instruction file limit reached for CRM/runtime/customer-api: discovered 3, included 2."
        );
    }

    private static final class TestGitLabRepositoryPort implements GitLabRepositoryPort {

        private static final Map<String, String> FILES = Map.of(
                "AGENTS.md",
                "Use package boundaries.",
                ".github/copilot-instructions.md",
                "Follow [architecture](docs/architecture-instructions.md).",
                "src/main/java/com/example/customer/AGENTS.md",
                "Controllers must stay thin.",
                "docs/architecture-instructions.md",
                "Hexagonal boundaries apply."
        );

        @Override
        public InstructionRepositoryFile readFile(InstructionRepositoryFileRequest request) {
            var content = FILES.get(request.path());
            if (content == null) {
                return InstructionRepositoryFile.missing(request.repositoryKey(), request.ref(), request.path());
            }
            return new InstructionRepositoryFile(
                    request.repositoryKey(),
                    request.ref(),
                    request.path(),
                    true,
                    content,
                    false,
                    null
            );
        }

        @Override
        public InstructionRepositoryInventory loadFileInventory(InstructionRepositoryInventoryRequest request) {
            return InstructionRepositoryInventory.available(FILES.keySet().stream().toList());
        }

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFile> listRepositoryFiles(
                String group,
                String projectName,
                String branch,
                String pathPrefix
        ) {
            return List.of();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return null;
        }

        @Override
        public GitLabRepositoryFileChunk readFileChunk(
                String group,
                String projectName,
                String branch,
                String filePath,
                int startLine,
                int endLine,
                int maxCharacters
        ) {
            return null;
        }
    }
}
