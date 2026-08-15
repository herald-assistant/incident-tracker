# UI Explorer - dokumentacja funkcjonalna i techniczna widokow

Status: draft

## Potrzeba

Zlozony frontend nie ma aktualnej dokumentacji, ktora wyjasnialaby jego
zachowanie z perspektywy uzytkownika, procesu biznesowego oraz systemow
zasilajacych widok. Wiedza jest rozproszona pomiedzy routingiem, komponentami,
formularzami, zarzadzaniem stanem, klientami backendu, uprawnieniami oraz
customowymi bibliotekami. Nazwy plikow i struktura kodu nie sa uzytecznym
punktem wejscia dla analityka, ktory nie zna Angulara ani architektury
aplikacji.

Brak tej dokumentacji utrudnia:

- zrozumienie, co uzytkownik moze zobaczyc i zrobic na danym ekranie,
- przygotowanie kompletnego inputu do zmiany funkcjonalnej,
- ocene konsekwencji zmiany dla formularzy, akcji, danych i nawigacji,
- przekazanie wiedzy pomiedzy analitykami, developerami i testerami,
- wykrycie warunkow, wariantow i ograniczen ukrytych w kodzie,
- odroznienie zachowania potwierdzonego od przypuszczenia wynikajacego z
  niepelnej widocznosci.

## Kontekst produktu

Docelowym zrodlem jest frontend bedacy duzym monolitem rozwijanym w Angularze,
Nx, RxJS i NgRx, z elementami Angular Material, customowym UI oraz bibliotekami
publicznymi i organizacyjnymi. Aplikacja moze zawierac:

- gleboki routing, lazy loading, guardy i nawigacje zalezne od kontekstu,
- wiele formularzy, tabel, akcji i wizualizacji na jednym ekranie,
- formularze hardcodowane oraz generowane z definicji dostarczanych przez
  backend,
- dynamiczne walidacje, wyliczenia, widocznosc, edytowalnosc i zaleznosci
  pomiedzy polami,
- dane wielu domen pobierane i aktualizowane przez REST oraz WebSocket,
- synchronizacje stanu przez store, efekty, reducery i selectory,
- uwierzytelnienie przez Keycloak oraz warunki widocznosci zalezne od rol,
- znaczacy dlug technologiczny i lokalne konwencje, ktore nie zawsze sa
  konsekwentne.

UI Explorer ma byc odporny na te roznice. Brak dostepu do implementacji
biblioteki organizacyjnej albo schematu dostarczanego dopiero w runtime nie
moze prowadzic do wymyslania zachowania.

## Uzytkownicy i decyzje

Glownym uzytkownikiem jest analityk biznesowo-systemowy, ktory nie musi znac
struktury repozytorium ani sposobu pracy AI. Z wyniku moga korzystac rowniez
tester, developer, product owner i osoba utrzymujaca system.

Uzytkownik powinien moc wykorzystac UI Explorer do jednej z trzech decyzji:

1. zrozumiec i udokumentowac funkcjonalnie istniejacy widok,
2. przygotowac kompletny material wejsciowy do planowanej zmiany,
3. uzyskac techniczny handoff wyjasniajacy realizacje zachowania i miejsca
   prawdopodobnego oddzialywania zmiany.

Te cele powinny korzystac ze wspolnej struktury wyniku. Wybor celu zmienia
priorytety i domyslna szczegolowosc, a nie tworzy trzech niespojnych produktow.

## Jednostka analizy

Jednostka analizy to **widok w konkretnym scenariuszu**:

- aplikacja lub system,
- wersja zrodla,
- punkt nawigacyjny albo ekran,
- opcjonalny wariant wejscia lub opis sytuacji biznesowej.

Sam komponent, plik albo adres endpointu nie jest jednostka zrozumiala dla
analityka. Jeden widok moze skladac sie z wielu komponentow i domen, a ten sam
widok moze zachowywac sie inaczej w zaleznosci od roli, danych, parametrow
trasy albo stanu procesu.

## Oczekiwane wejscie

