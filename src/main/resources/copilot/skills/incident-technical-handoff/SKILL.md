---
name: incident-technical-handoff
description: Kontrakt Technical Handoff v1 dla pola technicalAnalysis oraz follow-upow, ktore maja dac developerowi, QA, DevOps, DBA, wlascicielowi danych, partnerowi albo innemu zespolowi gotowy material do dzialania.
---

# Skill Technicznego Handoffu Incydentu

Uzywaj tego skilla dla pola `technicalAnalysis` w kazdym poczatkowym wyniku
analizy incydentu.

Uzywaj go rowniez, gdy uzytkownik prosi o handoff, developer handoff, bug
report, ticket text, przekazanie, zgloszenie, raport dla developera, raport
dla QA, raport dla DevOps, przekazanie do innego zespolu albo dowolna
odpowiedz, ktora ma byc gotowa do uzycia przez osobe techniczna.

Frontend renderuje odpowiedzi chatu jako Markdown, wiec odpowiedz ma byc
bezposrednim, dobrze ustrukturyzowanym Markdownem. Nie opakowuj handoffu w JSON,
chyba ze uzytkownik jawnie o to prosi.

## Rola Wobec Orkiestratora

Ten skill jest kontraktem wyniku dla `technicalAnalysis` oraz technicznych
follow-upow. Orkiestrator uzywa go po researchu flow, klasyfikacji bledu i
zebraniu evidence potrzebnego do naprawy, weryfikacji albo przekazania poza
analizowany system.

Nie zaczynaj diagnostyki od nowa. W initial analysis ten skill syntetyzuje
ustalenia w gotowy handoff i nie uruchamia samodzielnie pelnej eksploracji.
W follow-upie moze dociagnac tylko brakujacy, konkretny szczegol, jezeli bez
niego odbiorca nie moze zaczac dzialania.

## Wejscie Oczekiwane Od Orkiestratora

Przyjmij:

- fingerprint incydentu i detected problem,
- result-sufficient use-case flow z failure point,
- klasyfikacje bledu i causal chain,
- rozdzielenie faktow, hipotez i visibility limits,
- material specjalistyczny: GitLab code path, DB finding, runtime evidence,
  downstream boundary, operational context albo resolved handoff,
- oczekiwany profil odbiorcy, gdy wynika z pytania albo ownershipu.

## Czego Ten Skill Nie Diagnozuje

Nie diagnozuj tutaj od zera:

- calego systemu ani nowego use case'u,
- klas bledu, jesli orkiestrator ich jeszcze nie ustalil,
- DB issue bez DB evidence,
- code issue bez code evidence,
- ownershipu i handoffu bez operational context albo incident evidence.

Jezeli handoff wymaga brakujacej diagnostyki, wypisz limitation i skieruj do
odpowiedniego skilla specjalistycznego zamiast ukrywac luke.

## Wklad Do Wyniku

Ten skill wypelnia `technicalAnalysis`: techniczny opis problemu, lokalizacje,
root cause albo najlepsza hipoteze, dowody, proponowana akcje, testy,
verification path, ryzyka, ograniczenia i Definition of Done.

## Cel

Wygeneruj powtarzalny dokument `Technical Handoff v1`, ktory odbiorca
techniczny moze wykorzystac bez ponownego czytania calej analizy.

Handoff ma pomoc odbiorcy odpowiedziec:

- co sie stalo,
- gdzie to sie stalo,
- co jest potwierdzone,
- co jest hipoteza,
- jaki obszar kodu, danych, integracji, runtime albo ownershipu jest dotkniety,
- jaka konkretna akcja jest oczekiwana,
- jak sprawdzic, ze problem zostal naprawiony albo dobrze przekazany.

## Kontrakt Wyniku

Wynikiem jest Markdown `Technical Handoff v1` zapisany w sekcji
`TECHNICAL_HANDOFF` raportu i mapowany na publiczne pole `technicalAnalysis`.
Korzystaj z:

- `IncidentDiagnosisLedger`,
- `CodeGroundingSummary`,
- `DataDiagnosticSummary`,
- `OperationalGroundingSummary`,
- runtime/log/downstream evidence,
- visibility limits i source refs przekazanych przez orkiestratora.

## Readiness Gate

Przed wypelnieniem `technicalAnalysis` sprawdz, czy material wystarcza do
konkretnego handoffu: odbiorca, pierwsza akcja, target techniczny, evidence,
verification path i limitations.

Jezeli w initial analysis brakuje rozstrzygalnego artifactu, nie tworz
pozornie kompletnego handoffu. Zwroc do orkiestratora:

