# Strona informacyjna AI Models

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator potrzebuje porownania kosztow modeli dostepnych na jego koncie.
Powtarzane opisy modeli i niewypelniony mnoznik premium nie pomagaja podjac
decyzji. Strona powinna przypominac cennik GitHub Copilot i korzystac z
aktualnych danych SDK, bez recznego katalogu cen lub rekomendacji.

## Baseline i conformance delta

- `GET /api/analysis/ai/options` zwraca dynamiczna liste `models.list` i jest
  konsumowany przez selektory modeli na ekranach analitycznych.
- SDK 1.0.11 udostepnia `billing.tokenPrices` z cenami w AI credits za paczke
  tokenow oraz opcjonalne stawki `longContext`; dotychczasowe API ich nie
  projektowalo.
- Strona `Platform / AI Models` istnieje, ale pokazuje recznie utrzymywane
  opisy i w praktyce puste mnozniki premium.
- Poziom L2: rozszerzenie wspolnego kontraktu API i modelu FE. Istniejace
  pola, selektory modeli, runtime sesji i rozliczanie runow nie zmieniaja
  semantyki.

## Proponowane rozwiazanie

Projektowac stawki tokenowe z `models.list` do opcjonalnego DTO. Backend
normalizuje cene z AI credits za paczke do AI credits za milion tokenow.
Widok pokazuje tabele: model, wariant, wejscie, odczyt i zapis cache oraz
odpowiedz. Wariant dlugiego kontekstu pojawia sie tylko, gdy jego ceny w SDK
sa inne od stawek podstawowych.
Brak stawek jest jawny i prowadzi do oficjalnego cennika. Strona nie
przechowuje opisow ani cen poszczegolnych modeli.

## Zakres

- Dynamiczna projekcja stawek w shared/operator API.
- Tabela cen w istniejącej trasie `Platform / AI Models`.
- Usuniecie recznie utrzymywanych opisow i poprzedniej prezentacji mnoznika.
- Testy SDK mapowania, API, UI, nawigacji i adekwatny build.
- Aktualizacja kanonicznej dokumentacji architektury.

## Non-goals

- Recznie utrzymywany cennik lub opisy zastosowan model po modelu.
- Odtwarzanie grupowania providerow z identyfikatorow; SDK nie zwraca providera.
- Zmiana wyboru modelu w istniejacych feature'ach.

## Ograniczenia i ryzyka

- Nie kazdy model ma stawki; `auto` i inne pozycje moga pozostac bez ceny.
- Stawki w katalogu sa orientacyjne dla modelu i liczby tokenow. Koszt runu
  nadal zalezy od planu, cache i faktycznego zuzycia.
- Przeliczenie wymaga dodatniego `batchSize`; niepoprawne lub brakujace dane
  cenowe nie sa zgadywane.
- Niezapisane zmiany auth/PAT w tym samym worktree musza zostac zachowane.

## Kryteria akceptacji

- Zakladka pokazuje realne stawki z `models.list`, jezeli SDK je zwraca.
- Stawki wejscia i odpowiedzi sa widoczne przed drugoplanowymi metadanymi.
- Ceny i opisy modeli nie sa hardkodowane.
- Brak cen, pusta lista, blad pobrania i dlugi kontekst maja czytelne stany.
- Wspolne selektory modeli pozostaja zgodne z rozszerzonym kontraktem.

## Kroki

- [x] Krok 1: Sprawdzic zrodlo stawek w SDK i oficjalna semantyke cen;
  dowod: `ModelBillingTokenPrices` w SDK 1.0.11 oraz dokumentacja GitHub
  Copilot SDK `usage-and-billing`.
- [x] Krok 2: Przeniesc ceny przez platformowy katalog i shared API oraz
  zastapic karty tabela bez recznych opisow; dowod: DTO, widok i testy
  celowane.
- [x] Krok 3: Zweryfikowac pelna macierz backend/frontend, sprawdzic
  wynikowy bundle i dokumentacje; dowod: 634 testy Angulara, produkcyjny
  build, `mvn -q -Pbackend-dev clean package`, live `models.list` przez API
  (16 modeli, 15 z cenami), celowany test po korekcie UI, odswiezony JAR i
  `git diff --check`.