Podstawowy przebieg powinien opierac sie na polach wyboru, radio buttonach i
jednym opcjonalnym polu opisowym. Uzytkownik wybiera:

- aplikacje lub system,
- dostepna wersje zrodla,
- ekran z katalogu opisanym nazwa i sciezka nawigacyjna,
- cel: dokumentacja funkcjonalna, przygotowanie zmiany albo dokumentacja
  techniczna,
- oczekiwane sekcje i ich poziom: pominieta, skrocona albo poglebiona,
- opcjonalny scenariusz, role, przypadek danych lub zakres zmiany opisany
  naturalnym jezykiem.

Uzytkownik nie powinien podawac sciezek plikow, nazw komponentow, nazw tools,
promptow ani instrukcji eksploracji repozytorium.

Lista aplikacji nie powinna powstawac z przypadkowo znalezionych repozytoriow.
Powinna zawierac tylko frontendy jawnie zarejestrowane w katalogu systemow i
posiadajace kontrolowany zakres wyszukiwania kodu. Brak kompletnej rejestracji
ma byc widocznym problemem konfiguracji, a nie powodem do zgadywania przez AI.

## Oczekiwany wynik

Wynik powinien byc jednym, czytelnym raportem z wybieralnymi sekcjami:

1. **Cel i kontekst widoku** - po co istnieje ekran, kto z niego korzysta i
   jakie miejsce zajmuje w procesie.
2. **Nawigacja i dostep** - jak wejsc na ekran, parametry wejscia, guardy,
   widoczne warunki dostepu i role.
3. **Struktura widoku** - sekcje, formularze, tabele, zestawienia, komunikaty i
   customowe elementy wizualne.
4. **Akcje i rezultaty** - czynnosci uzytkownika, warunki dostepnosci,
   potwierdzenia, skutki, przejscia oraz mozliwe bledy.
5. **Formularze i reguly** - pola i grupy, pochodzenie wartosci, walidacje,
   wyliczenia, zaleznosci, pokaz/ukryj, zmiany stanu oraz zasady recznej
   korekty wyniku automatycznego.
6. **Dane i uslugi** - informacje prezentowane i modyfikowane, ich zrodla,
   odswiezanie, operacje odczytu i zapisu oraz widoczne integracje.
7. **Stan i synchronizacja** - jak dane trafiaja do widoku, co jest stanem
   lokalnym lub wspoldzielonym i jakie zdarzenia powoduja ponowne pobranie lub
   przeliczenie.
8. **Warianty i sytuacje wyjatkowe** - roznice wynikajace z roli, danych,
   statusu procesu, bledow, pustych wynikow oraz niepelnej widocznosci.

Raport funkcjonalny powinien uzywac jezyka biznesowego. Szczegoly techniczne
powinny byc dostepne jako osobna warstwa albo w profilu technicznym, bez
zmuszania analityka do czytania nazw klas i operatorow RxJS.

## Wiarygodnosc i widocznosc

Kazde istotne twierdzenie powinno miec jeden z poziomow pewnosci:

- **potwierdzone** - ma bezposrednie oparcie w dostepnym zrodle,
- **wywnioskowane** - wynika z kilku sygnalow, ale nie jest jawnie zapisane,
- **nieustalone** - nie da sie go bezpiecznie rozstrzygnac z dostepnego
  materialu.

Wynik musi wskazywac analizowana wersje zrodla, zakres obejrzanego materialu i
ograniczenia widocznosci. Dotyczy to szczegolnie:

- konfiguracji formularza dostarczanej dopiero przez backend,
- zachowania ukrytego w niedostepnej bibliotece organizacyjnej,
- uprawnien egzekwowanych przez backend lub Keycloak poza kodem widoku,
- regul biznesowych wykonywanych tylko przez usluge backendowa,
- wariantow zaleznych od danych runtime.

Warunek widocznosci elementu po stronie klienta nie moze byc przedstawiony
jako dowod skutecznej autoryzacji operacji po stronie backendu.

## Doswiadczenie uzytkownika

