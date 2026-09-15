# Change Verification - jeden rejestr regul

Status: approved

Source need: [Change Verification - wynik prowadzony przez reguly zrodlowe](../needs/change-verification-rule-first-result.md)

Klasyfikacja: L1. Zmiana obejmuje feature-owned kontrakt wyniku, prompt,
runtime skille, report, import/export oraz workspace Angulara. Nie zmienia
shared modeli ani granic platformy AI. Uzytkownik 2026-09-15 zatwierdzil
breaking change bez kompatybilnosci wstecznej.

## Baseline

- Publiczny wynik `change-verification-result-v4` zawiera `compliance` z
  `verificationChecks`, `findings`, `suggestedActions`, statusem i confidence.
- AI tworzy osobno wynik JSON oraz trzy sekcje Markdown przez report tools;
  backend laczy oba materialy w report, przez co istnieja konkurencyjne zrodla
  semantyki.
- UI ponownie dzieli checks na priorytet review, potwierdzony zakres i pelny
  material, a pozniej pokazuje dodatkowe appendixy.
- Source discovery automatycznie rozszerza MR-y o rodzica i jego rodzenstwo,
  zas prompt powtarza listy plikow i szeroki kontrakt instrukcji.
- Zalaczony rezultat mial okolo 920 tys. znakow promptu, 15 checks, 4 findings,
  4 actions i 31 visibility limits; globalny status nie byl spojny z
  sekcyjnymi raportami Markdown.
- Baseline testow przed zmiana:
  `mvn -q "-Dtest=*ChangeVerification*" test` - PASS;
  `npm --prefix frontend test -- --include "src/app/features/change-verification/**/*.spec.ts"` - PASS (10 testow).

## Conformance delta

- Jedynym zrodlem prawdy wyniku jest `ruleLedger`; report jest deterministyczna
  projekcja tego samego modelu.
- Regula zrodlowa zachowuje cytat i referencje, a normalizacja AI jest polem
  pomocniczym. Jedna regula wystepuje w liscie tylko raz.
- Outcome ma zamkniety zbior `SATISFIED`, `NOT_SATISFIED`, `NOT_VERIFIED`.
  Wplyw na release jest osobnym polem `NONE`, `REVIEW`, `BLOCKER`.
- Backend wylicza decyzje: failure -> `NEEDS_ACTION`, brak failure i co
  najmniej jeden brak dowodu -> `NEEDS_EVIDENCE`, komplet -> `READY`, brak
  regul zrodlowych -> `INCONCLUSIVE`.
- `additionalChecks` AI sa osobna lista i nie wplywaja na decyzje dla regul
  autora.
- Model nie generuje globalnego statusu, findings, globalnych actions ani
  niezaleznych sekcji Markdown.
- Visibility limit w wyniku jest przypiety do konkretnych regul; techniczne
  ograniczenia discovery pozostaja w diagnostyce run/evidence.
- Import akceptuje tylko nowy envelope v6 i kontrakt
  `change-verification-result-v5`; starsze pliki sa odrzucane jawnie.
- UI pokazuje zwarty werdykt i jedna filtrowalna liste regul. Szczegoly sa
  rozwijane w miejscu, dodatkowe checks AI sa domyslnie zwiniete, a formularz
  runu po zakonczeniu przechodzi w kompaktowy kontekst.

## Audit konsumentow

- Backend: AI response/parser, Copilot provider i assembler, job state/service,
  publiczne DTO job API, report mapper/factory, snapshot historii oraz
  import/export.
- Runtime: Change Verification prompt oraz wszystkie skille
  `change-verification-*`; report tools zostaja usuniete z allowlisty tej
  sesji.
- Frontend: modele, job page, prezentacja wyniku, copy result, historia lokalna
  i walidacja import/export.
- Testy: parser/model/decision, prompt/source discovery, Copilot runtime,
  job/report/export oraz komponenty i page Angulara.

## Zakres i non-goals

Zakres obejmuje pelne zastapienie dotychczasowego modelu wyniku. Nie obejmuje
zmiany wspolnych kontraktow reportu, platformy Copilot ani integracji Jira,
Confluence i GitLab. Nie dodajemy migratora starych eksportow ani aliasow pol.

## Kroki

- [x] 1. Zastapic compliance modelem jednego rule ledger, scislym parserem i
  deterministyczna decyzja backendu. Dowod: testy kontraktu, walidacji i
  wszystkich czterech decyzji.
- [x] 2. Uproscic source discovery, prompt i runtime skille do jednego
  przebiegu oceny; usunac model-generated report i powtorzenia artefaktow.
  Dowod: testy scope MR, deduplikacji instrukcji, promptu i allowlisty tools.
- [x] 3. Przebudowac job, report i export na projekcje ledgeru oraz podniesc
  envelope do v6 / result contract v5 bez legacy parsing. Dowod: testy joba,
  reportu i odrzucenia starego importu.
- [x] 4. Zastapic trzy konkurencyjne widoki jedna filtrowalna lista regul,
  oddzielic dodatkowe checks AI i zwinac composer po zakonczeniu. Dowod: testy
  komponentu, page i import/export.
- [x] 5. Zaktualizowac dokumentacje architektoniczna i zamknac macierz
  weryfikacji. Dowod: testy Angulara, build Angulara oraz
  `mvn -q -Pbackend-dev clean package`.

## Weryfikacja po implementacji

- `mvn -q "-Dtest=*ChangeVerification*" test` - PASS.
- `npm --prefix frontend test -- --include "src/app/features/change-verification/**/*.spec.ts"` - PASS (7 testow).
- `npm --prefix frontend test -- --watch=false` - PASS (586 testow w 67 plikach).
- `npm --prefix frontend run build` - PASS; produkcyjny bundle zapisany w
  `src/main/resources/static`.
- `mvn -q -Pbackend-dev clean package` - PASS po usunieciu wycofanych pustych
  katalogow runtime skilli.