```text
IncidentResultReadinessFeedback
missingArtifact: CodeGroundingSummary | DataDiagnosticSummary | OperationalGroundingSummary | IncidentDiagnosisLedger | logOrRuntimeEvidenceSummary
neededFor: technicalAnalysis | handoff | confidence | visibilityLimits
suggestedSkill: incident-code-grounding | incident-data-diagnostics | incident-operational-grounding | runtime/log/downstream visibility
minimumNextQuestion: <jedno waskie pytanie potrzebne do technicznego handoffu>
reason: <dlaczego bez tego odbiorca nie moze zaczac dzialania>
```

Jezeli brak nie jest rozstrzygalny w aktualnej sesji, wypelnij Technical
Handoff v1 z jawnym `Brak danych w evidence`, `Nie ustalono` albo
`Hipoteza, wymaga potwierdzenia`. W follow-upie wolno uzyc tools tylko dla
jednego konkretnego brakujacego szczegolu; jesli wymaga to nowej diagnozy,
wroc do limitation albo popros orkiestrator o odpowiedni skill.

## Minimalny Poziom Jakosci

Kazdy handoff musi byc:

- ugruntowany w evidence,
- wystarczajaco konkretny, zeby zaczac implementacje albo weryfikacje,
- czytelny dla juniora lub mida technicznego,
- stabilny w kolejnosci i nazwach sekcji,
- jawny co do brakujacej widocznosci,
- jasny co do odbiorcy i pierwszej akcji,
- wolny od niepotwierdzonych twierdzen o ownershipie lub root cause.

Jezeli wartosc jest nieznana, nie usuwaj sekcji. Uzyj jednej z wartosci:

- `Nie ustalono`
- `Nie dotyczy`
- `Brak danych w evidence`
- `Hipoteza, wymaga potwierdzenia`

## Profil Odbiorcy

Wywnioskuj odbiorce z najnowszej wiadomosci uzytkownika.

Uzyj `Developer`, gdy uzytkownik pisze:

- developer,
- dev,
- programista,
- poprawka,
- fix,
- bug,
- code change,
- PR.

Uzyj `QA / Tester`, gdy uzytkownik pisze:

- QA,
- tester,
- testy,
- scenariusz testowy,
- reprodukcja,
- regresja.

Uzyj `DevOps / Platform`, gdy uzytkownik pisze:

- DevOps,
- platform,
- deployment,
- pod,
- container,
- namespace,
- image,
- metrics,
- infrastructure,
- runtime.

Uzyj `Data / DBA`, gdy uzytkownik pisze:

- DBA,
- dane,
- DB,
- baza,
- tabela,
- rekord,
- migracja danych,
- referencja,
- slownik.

Uzyj `Partner / Other Team`, gdy request dotyczy innego systemu, downstream,
upstream, external party albo ownership handoff.

Jezeli odbiorca nie jest jasny, uzyj `Technical receiver` i napisz, ze docelowy
zespol nie jest w pelni ustalony.

## Zasady Evidence

W initial analysis uzywaj materialu przekazanego przez orkiestrator:
artefaktow incydentu, summary artifacts, source refs i visibility limits. Nie
uruchamiaj wtedy samodzielnie tools; rozstrzygalny brak zwroc jako
`IncidentResultReadinessFeedback`.

W follow-upie uzywaj zakonczonych artefaktow incydentu, finalnego wyniku
analizy, poprzedniego tool evidence, historii chatu i nowych tool results z
aktualnego requestu.

W follow-upie mozesz uzyc tools tylko wtedy, gdy bez tego handoffowi brakuje
konkretnego wymaganego szczegolu i capability jest dostepne w sesji.

Uzywaj:

- Operational Context tools dla resolved ownership, handoff rules, procesu,
  bounded contextu, systemu, repository scope i receiving owner details,
- GitLab tools dla pliku, klasy, metody, repozytorium, predykatu, call flow
  albo szczegolow code change,
- Database tools dla potwierdzonego stanu danych, brakujacych rekordow,
  zlych predykatow albo DB verification targets,
- Elasticsearch tools dla dodatkowego log timing, stacktrace, frequency albo
  request evidence.

Nie uzywaj katalogu jako dowodu root cause. Katalog moze wspierac routing,
ownership, code-search scope, vocabulary procesu i gotowosc handoffu.

## Fakty A Hipotezy

Uzywaj bezposredniego jezyka tylko dla twierdzen wspartych evidence:

- `Potwierdzone: ...`
- `Evidence wskazuje, ze ...`
- `Logi pokazuja ...`
- `Kod w ... pokazuje ...`

