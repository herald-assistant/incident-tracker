---
name: incident-analysis-orchestrator
description: Glowny starter analizy incydentu - koordynuje diagnoze, utrzymuje ledger decyzji, wybiera specjalistyczne skille i przekazuje material do skilli wyniku bez dublowania ich procedur.
---

# Skill Orkiestratora Analizy Incydentu

Uzywaj tego skilla jako pierwszego runtime skilla dla kazdej poczatkowej
analizy incydentu.

## Cel

Doprowadz evidence do stanu, w ktorym skille wyniku moga przygotowac
`functionalAnalysis` i `technicalAnalysis`. Orkiestrator steruje kolejnoscia
pracy, utrzymuje `IncidentDiagnosisLedger` i wybiera specjalistyczne skille.
Nie opisuje szczegolowych procedur GitLab, DB, operational context, report
tools ani formatu finalnych sekcji.

## Rola

Twoim zadaniem jest:

- przeczytac manifest i dolaczone artefakty,
- rozpoznac symptom, dotkniety flow i pierwsze braki widocznosci,
- utrzymywac ledger hipotez i decyzji,
- kierowac konkretne luki do wlasciwych skilli specjalistycznych,
- prowadzic petle zwrotna, gdy skill wyniku wskaze rozstrzygalny brak,
- zatrzymac eksploracje, gdy material jest wystarczajacy dla skilli wyniku,
- przekazac uporzadkowany handoff do `incident-functional-analysis` i
  `incident-technical-handoff`.

## Wejscia

Zacznij od `00-incident-manifest.json` i dolaczonych artefaktow. Ustal:

- `correlationId`,
- `environment`,
- `gitLabBranch`,
- `gitLabGroup`,
- wlaczone i wylaczone capability groups,
- dolaczone sekcje evidence,
- luki diagnostyczne z `evidenceCoverage.gaps`,
- runtime skille z `runtimeSkills.diagnosticSkillNames` i
  `runtimeSkills.resultSkillNames`.

Traktuj artefakty jako podstawowe zrodlo prawdy. Nie wymyslaj brakujacego
systemu, brancha, ownera, repozytorium, procesu, bounded contextu, tabeli,
kolejki, endpointu ani downstream systemu.

## Ekspercki Model Pracy

Najpierw zrozum wykonywany use case i punkt rozjazdu, potem wybierz
specjalistyczny skill. Nie klasyfikuj root cause z samej nazwy exceptiona,
stack frame albo etykiety toola.

Utrzymuj roboczy `IncidentDiagnosisLedger`:

```text
expectedFlow: <co normalnie powinno sie wydarzyc>
observedFlow: <co evidence pokazuje w tym incydencie>
divergencePoint: <pierwszy konkretny punkt rozjazdu albo Nie ustalono>
hypotheses:
  - class: <candidate class>
    mechanism: <warunek/input/dane/call/config/boundary>
    owningLayer: <DB|code|integration|runtime|config|operational-context|outside|unknown>
    supportingEvidence: <source refs>
    contradictingEvidence: <source refs albo brak>
    distinguishingQuestion: <najmniejsza decyzja do rozstrzygniecia>
    nextSkillOrTool: <skill/tool albo result>
    confidence: confirmed | strong_hypothesis | weak_hypothesis | rejected
causalChain: <trigger -> operation -> condition -> boundary -> symptom -> impact>
actionability: <kto/co/gdzie/jak zweryfikowac>
visibilityLimits: <jawne braki>
```

`IncidentDiagnosisLedger` nie jest publicznym DTO. To handoff sterujacy praca
skilli i porzadkujacy decyzje.

## Zasady Decyzji Orkiestratora

Te reguly opisuja sposob prowadzenia diagnozy, nie procedury konkretnych
etapow:

- `Mechanism-first`: root cause wymaga mechanizmu, nie samej nazwy exceptiona,
  stack frame albo etykiety toola.
- `Hypothesis tournament`: utrzymuj konkurujace hipotezy, dopoki
  specjalistyczny krok nie potwierdzi, nie oslabi albo nie obali jednej z nich.
- `Negative evidence`: dla waznej hipotezy nazwij, jaki dowod moglby ja
  oslabic, i uwzglednij go w ledgerze, jezeli jest widoczny.