UI Explorer powinien prowadzic uzytkownika przez znany w platformie przebieg
analizy: konfiguracja, przygotowanie kontekstu, praca AI i raport. Nazwy krokow
maja opisywac prace zrozumiala dla analityka, a nie mechanike agentowa.

Postep powinien pokazywac, czy udalo sie:

- zidentyfikowac ekran i jego wejscia,
- znalezc glowna strukture oraz zachowania,
- powiazac formularze, dane, akcje i nawigacje,
- osiagnac wystarczajace pokrycie wybranych sekcji,
- zapisac raport albo jawnie wskazac braki.

Dowody techniczne powinny byc dostepne do weryfikacji, lecz nie dominowac
glownego raportu.

## Kryteria sukcesu MVP

MVP jest uzyteczny, jezeli:

- analityk potrafi uruchomic analize bez znajomosci repozytorium i Angulara,
- ekran jest wybierany z katalogu zamiast wskazywany nazwa pliku,
- raport konsekwentnie opisuje wybrane sekcje i zachowuje jeden uklad dla
  wszystkich celow,
- kazda sekcja rozdziela fakty, wnioski i niewiadome,
- wynik identyfikuje zrodla danych oraz operacje aktualizujace backend, gdy sa
  widoczne w kodzie,
- analiza formularza opisuje dynamiczne zachowania bez deklarowania
  nieobserwowalnych regul runtime jako faktow,
- wynik nadaje sie do skopiowania jako dokumentacja albo material do
  przygotowania zmiany,
- powtorzenie analizy dla tej samej wersji i zakresu daje porownywalna
  strukture,
- niepowodzenie jednej sekcji nie ukrywa poprawnie zebranych wynikow
  pozostalych sekcji,
- raport jawnie podaje wersje zrodla i ograniczenia widocznosci.

## Non-goals MVP

MVP nie ma:

- uruchamiac aplikacji w przegladarce ani automatycznie logowac sie przez
  Keycloak,
- odtwarzac wszystkich kombinacji danych, rol i stanow procesu,
- tworzyc pixel-perfect makiety albo zastapic testow wizualnych,
- analizowac pelnej implementacji wszystkich uslug backendowych,
- gwarantowac kompletnosci regul dostarczanych dopiero w runtime,
- budowac trwalego globalnego grafu wszystkich komponentow frontendu,
- modyfikowac kodu, publikowac dokumentacji ani wykonywac akcji w systemie
  zrodlowym,
- traktowac wygenerowanego raportu jako dowodu backendowej autoryzacji,
- oferowac follow-up chatu zmieniajacego wynik analizy.

## Ryzyka i kompromisy

- Statyczny kod pokazuje mozliwe zachowania, ale nie zawsze konkretny stan
  runtime.
- Customowe biblioteki moga ukrywac semantyke za generycznym API; nazwy i
  uzycie sa wtedy tylko sygnalem.
- Cross-domainowy ekran moze wymagac szerokiej eksploracji, dlatego potrzebne
  sa limity i jawne pokrycie zamiast pozornej kompletnosci.
- Dlug technologiczny i dynamiczne wywolania moga uniemozliwic jednoznaczne
  powiazanie akcji z usluga.
- Dokumentacja generowana bez przypisania do wersji kodu szybko stalaby sie
  nieodroznialna od nieaktualnej dokumentacji recznej.

## Dalsze rozszerzenia poza MVP

Po zweryfikowaniu wartosci statycznego MVP mozna rozwazyc:

- polaczenie analizy kodu z kontrolowana sesja przegladarki,
- porownanie raportow pomiedzy wersjami zrodla,
- follow-up chat korzystajacy z zamrozonego kontekstu analizy,
- generowanie scenariuszy testowych i kryteriow akceptacji,
- publikacje zatwierdzonego raportu do zewnetrznego repozytorium wiedzy,
- wejscie od zrzutu ekranu lub adresu widoku jako pomoc w wyborze katalogowym,
- rozszerzenie analizy o kontrakty i implementacje backendu w osobnym,
  kontrolowanym zakresie.
