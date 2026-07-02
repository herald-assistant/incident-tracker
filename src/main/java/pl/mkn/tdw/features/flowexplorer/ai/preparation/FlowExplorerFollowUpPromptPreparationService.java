package pl.mkn.tdw.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.List;
import java.util.Map;

@Service
public class FlowExplorerFollowUpPromptPreparationService {

    public FlowExplorerPromptPreparation prepare(
            FlowExplorerJobStartRequest initialRequest,
            FlowExplorerContextSnapshot contextSnapshot,
            String message
    ) {
        return new FlowExplorerPromptPreparation(
                prompt(initialRequest, contextSnapshot, message),
                List.of(),
                Map.of()
        );
    }

    private String prompt(
            FlowExplorerJobStartRequest initialRequest,
            FlowExplorerContextSnapshot contextSnapshot,
            String message
    ) {
        return """
                # Flow Explorer follow-up chat

                ## Runtime envelope
                - To jest turn follow-up chat po zakonczonym wyniku Flow Explorera, a nie ponowne wygenerowanie initial result.
                - Domyslnie odpowiedz w Markdown jak zwykla odpowiedz AI w rozmowie.
                - Nie zwracaj pelnego JSON `flow-explorer-write-report` ani obiektu z `goal`, `overview` i `sections`, chyba ze uzytkownik w tej wiadomosci wyraznie prosi o JSON albo regeneracje pelnego wyniku Flow Explorera.
                - Jezeli uzytkownik prosi o inna forme, np. tabele, checklist, JSON, liste testow albo krotkie podsumowanie, zastosuj te forme tylko dla tej odpowiedzi chatu.
                - Zasady rozmowy pochodza ze skilla `flow-explorer-follow-up-chat`; uzyj go przed odpowiedzia.

                ## Follow-up exploration policy
                - Nie zakladaj, ze initial analysis przeczytala cala implementacje endpointu. Initial result i snippet cards sa punktem startowym, nie pelnym dowodem.
                - Gdy pytanie dotyczy poglebienia, potwierdzenia, doprecyzowania, doszczegolowienia, walidacji, persistence, integracji, edge case albo korekty initial wyniku, domyslnie uzyj dostepnych Flow Explorer tools przed odpowiedzia.
                - Dla kodu uzywaj focused GitLab reads/search zgodnie ze skillem `flow-explorer-code-grounding`; dla nazw domenowych, procesu, bounded contextu, ownershipu, glossary albo handoffu uzywaj `flow-explorer-operational-grounding`.
                - Dla sekcji persistence uzywaj `flow-explorer-map-persistence-section`; dla zewnetrznych systemow, eventow, kolejek, payloadow albo handoffow uzywaj `flow-explorer-map-integrations-section`.
                - Jezeli tool jest niedostepny, odrzucony albo nie daje wystarczajacego materialu, odpowiedz najlepiej jak sie da i jawnie nazwij ograniczenie widocznosci.

                ## Audience and language
                - Docelowy odbiorca to analityk albo tester, tak jak w initial result.
                - Odpowiadaj jezykiem domenowym i procesowym: co system robi, jaki warunek obsluguje, jaki stan danych zmienia albo jaki wariant procesu rozroznia.
                - Nie zaczynaj odpowiedzi od nazw klas, metod, beanow, plikow ani tooli. Nazwy techniczne pokazuj tylko jako dowod, source ref albo doprecyzowanie, gdy pomaga to zweryfikowac odpowiedz.
                - Gdy implementacja jest jedynym zrodlem terminu domenowego, oznacz go jako inferencje i wskaz, czego brakuje w operational context.

                ## Current Flow Explorer scope
                systemId: %s
                endpointId: %s
                httpMethod: %s
                endpointPath: %s
                branchRef: %s
                goal: %s
                focusAreas: %s
                reasoningEffort: %s
                endpointResolved: %s
                repositoryScopeResolved: %s

                ## User message
                %s
                """.formatted(
                value(initialRequest != null ? initialRequest.systemId() : null),
                value(initialRequest != null ? initialRequest.endpointId() : null),
                value(initialRequest != null ? initialRequest.httpMethod() : null),
                value(initialRequest != null ? initialRequest.endpointPath() : null),
                value(contextSnapshot != null ? contextSnapshot.resolvedRef() : initialRequest != null ? initialRequest.branch() : null),
                initialRequest != null && initialRequest.goal() != null ? initialRequest.goal() : "n/a",
                initialRequest != null && initialRequest.focusAreas() != null ? initialRequest.focusAreas() : List.of(),
                reasoningEffort(initialRequest),
                contextSnapshot != null && contextSnapshot.coverage() != null
                        && contextSnapshot.coverage().endpointResolved(),
                contextSnapshot != null && contextSnapshot.repositories() != null
                        && contextSnapshot.repositories().stream().anyMatch(repository ->
                        StringUtils.hasText(repository.projectName())),
                userMessage(message)
        ).trim();
    }

    private String reasoningEffort(FlowExplorerJobStartRequest request) {
        if (request == null || request.aiOptions() == null
                || !StringUtils.hasText(request.aiOptions().reasoningEffort())) {
            return "default backend";
        }
        return request.aiOptions().reasoningEffort();
    }

    private String userMessage(String message) {
        return StringUtils.hasText(message)
                ? message.trim()
                : "(empty message)";
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "n/a";
    }
}