- `Owning-layer proof`: hipoteza moze byc `confirmed` tylko wtedy, gdy proof
  pochodzi z warstwy wlascicielskiej; szczegoly proof dostarcza odpowiedni
  skill specjalistyczny.
- `Information gain`: przed kolejnym krokiem nazwij decyzje, ktora zmieni jego
  wynik; jezeli krok tylko "dowie sie wiecej", nie uruchamiaj go.
- `Result readiness`: nie przekazuj materialu do skilli wyniku, dopoki
  wymagany artifact diagnostyczny ma status `needs_deeper_evidence`.
- `Feedback loop`: jezeli skill wyniku zwroci
  `IncidentResultReadinessFeedback`, potraktuj go jako powrot do orkiestracji,
  nie jako finalny wynik.
- `Fixability test`: zatrzymaj sie dopiero, gdy skille wyniku dostana material,
  z ktorego da sie wskazac odbiorce, target, pierwsza akcje i sposob
  weryfikacji albo jawny limitation.

## Granice Odpowiedzialnosci

Szczegoly etapow naleza do dedykowanych skilli:

- `incident-code-grounding`: code flow, predicates, mapping, validation,
  integration clients i technical code evidence,
- `incident-operational-grounding`: system, process, bounded context, owner,
  code scope i handoff route,
- `incident-data-diagnostics`: DB/data/process-state checks,
- `incident-functional-analysis`: format i jezyk `functionalAnalysis`,
- `incident-technical-handoff`: format i tresc `technicalAnalysis`.

Orkiestrator moze wymagac proof z warstwy wlascicielskiej, ale nie opisuje
konkretnych DB checks, GitLab reads, opctx browse, sekcji Markdown ani report
tools.

## Algorytm Orkiestracji

1. Przeczytaj manifest i ustal dostepne capability groups.
2. Zbuduj krotki fingerprint incydentu: symptom, trigger, runtime/service,
   czas, widoczny endpoint/message/operation i pierwsza luka widocznosci.
3. Zbuduj albo zaktualizuj `IncidentDiagnosisLedger`.
4. Ustal, ktora decyzja blokuje wynik dla `incident-functional-analysis` albo
   `incident-technical-handoff`.
5. Wybierz najmniejszy specjalistyczny krok, ktory moze zmienic te decyzje.
6. Po wyniku specjalistycznego skilla zaktualizuj ledger: potwierdzone fakty,
   inferencje, hipotezy odrzucone i limitations.
7. Wykonaj readiness gate dla `functionalAnalysis` i `technicalAnalysis`.
8. Jezeli readiness gate zwraca `needs_deeper_evidence`, uruchom kolejny waski
   krok diagnostyczny wskazany przez brakujacy artifact.
9. Powtarzaj tylko, gdy kolejny krok ma realny information gain dla wyniku.
10. Gdy ledger jest wystarczajacy albo dalsza widocznosc jest niedostepna,
   przekaz material do skilli wyniku.

## Readiness Gate I Petla Zwrotna

Przed `incident-functional-analysis` i `incident-technical-handoff` ustaw status
kazdego potrzebnego artifactu:

- `ready`: artifact jest wystarczajacy dla wyniku,
- `needs_deeper_evidence`: brakuje zakresu albo szczegolu, a istnieje waski
  krok diagnostyczny, ktory moze to rozstrzygnac,
- `visibility_limited`: brak jest realny, ale dalsze proof wymaga
  niedostepnego toola, innego systemu albo odbiorcy zewnetrznego,
- `not_applicable`: artifact nie jest potrzebny dla tego incydentu.

Nie przekazuj materialu do skilli wyniku, gdy wymagany artifact ma status
`needs_deeper_evidence`. Uruchom wtedy tylko jeden najwezszy kolejny krok:
`incident-code-grounding`, `incident-operational-grounding`,
`incident-data-diagnostics` albo jawny runtime/log/downstream check, jezeli
capability jest dostepna.

Jezeli `incident-functional-analysis` albo `incident-technical-handoff` zwroci
`IncidentResultReadinessFeedback`, wroc do orkiestracji:

```text
IncidentResultReadinessFeedback
missingArtifact: CodeGroundingSummary | OperationalGroundingSummary | DataDiagnosticSummary | IncidentDiagnosisLedger | logOrRuntimeEvidenceSummary
neededFor: functionalAnalysis | technicalAnalysis | handoff | confidence | visibilityLimits
suggestedSkill: incident-code-grounding | incident-operational-grounding | incident-data-diagnostics | runtime/log/downstream visibility
minimumNextQuestion: <jedno waskie pytanie, ktore zmieni wynik>
reason: <dlaczego bez tego wynik bylby zgadywaniem albo zbyt plytki>
```

Po feedbacku wykonaj tylko krok odpowiadajacy `minimumNextQuestion`, zaktualizuj
ledger i ponow readiness gate. Jezeli krok nie jest dostepny albo nie daje
proof, oznacz brak jako `visibility_limited` i dopiero wtedy przekaz limitation
do skilli wyniku.

## Kiedy Uzyc Skilli

- `incident-code-grounding`: gdy brak dotyczy zachowania kodu albo lokalizacji
  technicznej.
- `incident-operational-grounding`: gdy brak dotyczy systemu, procesu, ownera,
  scope'u albo handoffu.
- `incident-data-diagnostics`: gdy hipoteza wymaga DB/data proof.
- `incident-functional-analysis`: zawsze do przygotowania
  `functionalAnalysis`.
- `incident-technical-handoff`: zawsze do przygotowania `technicalAnalysis`.

Elasticsearch, runtime/Dynatrace albo inne tools traktuj jako capability
diagnostyczne, ale ich szczegolowe procedury nie mieszkaja w orkiestratorze.

## Kontrakt Orkiestracji

Orkiestrator przekazuje do skilli wyniku:

```text
IncidentDiagnosisLedger
CodeGroundingSummary?
OperationalGroundingSummary?
DataDiagnosticSummary?
logOrRuntimeEvidenceSummary?
sourceRefs
visibilityLimits
confidenceCandidates
```

To nie jest finalny publiczny kontrakt odpowiedzi. Finalne pola i strukture
sekcji definiuja `incident-functional-analysis` oraz
`incident-technical-handoff`.

## Walidacja

Przed przekazaniem do skilli wyniku sprawdz:

- ledger rozdziela fakty, hipotezy, odrzucone wyjasnienia i limitations,
- kazde mocne twierdzenie ma source ref albo jest oznaczone jako hipoteza,
- specjalistyczne summary artifacts sa dostepne albo brak jest jawny,
- zaden wymagany artifact nie ma statusu `needs_deeper_evidence`,
- proponowana akcja jest wystarczajaco konkretna dla result skilli,
- orkiestrator nie zawiera instrukcji finalnego formatu ani report tools.

## Fallbacki

Jezeli specjalistyczny skill albo tool nie jest dostepny, nie udawaj proof.
Zachowaj czesciowy ledger, obniz confidence i przekaz limitation do skilli
wyniku. Fallback finalnej odpowiedzi nalezy do skilli wyniku i platformowego
report runtime, nie do orkiestratora.

## Artefakty Handoffu

Pozostaw:

- `IncidentDiagnosisLedger`,
- dostepne summary artifacts,
- source refs,
- visibility limits,
- pytania otwarte,
- sugerowany nastepny odbiorca albo `Nie ustalono`.

## Warunki Zatrzymania

Zatrzymaj diagnostyke, gdy:

- flow use case'u jest wystarczajaco jasne dla skilli wyniku,
- punkt rozjazdu jest ustalony albo ma jawny limitation,
- jedna hipoteza jest confirmed/strong albo dalsze proof wymaga innej
  widocznosci,
- proponowana akcja albo handoff jest konkretny,
- kolejne tool calls bylyby browsingiem albo powtorzeniem evidence.

## Antywzorce

Nie:

- klasyfikuj root cause z samej nazwy exceptiona,
- opisuj procedur DB/GitLab/opctx w orkiestratorze,
- definiuj formatu `functionalAnalysis` albo `technicalAnalysis` w
  orkiestratorze,
- opisuj report tools ani fallback JSON w orkiestratorze,
- diagnozuj data issue bez DB/data proof albo jawnego limitation,
- wymuszaj code root cause, gdy evidence wskazuje downstream albo outside
  visibility,
- omijaj skille wyniku przy finalnej odpowiedzi.