Uzywaj jezyka hipotezy, gdy evidence jest niepelne:

- `Hipoteza: ...`
- `Najbardziej prawdopodobne wyjasnienie: ...`
- `Wymaga potwierdzenia w ...`
- `Nie potwierdzono bezposrednio ...`

Nigdy nie przedstawiaj inferred ownership, inferred DB state,
inferred external-system failure ani inferred code behavior jako potwierdzonego
faktu.

## Format Wyniku

Uzyj dokladnie tej struktury top-level, w tej kolejnosci.

````markdown
# Technical Handoff v1: <krotki tytul techniczny>

## 1. Odbiorca i cel przekazania

| Pole | Wartosc |
|---|---|
| Profil odbiorcy | <Developer / QA / DevOps / Data / Partner / Technical receiver> |
| Sugerowany odbiorca | <primary owner / partner owner / system owner albo Nie ustalono> |
| Cel przekazania | <jedno zdanie: implement fix / verify data / validate runtime / handoff to partner> |
| Pierwsza oczekiwana akcja | <jedna konkretna akcja> |

## 2. Streszczenie

<3-6 krotkich zdan po polsku. Uwzglednij objaw, wplyw, prawdopodobna przyczyne
albo hipoteze oraz nastepna akcje.>

## 3. Priorytet / Severity

**Severity:** <niski / sredni / wysoki / krytyczny>

**Uzasadnienie:** <impact, determinism, affected users/flow, workaround,
visibility, urgency>

## 4. Srodowisko i zakres wykrycia

| Parametr | Wartosc |
|---|---|
| Srodowisko | <environment albo Nie ustalono> |
| Branch | <gitLabBranch albo Nie ustalono> |
| GitLab group | <gitLabGroup albo Nie ustalono> |
| Namespace / runtime | <namespace/runtime albo Nie ustalono> |
| Pod / instancja | <pod/instance albo Nie ustalono> |
| Kontener / service | <container/service albo Nie ustalono> |
| Commit / image | <commit/image albo Nie ustalono> |
| correlationId / requestId | <id albo Nie ustalono> |
| Czas zdarzenia | <timestamp/window albo Nie ustalono> |

## 5. Objawy i wplyw

- **Objaw:** <co widzi operator>
- **Wplyw:** <zablokowany endpoint/job/process/capability>
- **Powtarzalnosc:** <potwierdzona/reprodukowalna/nieznana>
- **Zakres:** <single request/customer/environment/all known cases/unknown>
- **Obejscie:** <znany workaround albo Brak danych w evidence>

## 6. Dokladna lokalizacja techniczna

| Element | Lokalizacja |
|---|---|
| Repozytorium | <repo albo Nie ustalono> |
| Modul / aplikacja | <module/app albo Nie ustalono> |
| Plik | <file path albo Nie ustalono> |
| Klasa / metoda | <class/method albo Nie ustalono> |
| Linie | <lines albo Nie ustalono> |
| Endpoint / event / job | <entry point albo Nie ustalono> |
| Powiazane wywolania | <important callers/callees albo Nie ustalono> |

## 7. Przyczyna zrodlowa albo najlepsza hipoteza

**Status:** <Potwierdzone / Hipoteza / Nie ustalono>

<Wyjasnij root cause albo najlepsza hipoteze. Napisz, dlaczego dzieje sie to
tutaj i jaki warunek to uruchamia. Jezeli root cause nie jest w pelni
potwierdzony, napisz czego brakuje.>

## 8. Dowody

| Dowod | Zrodlo / ID | Co potwierdza |
|---|---|---|
| <artifact/tool/log/code/db/opctx> | <artifactId/itemId/tool result> | <claim supported by this evidence> |

## 9. Przeplyw wykonania

```text
<entry point>
  -> <controller/listener/job/client>
    -> <service/facade>
      -> <repository/integration/runtime component>
        -> <failure point>
```

Jezeli flow nie jest ustalone, wpisz krotkie ograniczenie zamiast wymyslac
brakujace kroki.

## 10. Proponowana poprawka albo oczekiwane dzialanie

**Rekomendacja:** <preferowana akcja>

**Dlaczego ten wariant:** <krotkie evidence-based uzasadnienie>

**Zakres zmiany / dzialania:**
- <file/data/runtime/config/integration action>

**Alternatywy:**
- <opcjonalna bezpieczniejsza/defensywna/refactor alternatywa albo Nie dotyczy>

## 11. Testy i weryfikacja

