# Deduplikacja instruction context dla Change Verification

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Diagnostyczny eksport Change Verification dla wielu merge requestow tego samego
repozytorium i refu zawieral osiem identycznych kopii kazdego pliku instrukcji.
Powtorzenia zwiekszyly initial prompt o 175 560 znakow i przyspieszyly osiagniecie
progu runtime context upgrade bez dodania nowego evidence.

## Proponowane rozwiazanie

Neutralny `InstructionContextDiscoveryService` scali przed discovery scope'y o
tym samym `repositoryKey` i `ref`, zachowujac kolejnosc pierwszego wystapienia i
unie zmienionych plikow. Deduplikacja pozostaje w capability integracji, poniewaz
to ona jest wlascicielem semantyki `InstructionContextRequest`.

## Zakres

- deduplikacja rownowaznych scope'ow instruction discovery;
- bezstratne polaczenie `changedFilePaths`;
- regresja Change Verification i operatorskiego API GitLab;
- test jednostkowy potwierdzajacy pojedynczy odczyt repozytorium i unie sciezki.

## Non-goals

- zmiana context tier, runtime resume albo modelu AI;
- zmiana wyboru source/target ref dla scalonych merge requestow;
- zmiana limitow i obcinania pojedynczych plikow instrukcji;
- zmiana publicznego DTO, eksportu, reportu, prompt guidance albo UI.

## Ograniczenia i ryzyka

Zmiana dotyka reusable integracji. Konsumentami sa Change Verification oraz
operatorskie API GitLab. API tworzy jeden scope, wiec jego zachowanie pozostaje
bez zmian. Dla wielu scope'ow tego samego repozytorium/refu unia sciezek moze
zwiekszyc liste `applicableChangedFiles`, ale nie dodaje instrukcji spoza lacznego
zakresu wejsciowego.

## Baseline

- Change Verification tworzy osobny scope dla kazdego merge requestu.
- Scope'y po fallbacku usunietych branchy moga wskazywac ten sam target ref.
- Discovery izoluje mape znalezionych instrukcji per scope, przez co zwraca
  identyczne `InstructionSource` wielokrotnie.
- Prompt osadza cala liste `InstructionContextResult.sources()`.
- Operatorskie API GitLab przekazuje pojedynczy scope.

## Conformance delta

- Cel zmiany: jedna kopia instrukcji per `repositoryKey + ref + path`.
- Warstwa bedaca wlascicielem: `integrations.gitlab.instructions`.
- Publiczne API/DTO: bez zmian.
- Context/evidence: rownowazne scope'y sa scalane, changed files tworza unie.
- Prompt/artifacts/skills: bez zmiany formatu; mniej powtorzonej tresci.
- Tools/policy/hidden scope/budzet: bez zmian.
- Report/result/job state/persistence/export/shared FE: bez zmian.
- Zaleznosci i graf pakietow: bez zmian.
- Konsumenci: Change Verification oraz operatorskie API GitLab.
- Kompatybilnosc/migracja: brak migracji; zmiana jest bezstratna.
- Testy regresji: test serwisu instrukcji, Change Verification source discovery,
  GitLab API oraz pelny backend.
- Dokumentacja: ten plan; brak zmiany kanonicznych kontraktow.
- Znany drift: problemy runtime context tier pozostaja poza zakresem.

## Kryteria akceptacji

- rownowazne scope'y uruchamiaja inventory i odczyt kazdego pliku tylko raz;
- wynik zawiera unie zmienionych plikow bez duplikatow;
- rozne repozytoria albo refy pozostaja izolowane;
- istniejace testy backendu przechodza.

## Kroki

- [x] Krok 1: Scalac rownowazne scope'y i dodac celowany test regresyjny.
- [x] Krok 2: Uruchomic regresje konsumentow, pelne testy backendu i architecture diff.
