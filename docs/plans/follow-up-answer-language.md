# Jezyk odpowiedzi w follow-up chat

Status: done

Source need: brak osobnego dokumentu; polecenie operatora z 2026-09-23, aby wszystkie follow-upy odpowiadaly jezykiem analityka, a na konkretne pytania o aspekt techniczny podawaly jego dokladne, weryfikowalne szczegoly.

Autoryzacja zakresu: operator w tej samej rozmowie polecil spojnosc dla wszystkich follow-up chatow i kontynuowanie sesji zgodnie ze skillem.

## Potrzeba / dlaczego

Domyslnie analityk potrzebuje wyjasnienia zachowania i skutku, ale pytania o kontrakt API, schemat danych lub nazwe systemu wymagaja dokladnych identyfikatorow. Obecne skille Flow i UI nie wyrazaja tego wyjatku wprost, a techniczny handoff incydentu moze byc mylony ze zwyklym pytaniem o szczegol.

## Proponowane rozwiazanie

Doprecyzowac feature-owned instrukcje follow-up bez nowego wspolnego skilla i bez dolaczania raportu do promptu. W kazdej sciezce utrzymac prosty jezyk jako default oraz dopuscic precyzyjne nazwy i struktury, gdy uzytkownik o nie pyta. Flow ma odpowiadac naturalnym Markdownem, bez stalego schematu pol. Incident Technical Handoff pozostaje specjalnym formatem na wyrazna prosbe o przekazanie techniczne.

## Zakres

Poziom L2: audyt wszystkich czterech obecnych konsumentow follow-up. Zmiana dwoch runtime skilli follow-up, dwoch skilli wyniku incydentu oraz instrukcji follow-up UI/UX. Zmiana dotyczy sposobu odpowiedzi, bez publicznego API i bez zmian tool policy.

## Non-goals

Nowy skill incydentowy, nowy turn modelu, serializacja raportu, zmiana sesji, mutacja raportu, zmiana initial result.

## Ograniczenia i ryzyka

Runtime loader nie nadpisuje juz aktywnych skilli. Obie lokalne kopie skilli sa obecnie identyczne z packaged seed, wiec po zmianie trzeba zaktualizowac rowniez efektywne pliki. Zasada nie upowaznia modelu do zgadywania niepotwierdzonych kontraktow ani schematow.

## Baseline i conformance delta

- Flow: domyslny jezyk domenowy, ale nazwy techniczne odsylane glownie do `Zrodla` i przykladowy blok z etykietami `answer`, `checkedEvidence`; delta: dokladne identyfikatory w odpowiedzi na pytanie i naturalny Markdown.
- UI: styl funkcjonalny w skillu i durable contract, ale bez jawnego wyjatku dla pytan o API lub dane; delta: zakres techniczny okresla pytanie.
- UX: brak runtime skilla; durable contract dopuszcza nazwy techniczne tylko gdy pomagaja zrozumiec zachowanie; delta: jawnie dopuszcza konkretny aspekt analityczny.
- Incident: brak dedykowanego skilla chatu, follow-up wysyla sama wiadomosc; skille `incident-functional-analysis` i `incident-technical-handoff` koncentruja sie na initial result lub pelnym przekazaniu; delta: zwykle pytanie o API, baze lub system nie odtwarza formatu wyniku ani pelnego handoffu.
- Konsumenci: live i local follow-up dla czterech feature'ow; obecne prompty i publiczne DTO bez zmian.
- Macierz weryfikacji: contract testy runtime skilli, testy instrukcji UI/UX, `CopilotRuntimeSkillFrontmatterTest`, pelny `mvn -q test`, diff i zgodnosc kopii aktywnych.

## Kryteria akceptacji

Kazda sciezka follow-up ma czytelna regule jezyka domyslnego i wyjatku dla konkretnych, potwierdzonych szczegolow technicznych zrozumialych dla analityka. Incident nie generuje pelnego Technical Handoff na zwykle pytanie o kontrakt. Brak zmiany promptu z dolaczonym raportem.

## Kroki

- [x] Doprecyzowano skille i instrukcje sesji, zachowujac granice feature'ow; celowane contract testy przeszly 2026-09-23.
- [x] Zsynchronizowano cztery lokalne effective skille (hash zgodny z packaged seed); `mvn -q test`, ponowne `mvn -q -o test` po finalnym doprecyzowaniu skilli oraz `git diff --check` przeszly 2026-09-23.