| Typ weryfikacji | Co sprawdzic |
|---|---|
| Unit test | <konkretny unit test albo Nie dotyczy> |
| Integration/API test | <konkretny integration test albo Nie dotyczy> |
| DB/data check | <konkretny data check albo Nie dotyczy> |
| Runtime/log verification | <konkretny log/metric/pod check albo Nie dotyczy> |
| Manual regression | <scenariusz albo Nie dotyczy> |

## 12. Ryzyka i skutki uboczne

- <risk 1>
- <risk 2>
- <Nie ustalono, jezeli nie widac ryzyka>

## 13. Ograniczenia diagnostyki

- <missing DB/runtime/GitLab/log/downstream visibility>
- <unverified assumption>
- <scope limitation>

## 14. Definition of Done

- <fix/action implemented or routed>
- <tests/checks passed>
- <incident scenario verified>
- <handoff receiver confirms ownership or redirects with reason>

## 15. Referencje

| Artefakt | ID / lokalizacja | Rola |
|---|---|---|
| <artifact/tool/code/log/db/opctx> | <id/path> | <jak zostal uzyty> |
````

## Quality Gate

Przed oddaniem handoffu sprawdz:

- tytul wskazuje objaw i prawdopodobny obszar, nie tylko "blad",
- streszczenie jest zrozumiale bez stacktrace,
- severity wynika z impactu i powtarzalnosci, nie z tonu exceptiona,
- lokalizacja techniczna pozwala odbiorcy zaczac od konkretnego file/method,
  table/key, endpoint, pod/config albo downstream boundary,
- root cause opisuje mechanizm, nie tylko exception,
- kazde mocne twierdzenie ma evidence albo jest oznaczone jako hipoteza,
- rekomendacja mowi kto/co/gdzie/jak zweryfikowac,
- testy sa konkretne dla warstwy: unit/API/DB/runtime/manual,
- ograniczenia mowia, czego nie zweryfikowano i jaki dowod jest potrzebny,
- Definition of Done pozwala potwierdzic fix albo prawidlowy handoff.

Dopasuj akcje do odbiorcy:

- `Developer`: file/method, oczekiwana zmiana, edge cases, testy.
- `QA / Tester`: reproduction path, input, expected result, regresja.
- `DevOps / Platform`: namespace/pod/service/image/config/metric/log check.
- `Data / DBA`: application scope, table/column/key/predicate, data correction.
- `Partner / Other Team`: system/integration, evidence package, konkretne
  pytanie albo oczekiwana odpowiedz.

Jezeli uzytkownik prosi o krotka wersje, zachowaj ta sama kolejnosc logiczna,
ale skondensuj tresc zamiast zmieniac kontrakt.

## Walidacja

Stosuj `Quality Gate` jako obowiazkowa walidacje handoffu. Dodatkowo sprawdz:

- odbiorca i pierwsza akcja sa konkretne albo oznaczone jako `Nie ustalono`,
- root cause jest faktem tylko przy wystarczajacym evidence,
- DB, downstream, runtime i ownership claims nie sa zgadywane,
- odbiorca wynika z `resolvedOwnership` systemu/bounded contextu albo jest
  jawnie oznaczony jako inferowany,
- testy i Definition of Done daja odbiorcy jasny sposob zamkniecia sprawy.

## Fallbacki

Jezeli brakuje evidence:

- zachowaj strukture Technical Handoff v1,
- wpisz `Brak danych w evidence`, `Hipoteza, wymaga potwierdzenia` albo
  `Nie ustalono`,
- nie usuwaj sekcji,
- skieruj dalsza akcje do warstwy, ktora moze dostarczyc brakujacy dowod.

## Artefakty Handoffu

Przekaz do raportu:

- Markdown `Technical Handoff v1`,
- source refs dla evidence,
- lista konkretnych plikow, metod, tabel, endpointow, runtime targets albo
  downstream boundaries, jezeli sa ugruntowane,
- Definition of Done dla odbiorcy technicznego.

## Antywzorce

Nie:

- zwracaj nieustrukturyzowanej narracji, gdy prosba dotyczy handoffu,
- pomijaj sekcji, bo brakuje danych,
- wymyslaj teamu albo ownera,
- twierdz o stanie DB bez DB evidence,
- twierdz o awarii external system bez downstream/runtime evidence,
- wklejaj dlugich stacktrace albo duzych blokow kodu, chyba ze uzytkownik prosi,
- zwracaj JSON dla normalnego handoffu w chacie,
- mieszaj faktow i hipotez w niejednoznacznym zdaniu,
- pisz handoffu zrozumialego tylko dla senior developera znajacego system.
