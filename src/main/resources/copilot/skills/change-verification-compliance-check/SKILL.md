---
name: change-verification-compliance-check
description: Ocenia kazda regule Jira, Confluence i instrukcji repozytorium w jednym rejestrze.
---

# Change Verification Compliance Check

## Zrodla i zakres

Target issue jest glownym zakresem. Acceptance criteria, jawne wymagania w
opisie i komentarzach oraz fragmenty Confluence wyraznie wlaczone przez target
issue tworza reguly `STORY`. Aktywne instrukcje repozytorium tworzace
sprawdzalne zobowiazania dla zmienionego kodu tworza reguly `INSTRUCTION`.

Parent, subtaski i szerszy Confluence sa kontekstem interpretacyjnym. Nie
zamieniaj ich automatycznie w reguly target issue. Direct subtasks moga wskazac
implementacje, ale ich wymagania staja sie regula targetu tylko przy jawnym
powiazaniu. Operator `userInstructions` jest regula tylko wtedy, gdy zawiera
konkretne wymaganie do sprawdzenia; wtedy uzyj `OPERATOR_INSTRUCTION`.

## Budowa ledgeru

1. Przejdz zrodla w stabilnej kolejnosci: acceptance criteria, opis targetu,
   komentarze, jawnie wlaczony Confluence, operator instructions, instrukcje
   repozytorium.
2. Deduplikuj po znaczeniu i pochodzeniu. Gdy ten sam tekst jest powtorzony,
   pozostaw najsilniejsza referencje i wymien pozostale w evidence lub
   conclusion; nie tworz drugiej reguly.
3. W `source.quote` zachowaj krotki doslowny fragment autora. W
   `normalizedRule` zapisz sprawdzalna interpretacje. Konflikt zrodel oznacz
   `interpretationType=CONFLICTING`, bez cichego wyboru jednej wersji.
4. Dla kazdej reguly odczytaj tylko dowody potrzebne do jej oceny. Repository
   Scope podaje kanoniczne `projectName` i `analysisRef` dla GitLab tools.
5. Ustaw outcome:
   - `SATISFIED` tylko przy konkretnym pozytywnym evidence,
   - `NOT_SATISFIED` przy konkretnym dowodzie rozjazdu,
   - `NOT_VERIFIED` przy zidentyfikowanej regule, ktorej material nie pozwala
     rozstrzygnac.
6. Ustaw `releaseImpact` osobno: `NONE`, `REVIEW` albo `BLOCKER`. Status
   `WARNING` nie istnieje. Dla spelnionej reguly zawsze `NONE`.
7. W conclusion podaj jeden zwiezly wniosek. Evidence zawiera fakt i konkretna
   referencje. MissingEvidence nazywa brak. Action opisuje najmniejszy krok
   potrzebny do usuniecia niezgodnosci albo uzyskania dowodu.

## Dodatkowe kontrole AI

Po ocenie wszystkich regul zrodlowych mozesz utworzyc maksymalnie piec
`additionalChecks`. Kazdy musi wynikac z konkretnych sygnalow zmiany i dotyczyc
poprawnosci, bezpieczenstwa, integralnosci danych, kompatybilnosci kontraktu
albo gotowosci release. Ustaw `scope=ADDITIONAL`, source
`AI_SUGGESTION`, `interpretationType=INFERRED`, rationale, riskIfOmitted,
signals i confidence. Pomijaj ogolne best practices i nie wypelniaj limitu na
sile. Te wpisy nie wplywaja na werdykt regul autora.

## Widocznosc

Visibility limit dodaj tylko wtedy, gdy konkretny brak widocznosci wplywa na
ocene istniejacej reguly lub dodatkowego checka. W `affectedRuleIds` wskaz jego
id. Surowe komunikaty discovery, truncation i bledy transportu pozostaja w
diagnostyce runu; nie kopiuj ich jako osobnych problemow, regul ani dzialan.

## Wyjscie

Finalny ksztalt i wymagane tablice sa zdefiniowane w
`change-verification/response-contract.md`. Zwroc tylko ten JSON. Nie wywoluj
report tools i nie tworz alternatywnej reprezentacji wyniku.
