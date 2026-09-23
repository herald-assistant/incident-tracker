# Minimalne prompty follow-up w istniejacej sesji

Status: done

Source need: brak osobnego dokumentu; polecenie operatora z 2026-09-23, aby follow-up nie dolaczal raportu ani ponownie przygotowanego kontekstu.

Autoryzacja zakresu: operator polecil zmiane dla wszystkich feature'ow i jawnie
wylaczyl kompatybilnosc wsteczna w tej samej rozmowie.

## Potrzeba / dlaczego

UI Explorer i UX Inspector serializuja caly zapisany raport w kazdym follow-upie. Flow Explorer ponownie renderuje obszerny scope i zasady, ktore sa juz w sesji i skillu. Powieksza to prompt i okno kontekstowe, szczegolnie po wygasnieciu cache.

## Proponowane rozwiazanie

Kazdy follow-up wznawia ten sam `copilotSessionId` i przekazuje tylko nowa wiadomosc operatora oraz, tam gdzie feature korzysta z runtime skilla, krotka wskazowke jego zaladowania. Feature zachowuje hidden scope, allowliste tools, auth i konfiguracje modelu poza promptem. UX Inspector zachowuje swoj krotki durable follow-up contract, poniewaz nie uzywa runtime skilli. Nie dolaczamy raportu ani ponownie renderowanego scope'u.

## Zakres

Poziom L2: Incident Analysis, Flow Explorer, UI Explorer i UX Inspector jako wszyscy obecni konsumenci follow-up. Zmiany promptow, wewnetrznych request DTO, testow i opisow runtime. Brak zmian publicznego HTTP API.

## Non-goals

Zmiana polityki cache Copilot CLI, compaction, modeli, tool policy albo mutacja raportu przez follow-up.

## Ograniczenia i ryzyka

Sesja musi byc dostepna i wznowiona; nie ma fallbacku do nowej sesji. Po compaction model moze nie miec pelnego body raportu, wiec odpowiedz powinna jawnie wskazac luke zamiast udawac dostep do nieobecnego evidence. Na zyczenie operatora nie utrzymujemy kompatybilnosci starych wariantow promptu ani wewnetrznych DTO.

## Baseline i conformance delta

- Incident Analysis: `EXISTING` i sama wiadomosc operatora; bez zmian.
- Flow Explorer: `EXISTING`, ale powtorzony runtime envelope, zasady skilla i repository scope; delta to wskazowka skilla i wiadomosc.
- UI Explorer: `EXISTING`, caly `AnalysisReport` w promptcie, read-only tools; delta usuwa report z requestu i promptu, pozostawia skill i hidden pinned scope.
- UX Inspector: `EXISTING`, caly `AnalysisReport` w promptcie, read-only tools i durable follow-up contract; delta usuwa report z requestu i promptu.
- Konsumenci: live job chat, local run continuation, zapis `chatMessages[].prompt`, import/export historii i wspolny aside. Publiczny ksztalt pozostaje bez zmian; historyczne prompty sa danymi historycznymi.
- Macierz testow: unit prompt UI/UX/Flow, live i local chat assembly, `PackageDependencyGuardTest`, pelny `mvn -q test`. Brak zmiany kontraktu UI, wiec brak builda Angulara.

## Kryteria akceptacji

Nowe prompty follow-up nie zawieraja serializowanego raportu, duplikatu scope'u ani initial result. Wszystkie cztery feature'y kontynuuja ta sama sesje; ich tools i policy pozostaja feature-owned. Testy backendu przechodza.

## Kroki

- [x] Zmienic prompty i wewnetrzne requesty w Flow Explorer, UI Explorer i UX Inspector; testy promptow i live/local chat sprawdzaja brak ponownej serializacji raportu i scope'u.
- [x] Zaktualizowac testy i dokumentacje runtime; `mvn -q test` oraz `git diff --check` przeszly 2026-09-23.
