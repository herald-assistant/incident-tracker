package pl.mkn.tdw.integrations.gitlab.instructions;

import pl.mkn.tdw.integrations.gitlab.config.InstructionDiscoveryProperties;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventory;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventoryRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionSource;
import pl.mkn.tdw.integrations.gitlab.service.instructions.InstructionContextDiscoveryService;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabInstructionRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InstructionContextDiscoveryServiceTest {

        private static final String COMMIT = "b".repeat(40);
        private static final InstructionContextRequest SCOPED = new InstructionContextRequest(List.of(
                        new InstructionRepositoryScope("CRM/customer", COMMIT, List.of("module/src/db/changelog/customer.sql"))));

        private GitLabInstructionRepositoryPort instructionFiles(Map<String, String> files) {
                var port = mock(GitLabInstructionRepositoryPort.class);
                when(port.loadFileInventory(any())).thenReturn(InstructionRepositoryInventory.available(List.copyOf(files.keySet())));
                when(port.readFile(any())).thenAnswer(invocation -> {
                        InstructionRepositoryFileRequest request = invocation.getArgument(0);
                        assertThat(request.ref()).isEqualTo(COMMIT);
                        return new InstructionRepositoryFile(request.repositoryKey(), request.ref(), request.path(),
                                        true, files.get(request.path()), false, null);
                });
                return port;
        }

        @Test void selectsGlobalAndMatchingNestedInstructionsAtPinnedCommit() {
                var port = instructionFiles(Map.of(
                                ".github/global.instructions.md", "Describe CRM changes.",
                                ".github/instructions/sql.instructions.md", "---\napplyTo: '**/db/changelog/**,**/*.csv'\n---\nExplain customer labels.",
                                ".github/instructions/java.instructions.md", "---\napplyTo: '**/*.java'\n---\nJava rules.",
                                "module/pom.xml", "Not an instruction."));
                var result = new InstructionContextDiscoveryService(port, new InstructionDiscoveryProperties()).discoverApplicable(SCOPED);
                assertThat(result.limitations()).isEmpty();
                assertThat(result.sources()).extracting(InstructionSource::path).containsExactly(
                                ".github/global.instructions.md", ".github/instructions/sql.instructions.md");
                assertThat(result.sources()).allSatisfy(source -> {
                        assertThat(source.ref()).isEqualTo(COMMIT);
                        assertThat(source.applicableChangedFiles()).containsExactly("module/src/db/changelog/customer.sql");
                });
                verify(port, never()).readFile(argThat(request -> request.path().equals("module/pom.xml")));
        }

        @Test void malformedAndUnsupportedApplyToNeverBecomeGlobalInstructions() {
                var port = instructionFiles(Map.of(
                                ".github/broken.instructions.md", "---\napplyTo: [\n---\nBroken.",
                                ".github/unsupported.instructions.md", "---\napplyTo: '**/*.{sql,csv}'\n---\nUnsupported.",
                                ".github/open.instructions.md", "---\napplyTo: '**/*.sql'\nNo closing frontmatter."));
                var result = new InstructionContextDiscoveryService(port, new InstructionDiscoveryProperties()).discoverApplicable(SCOPED);
                assertThat(result.sources()).isEmpty();assertThat(result.limitations()).hasSize(3);
        }

        @Test void limitsAndTruncationAreExplicitAndDoNotIncludePartialInstructions() {
                var port = instructionFiles(Map.of(".github/a.instructions.md", "CRM", ".github/b.instructions.md", "CRM"));
                doReturn(new InstructionRepositoryFile("CRM/customer", COMMIT,
                        ".github/a.instructions.md", true, "Partial", true, "Limit")).when(port).readFile(any());
                var properties = new InstructionDiscoveryProperties();properties.setMaxInstructionFiles(1);
                var result = new InstructionContextDiscoveryService(port, properties).discoverApplicable(SCOPED);
                assertThat(result.sources()).isEmpty();assertThat(result.limitations()).hasSize(2);
                verify(port, times(1)).readFile(any());
        }

        @Test void scopedDiscoveryDoesNotReadMutableRefsOrInventFilesWithoutInventory() {
                var port = instructionFiles(Map.of());
                var service = new InstructionContextDiscoveryService(port, new InstructionDiscoveryProperties());
                var mutable = service.discoverApplicable(new InstructionContextRequest(List.of(
                                new InstructionRepositoryScope("CRM/customer", "main", List.of("db/changelog/customer.sql")))));
                assertThat(mutable.sources()).isEmpty();assertThat(mutable.limitations()).hasSize(1);verifyNoInteractions(port);
                when(port.loadFileInventory(any())).thenReturn(null);
                var unavailable = service.discoverApplicable(SCOPED);
                assertThat(unavailable.sources()).isEmpty();assertThat(unavailable.limitations()).hasSize(1);
                verify(port, never()).readFile(any());
        }

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

    @Test
    void shouldCoalesceEquivalentRepositoryScopesAndMergeChangedFiles() {
        var properties = new InstructionDiscoveryProperties();
        var repositoryPort = new TestGitLabRepositoryPort();
        var service = new InstructionContextDiscoveryService(repositoryPort, properties);
        var controllerPath = "src/main/java/com/example/customer/CustomerController.java";
        var servicePath = "src/main/java/com/example/customer/CustomerService.java";

        var result = service.discover(new InstructionContextRequest(List.of(
                new InstructionRepositoryScope(
                        "CRM/runtime/customer-api",
                        "main",
                        List.of(controllerPath)
                ),
                new InstructionRepositoryScope(
                        "CRM/runtime/customer-api",
                        "main",
                        List.of(servicePath, controllerPath)
                )
        )));

        assertThat(result.sources())
                .extracting(InstructionSource::path)
                .containsExactly(
                        "AGENTS.md",
                        ".github/copilot-instructions.md",
                        "src/main/java/com/example/customer/AGENTS.md",
                        "docs/architecture-instructions.md"
                );
        assertThat(result.sources())
                .filteredOn(source -> "AGENTS.md".equals(source.path()))
                .singleElement()
                .extracting(InstructionSource::applicableChangedFiles)
                .isEqualTo(List.of(controllerPath, servicePath));
        assertThat(repositoryPort.inventoryRequestCount).isEqualTo(1);
        assertThat(repositoryPort.readRequestCountByPath)
                .containsEntry("AGENTS.md", 1)
                .containsEntry(".github/copilot-instructions.md", 1)
                .containsEntry("src/main/java/com/example/customer/AGENTS.md", 1)
                .containsEntry("docs/architecture-instructions.md", 1);
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
        private final Map<String, Integer> readRequestCountByPath = new LinkedHashMap<>();
        private int inventoryRequestCount;

        @Override
        public InstructionRepositoryFile readFile(InstructionRepositoryFileRequest request) {
            readRequestCountByPath.merge(request.path(), 1, Integer::sum);
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
            inventoryRequestCount++;
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
