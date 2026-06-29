package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDeclareExpectedFlowExplorerRuntimeSkills() {
        assertEquals("flow-explorer-orchestrator", FlowExplorerCopilotRuntimeSkillNames.STARTER_SKILL_NAME);
        assertEquals("flow-explorer-gitlab-tools", FlowExplorerCopilotRuntimeSkillNames.GITLAB_TOOLS_SKILL_NAME);
        assertEquals(
                "flow-explorer-operational-context-tools",
                FlowExplorerCopilotRuntimeSkillNames.OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-result-contract",
                FlowExplorerCopilotRuntimeSkillNames.RESULT_CONTRACT_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-follow-up-chat",
                FlowExplorerCopilotRuntimeSkillNames.FOLLOW_UP_CHAT_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-goal-deep-discovery",
                FlowExplorerCopilotRuntimeSkillNames.DEEP_DISCOVERY_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-goal-test-scenarios",
                FlowExplorerCopilotRuntimeSkillNames.TEST_SCENARIOS_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-goal-risk-detection",
                FlowExplorerCopilotRuntimeSkillNames.RISK_DETECTION_SKILL_NAME
        );
        assertEquals(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-gitlab-tools",
                "flow-explorer-operational-context-tools",
                "flow-explorer-result-contract",
                "flow-explorer-follow-up-chat",
                "flow-explorer-goal-deep-discovery",
                "flow-explorer-goal-test-scenarios",
                "flow-explorer-goal-risk-detection"
        ), FlowExplorerCopilotRuntimeSkillNames.allSkillNames());
        assertEquals(List.of(
                "flow-explorer-follow-up-chat",
                "flow-explorer-gitlab-tools",
                "flow-explorer-operational-context-tools"
        ), FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames());
    }

    @Test
    void shouldKeepFlowExplorerSkillFilesAlignedWithRuntimeContract() throws Exception {
        assertSkillContainsSections("flow-explorer-orchestrator", List.of(
                "## Rola",
                "## Wejscie Sesji",
                "## Sterowanie Analiza",
                "## Algorytm Pracy",
                "## Zasady Kosztowe",
                "## Kiedy Uzyc Tools",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-gitlab-tools", List.of(
                "## Rola",
                "## Scope Tooli",
                "## Algorytm",
                "## Algorytmy Sekcji",
                "## Heurystyki Java/Spring",
                "## Zasady Kosztowe",
                "## Wklad Do Wyniku",
                "## Stop"
        ));
        assertSkillContainsSections("flow-explorer-operational-context-tools", List.of(
                "## Rola Wobec Orkiestratora",
                "## Dozwolone Tools",
                "## Kiedy Uzyc",
                "## Zasady Interpretacji",
                "## Wklad Do Wyniku"
        ));
        assertSkillContainsSections("flow-explorer-result-contract", List.of(
                "## Rola",
                "## Wymagany JSON Contract",
                "## Follow-Up Prompts",
                "## Sekcje I Kolejnosc",
                "## Functional Flow Contract",
                "## Jezyk I Odbiorca",
                "## Persistence Deep Contract",
                "## Integration Boundary Contract",
                "## Source References",
                "## Confidence I Visibility Limits",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-follow-up-chat", List.of(
                "## Rola",
                "## Format Odpowiedzi",
                "## Poglebianie Przez Tools",
                "## Jezyk I Odbiorca",
                "## Widocznosc I Zrodla",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-deep-discovery", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Functional flow",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Compact Vs Deep",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-test-scenarios", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Functional flow",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Format Sekcji",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-risk-detection", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Functional flow",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Format Sekcji",
                "## Antywzorce"
        ));
    }

    @Test
    void shouldKeepFlowExplorerSkillsFeatureScopedAndFreeFromLocalSecrets() throws Exception {
        for (var skillName : FlowExplorerCopilotRuntimeSkillNames.allSkillNames()) {
            var content = Files.readString(SKILLS_ROOT.resolve(skillName).resolve("SKILL.md"));

            assertFalse(content.contains("incident-analysis"), () -> "Incident skill reference leaked into " + skillName);
            assertFalse(content.contains("C:\\"), () -> "Local Windows path leaked into " + skillName);
            assertFalse(content.contains("/Users/"), () -> "Local Unix path leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("githubtoken"), () -> "Token hint leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("password"), () -> "Password hint leaked into " + skillName);
        }
    }

    @Test
    void shouldDescribeExplicitGitLabToolScopeForFlowExplorer() throws Exception {
        var orchestrator = Files.readString(SKILLS_ROOT.resolve("flow-explorer-orchestrator").resolve("SKILL.md"));
        var gitLabTools = Files.readString(SKILLS_ROOT.resolve("flow-explorer-gitlab-tools").resolve("SKILL.md"));
        var deepDiscovery = Files.readString(SKILLS_ROOT.resolve("flow-explorer-goal-deep-discovery").resolve("SKILL.md"));
        var testScenarios = Files.readString(SKILLS_ROOT.resolve("flow-explorer-goal-test-scenarios").resolve("SKILL.md"));
        var riskDetection = Files.readString(SKILLS_ROOT.resolve("flow-explorer-goal-risk-detection").resolve("SKILL.md"));
        var resultContract = Files.readString(SKILLS_ROOT.resolve("flow-explorer-result-contract").resolve("SKILL.md"));
        var followUpChat = Files.readString(SKILLS_ROOT.resolve("flow-explorer-follow-up-chat").resolve("SKILL.md"));
        var operationalContextTools = Files.readString(
                SKILLS_ROOT.resolve("flow-explorer-operational-context-tools").resolve("SKILL.md"));

        assertTrue(orchestrator.contains("`branchRef`"));
        assertTrue(orchestrator.contains("`applicationName`"));
        assertTrue(orchestrator.contains("`flow-explorer/canonical-tool-inputs.md`"));
        assertTrue(orchestrator.contains("`compact-flow-manifest.md` jest kanoniczna lista"));
        assertTrue(orchestrator.contains("Nie zgaduj `projectName`, `projectPath`"));
        assertTrue(orchestrator.contains("Hidden `ToolContext` jest tylko techniczna mechanika runtime"));
        assertTrue(orchestrator.contains("Nie przekazuj `gitLabGroup` do tools"));
        assertTrue(orchestrator.contains("`sectionModes` sa zrodlem prawdy dla sekcji wyniku"));
        assertTrue(orchestrator.contains("`OFF` oznacza: nie zwracaj tej sekcji w `sections`"));
        assertTrue(orchestrator.contains("`focusAreas` nie sa celem"));
        assertTrue(orchestrator.contains("`reasoningEffort` okresla glebokosc eksploracji"));
        assertTrue(orchestrator.contains("`.github/copilot-instructions.md`, jezeli jest dostepny"));
        assertTrue(orchestrator.contains("repozytoryjny material pomocniczy"));
        assertTrue(orchestrator.contains("`goal`, `sectionModes`,"));
        assertTrue(orchestrator.contains("maja bezwzgledne pierwszenstwo"));
        assertTrue(orchestrator.contains("focused GitLab read dokladnej sciezki"));
        assertTrue(orchestrator.contains("preferuj `gitlab_build_java_method_use_case_context`"));
        assertTrue(orchestrator.contains("uzyj `gitlab_read_java_method_slice`"));

        assertTrue(gitLabTools.contains("GitLab tools nie czytaja functional scope'u z hidden `ToolContext`"));
        assertTrue(gitLabTools.contains("`flow-explorer/canonical-tool-inputs.md`"));
        assertTrue(gitLabTools.contains("`filePath` i `methodSelectors`"));
        assertTrue(gitLabTools.contains("Nie zaczynaj od `gitlab_read_repository_file`"));
        assertTrue(gitLabTools.contains("Po `truncated=true` nie wnioskuj o dalszej czesci pliku"));
        assertTrue(gitLabTools.contains("`totalLines`, `returnedStartLine` i"));
        assertTrue(gitLabTools.contains("`branchRef`, `applicationName` i `projectName`"));
        assertTrue(gitLabTools.contains("`gitlab_build_java_method_use_case_context`"));
        assertTrue(gitLabTools.contains("z `maxResults`"));
        assertTrue(gitLabTools.contains("Nie przekazuj `gitLabGroup`"));
        assertTrue(gitLabTools.contains("## Algorytm"));
        assertTrue(gitLabTools.contains("## Algorytmy Sekcji"));
        assertTrue(gitLabTools.contains("Spring Data/JPA/Hibernate first"));
        assertTrue(gitLabTools.contains("Liquibase/Flyway/DDL czytaj w sytuacji niespojnosci"));
        assertTrue(gitLabTools.contains("`@Entity`, `@Table`, `@Column`"));
        assertTrue(gitLabTools.contains("obowiazkowo uwzglednij kolumny z dziedziczenia"));
        assertTrue(gitLabTools.contains("`@MappedSuperclass`, `@Embedded`/kompozycji"));
        assertTrue(gitLabTools.contains("Mapowanie kolumn czytaj gleboko przez `extends`"));
        assertTrue(gitLabTools.contains("Nie wolno pominac"));
        assertTrue(gitLabTools.contains("metode obiektu zlozonego"));
        assertTrue(gitLabTools.contains("typach prostych mapowanych na kolumny"));
        assertTrue(gitLabTools.contains("`List<Interface>`"));
        assertTrue(gitLabTools.contains("`@FeignClient`"));
        assertTrue(gitLabTools.contains("`StreamBridge.send(...)`"));
        assertTrue(gitLabTools.contains("`@Bean Consumer<T>`"));
        assertTrue(gitLabTools.contains("`spring.cloud.stream.bindings.<binding>.*`"));
        assertTrue(gitLabTools.contains("YAML/properties traktuj jako resolver"));
        assertTrue(gitLabTools.contains("szukaj tylko granic poza analizowany komponent/system"));
        assertTrue(gitLabTools.contains("Dla `COMPACT` przygotuj liste wszystkich zewnetrznych systemow/kanalow"));
        assertTrue(gitLabTools.contains("Dla `DEEP` domknij kontrakt: path/destination, payload"));
        assertTrue(gitLabTools.contains("`@FeignClient` czytaj interfejs/klase klienta"));
        assertTrue(gitLabTools.contains("method-level mapping"));
        assertTrue(gitLabTools.contains("Dla `RestClient`, `WebClient` i `RestTemplate` ustal metode HTTP"));
        assertTrue(gitLabTools.contains("Dla `StreamBridge.send(...)` ustal binding name"));
        assertTrue(gitLabTools.contains("Consumer/listener traktuj jako `INTEGRATIONS` tylko wtedy"));
        assertTrue(gitLabTools.contains("YAML/properties dla integracji czytaj po nazwach z kodu"));
        assertTrue(gitLabTools.contains("`TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS`"));
        assertTrue(gitLabTools.contains("`.github/copilot-instructions.md`"));
        assertTrue(gitLabTools.contains("tylko jako repository guidance"));
        assertFalse(gitLabTools.contains("`gitLabGroup` i `gitLabBranch` pochodza z hidden ToolContext"));

        assertTrue(deepDiscovery.contains("| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |"));
        assertTrue(deepDiscovery.contains("`SOURCE` jest obowiazkowe"));
        assertTrue(deepDiscovery.contains("`GENERATED`"));
        assertTrue(deepDiscovery.contains("`REQUEST`"));
        assertTrue(deepDiscovery.contains("`CALCULATED`"));
        assertTrue(deepDiscovery.contains("biznesowa nazwa systemu albo komponentu"));
        assertTrue(deepDiscovery.contains("Nie koncz `PERSISTENCE=DEEP` bez ustalenia `SOURCE`"));
        assertTrue(deepDiscovery.contains("szczegoly implementacyjne nie sa trescia tabeli wynikowej"));
        assertTrue(deepDiscovery.contains("Sekcja `INTEGRATIONS` w `DEEP_DISCOVERY` opisuje tylko integracje poza"));
        assertTrue(deepDiscovery.contains("wszystkie widoczne zewnetrzne systemy, kanaly albo handoffy"));
        assertTrue(deepDiscovery.contains("dokladny kontrakt HTTP albo asynchroniczny"));
        assertTrue(deepDiscovery.contains("naglowki, content type, correlation id"));
        assertTrue(deepDiscovery.contains("timeouty, retry, fallback, DLQ"));

        assertTrue(testScenarios.contains("Sekcja `INTEGRATIONS` w `TEST_SCENARIOS` dotyczy tylko testowania zaleznosci"));
        assertTrue(testScenarios.contains("dla kazdego widocznego zewnetrznego systemu albo kanalu"));
        assertTrue(testScenarios.contains("adres/path/destination/topic/queue/binding"));
        assertTrue(testScenarios.contains("request/response, payload eventu, naglowki"));
        assertTrue(testScenarios.contains("setup danych i stubow dla HTTP path/URL template"));

        assertTrue(riskDetection.contains("Sekcja `INTEGRATIONS` w `RISK_DETECTION` ocenia tylko ryzyka na granicach"));
        assertTrue(riskDetection.contains("ryzyka dla kazdego widocznego zewnetrznego systemu"));
        assertTrue(riskDetection.contains("nieznanym payloadzie, brakujacym naglowku"));
        assertTrue(riskDetection.contains("query/path params, naglowki, content type"));

        assertTrue(resultContract.contains("## Persistence Deep Contract"));
        assertTrue(resultContract.contains("## Integration Boundary Contract"));
        assertTrue(resultContract.contains("`FUNCTIONAL_FLOW` ma tytul `Functional flow`"));
        assertTrue(resultContract.contains("**Cel funkcjonalny:**"));
        assertTrue(resultContract.contains("**Flow krok po kroku:**"));
        assertTrue(resultContract.contains("**Koordynacja i routing:**"));
        assertTrue(resultContract.contains("**Kalkulacje i reguly funkcjonalne:**"));
        assertTrue(resultContract.contains("**Rozgalezienia zalezne od kontekstu:**"));
        assertTrue(resultContract.contains("**Handoffy i efekty uboczne:**"));
        assertTrue(resultContract.contains("**Akcent goal:**"));
        assertTrue(resultContract.contains("Kazdy z tych punktow ma byc czytelny jako lista albo kroki"));
        assertTrue(resultContract.contains("Poziom szczegolow ma wynikac ze zlozonosci flow"));
        assertTrue(resultContract.contains("Techniczne typy, statusy, enumy, stale i wartosci graniczne"));
        assertTrue(resultContract.contains("gdy maja znaczenie funkcjonalne"));
        assertTrue(resultContract.contains("UI pokazuje je osobno jako zwijane elementy"));
        assertTrue(resultContract.contains("`SOURCE` jest polem kontrolowanym"));
        assertTrue(resultContract.contains("Dozwolone wartosci to tylko"));
        assertTrue(resultContract.contains("Nie wpisuj w `SOURCE` ani `SOURCE DETAILS` nazw klas"));
        assertTrue(resultContract.contains("Tabela ma obejmowac kolumny widoczne przez mapowanie ORM"));
        assertTrue(resultContract.contains("klas bazowych i `@MappedSuperclass`"));
        assertTrue(resultContract.contains("`@Embedded`, `@Embeddable` i `@AttributeOverride(s)`"));
        assertTrue(resultContract.contains("Nie pomijaj kolumn parenta ani kompozycji"));
        assertTrue(resultContract.contains("bezposrednio mapowanego na kolumne"));
        assertTrue(resultContract.contains("opisuje wylacznie komunikacje"));
        assertTrue(resultContract.contains("Nie wypelniaj sekcji architektura wewnetrzna"));
        assertTrue(resultContract.contains("| System/target | Typ | Adres/kanal/path | Moment w flow |"));
        assertTrue(resultContract.contains("HTTP method + path, URL template, destination, topic"));
        assertTrue(resultContract.contains("`@FeignClient`, `contextId`, property prefix"));
        assertTrue(resultContract.contains("`INTEGRATIONS=deep` zawiera wszystko z compact"));
        assertTrue(resultContract.contains("`Headers/auth/metadane`"));
        assertTrue(resultContract.contains("timeout, retry"));
        assertTrue(resultContract.contains("DLQ, idempotencja"));
        assertTrue(resultContract.contains("`tool:gitlab_build_java_method_use_case_context`"));

        assertTrue(followUpChat.contains("Domyslnie odpowiadaj w Markdown"));
        assertTrue(followUpChat.contains("Nie zwracaj pelnego JSON `flow-explorer-result-contract`"));
        assertTrue(followUpChat.contains("Nie zakladaj, ze initial analysis przeczytala cala implementacje"));
        assertTrue(followUpChat.contains("Domyslnie uzyj dostepnych tools przed odpowiedzia"));
        assertTrue(followUpChat.contains("Docelowy odbiorca to analityk albo tester"));
        assertTrue(followUpChat.contains("Nie zaczynaj od nazw klas, metod, beanow, plikow ani tooli"));

        assertTrue(operationalContextTools.contains("### SOURCE Dla Persistence Deep"));
        assertTrue(operationalContextTools.contains("nazwac `SOURCE` biznesowo"));
        assertTrue(operationalContextTools.contains("Nie wpisuj jako `SOURCE` nazw"));
    }

    private static void assertSkillContainsSections(String skillName, List<String> requiredSections) throws Exception {
        var skillFile = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");

        assertTrue(Files.exists(skillFile), () -> "Missing runtime skill: " + skillName);

        var content = Files.readString(skillFile);

        assertTrue(content.contains("name: " + skillName), () -> "Missing frontmatter name for " + skillName);
        for (var section : requiredSections) {
            assertTrue(content.contains(section), () -> "Missing section '" + section + "' in " + skillName);
        }
    }
}
