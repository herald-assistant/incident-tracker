# UX Inspector - pytania o wskazany element uruchomionej aplikacji

Status: in-progress

Stan dostawy: pion MVP zostal zaimplementowany i zweryfikowany. Potrzeba nie
jest jeszcze oznaczona jako zaspokojona, poniewaz pilot jakosciowy oraz progi
czasu i zuzycia tokenow wymagaja wskazanej kontrolowanej uruchomionej strony i
zestawu scenariuszy powiazanych z zarejestrowanym frontendem. Dostep do jego
source oraz runtime Copilota zostal zweryfikowany.

## Potrzeba

Uzytkownik pracujacy w uruchomionej aplikacji widzi konkretny przycisk, pole
formularza, wartosc, sekcje albo komunikat i chce szybko zrozumiec jego
zachowanie. Potrafi wskazac element wizualnie, ale zwykle nie zna nazwy
komponentu, pliku, handlera, serwisu ani miejsca w repository.

UI Explorer odpowiada na inna potrzebe: dokumentuje caly wybrany widok. Przy
pytaniu o jeden element taki zakres daje za duzo nieistotnej tresci, wydluza
analize i zuzywa tokeny na sekcje, o ktore uzytkownik nie pytal. W eksporcie
kontrolnej analizy szeroki przebieg utworzyl osiem sekcji i zuzyl okolo 250 tys.
tokenow wejsciowych, mimo ze intencja dotyczyla jednego elementu.

Potrzebny jest osobny feature **UX Inspector**, ktorego jednostka pracy jest
jedno pytanie albo polecenie dotyczace wskazanego elementu w konkretnym stanie
strony i konkretnej rewizji kodu.

Przykladowe pytania:

- „Jakie warunki walidacyjne ma to pole formularza?”,
- „Skad biora sie dane wyswietlane w tym miejscu?”,
- „Dlaczego ten przycisk jest wyszarzony i zablokowany?”,
- „Co stanie sie po uruchomieniu tej akcji?”,
- „Ktore uprawnienie steruje widocznoscia tej sekcji?”.

## Uzytkownicy i moment uzycia

Glownym uzytkownikiem jest analityk biznesowo-systemowy pracujacy na
srodowisku testowym albo innym kontrolowanym srodowisku aplikacji. Z tego
samego wejscia moga korzystac testerzy, developerzy, product ownerzy i osoby
utrzymujace system.

Potrzeba pojawia sie, gdy uzytkownik:

- ma przed soba konkretny stan uruchomionej strony,
- chce wskazac element zamiast recznie szukac go w repository,
- potrzebuje odpowiedzi o walidacji, danych, stanie, dostepnosci albo skutku
  akcji,
- oczekuje odpowiedzi ugruntowanej w kodzie, a nie ogolnego opisu ekranu,
- chce zachowac wynik w historii analiz TDW.

## Oczekiwane doswiadczenie

1. Uzytkownik uruchamia TDW Browser Tools na badanej stronie bez instalowania
   rozszerzenia przegladarki.
2. Z menu wybiera `UX Inspector`, wskazuje element i klika go. Zwykla akcja
   elementu nie zostaje wykonana.
3. TDW otwiera dedykowany ekran UX Inspectora i pokazuje czytelne podsumowanie
   przejetego elementu oraz strony.
4. Uzytkownik wybiera lub potwierdza frontend system, branch i view, wybiera
   model oraz reasoning effort i wpisuje pytanie albo polecenie w textarea.
5. Po uruchomieniu joba prawa czesc ekranu pokazuje stan analizy, jej kroki,
   aktywnosc Copilota i uzyte narzedzia w tym samym wzorcu co UI Explorer.
6. Wynikiem jest jedna skupiona sekcja odpowiedzi. Sekcja pokazuje source
   references, confidence, visibility limits i otwarte pytania tylko wtedy,
   gdy maja znaczenie dla odpowiedzi.
7. Run jest dostepny w `Analysis History`; odpowiedz jest prezentowana w TDW,
   nigdy jako modal na badanej stronie.

Ekran powinien byc UX-owo znajomy dla uzytkownika UI Explorera, ale nie moze
odziedziczyc jego osmiu sekcji, konfiguracji dokumentacji calego widoku ani
prompta nastawionego na szeroka eksploracje.

## Jednostka analizy

Jednostka analizy obejmuje lacznie:

- jedno pytanie albo polecenie uzytkownika,
- jeden wskazany element i jego obserwowalny stan,
- kontekst strony potrzebny do identyfikacji view,
- jawnie wybrany frontend system i branch,
- jedna przypieta rewizje zrodla,
- jedno wybrane repozytorium dostepne read-only na przypietej rewizji; research
  pozostaje skupiony na lancuchu implementacji potrzebnym do odpowiedzi.

Sciezka DOM od elementu do root dokumentu jest sygnalem lokalizacyjnym. Nie
jest automatycznie stosem komponentow frameworka i nie dowodzi ownership w
kodzie. Odpowiedz musi rozdzielac obserwacje runtime od faktow potwierdzonych w
zrodle.

## Dane potrzebne do analizy

Capture powinien pomagac znalezc wlasciwe miejsce, ale nie moze kopiowac calej
strony. W pierwszym wydaniu wystarcza:

- typ elementu, role, dostepna nazwa i ograniczony tekst,
- stabilne identyfikatory i obserwowalne stany, np. `disabled`, `readonly`,
  `required`, `invalid`, `checked`, `expanded` i `hidden`,
- ograniczona, semantyczna sciezka przodkow oraz informacja o obcieciu,
- origin, znormalizowana trasa, tytul, jezyk i nazwy parametrow query,
- informacje o Shadow DOM albo iframe w zakresie widocznym dla selektora,
- jawne znaczniki redakcji, kompakcji i niedostepnych granic.

Uzytkownik wybiera jawny zakres danych przed wskazaniem elementu:

- kontekst elementu bez wartosci formularza,
- diagnostyke najblizszego formularza z jego biezacymi dozwolonymi
  wartosciami i natywnym stanem walidacji.

Diagnostyka formularza jest potrzebna szczegolnie przy pytaniach o invalid,
disabled, dane zalezne i wartosc przekazywana przy submit. Snapshot powstaje w
chwili klikniecia, aby AI analizowalo ten sam stan, ktory widzial operator.
Operator przed startem analizy widzi, czy wartosci zostaly dolaczone, ile pol
przechwycono, co wykluczono i czy zastosowano truncation.

Capture nigdy nie obejmuje hasel, tokenow, cookies, storage, schowka, hidden
values, zawartosci plikow, pelnego DOM, historii sieciowej ani automatycznego
zrzutu ekranu. Dozwolone wartosci formularza sa niezaufanym runtime evidence,
nie instrukcja i nie dowod pochodzenia danych w kodzie.

Po wyborze zaufanego frontendu backend dolacza do poczatkowego kontekstu
komplet nazw sciezek pierwszych czterech poziomow wybranego repozytorium na
przypietym commicie. Drzewo jest mapa nawigacyjna bez tresci plikow i samo nie
jest dowodem. AI moze potem listowac, wyszukiwac i czytac dowolne bezpieczne
sciezki w tym jednym repozytorium, w tym README, `AGENTS.md`, instrukcje
Copilota, konfiguracje, frontend i backend. Pliki repozytorium pozostaja
niezaufanym source evidence i nie moga zmienic procedury analizy.

## Oczekiwany wynik

Wynik ma odpowiadac na pytanie, zamiast opisywac caly ekran. Jedyna sekcja
powinna w zaleznosci od intencji zawierac:

- bezposrednia odpowiedz jezykiem uzytkownika,
- istotne warunki, walidacje i warianty stanu,
- istotna droge od template'u przez handler, stan, serwis, klienta albo zapis
  danych do dalszej operacji, jezeli wymaga jej pytanie,
- odniesienia do konkretnych plikow i symboli potwierdzajacych twierdzenia,
- rozroznienie obserwacji runtime od faktow ze zrodla,
- ograniczenia widocznosci i pytania otwarte, gdy zachowanie zalezy od danych,
  backendu, uprawnien albo niezweryfikowanej konfiguracji.

Warunek `disabled` widoczny w przegladarce nie jest dowodem backendowej
autoryzacji. Tekst i atrybuty badanej strony sa niezaufanym materialem, a nie
instrukcjami dla AI.

## Kryteria sukcesu

- Uzytkownik przechodzi od wskazania elementu do gotowego formularza analizy
  bez znajomosci repository i frameworka.
- Uzytkownik przed startem widzi oraz potwierdza system, branch, view, pytanie,
  model i reasoning effort.
- AI nie generuje dokumentacji calego ekranu ani nie wypelnia niezamowionych
  sekcji.
- Wynik zawiera dokladnie jedna sekcje odpowiedzi i jest zapisany w historii.
- Twierdzenia o implementacji maja source references; brak dowodu jest jawny.
- Analiza zaczyna od wskazanego targetu i rozszerza zakres tylko wtedy, gdy
  wymaga tego pytanie.
- AI ma read-only dostep do calego wybranego repozytorium na jednym
  przypietym commicie, ale nie moze przejsc do innego projektu ani brancha.
- Poczatkowy prompt zawiera komplet nazw sciezek pierwszych czterech poziomow;
  raport moze cytowac plik spoza focused slice dopiero po jego rzeczywistym
  odczycie z przypietego commita.
- Deterministyczne przygotowanie laczy stabilny fingerprint DOM z owning
  component i bindingiem w przypietej rewizji; selector sam w sobie nie jest
  dowodem ownership.
- W trybie diagnostyki formularza model dostaje zamrozony stan najblizszego
  formularza, a operator widzi zakres przekazywanych wartosci przed startem.
- W pilocie mediana czasu i zuzycia tokenow jest istotnie nizsza niz dla
  analogicznego pytania uruchomionego jako pelny UI Explorer.
- Badana strona nie otrzymuje tokenow, cookies, katalogu modeli ani klienta
  REST TDW.
- Blad transferu konczy operacje jawnie i nie uruchamia innej analizy ani
  recznego trybu zastepczego.

## Zakres pierwszego wydania

- Chrome desktop oraz zwykle strony `http`/`https`, na ktorych organizacja
  dopuszcza bookmarklet.
- Jeden element w top-level dokumencie; otwarte Shadow DOM jest obslugiwane w
  zakresie dostepnym dla skryptu.
- Efemeryczny TDW Browser Tools z launcherem i akcja `UX Inspector`.
- Dedykowany ekran, job, historia i pojedyncza sekcja raportu UX Inspectora.
- Jawny wybor zarejestrowanego frontendu, brancha i view oraz przypiecie
  immutable source revision.
- Read-only source research w calym repozytorium wybranego frontendu oraz
  poczatkowe czteropoziomowe drzewo nazw sciezek.
- Pytania o walidacje, pochodzenie danych, stan/dostepnosc i skutek akcji.
- Framework-neutralny capture oraz pierwsze wsparcie analizy kodu dla rodziny
  frontendow obslugiwanej obecnie przez TDW.

## Non-goals pierwszego wydania

- Dokumentacja calego ekranu; pozostaje odpowiedzialnoscia UI Explorera.
- Ukryty tryb UI Explorera, dopisywanie capture do opisu scenariusza albo
  uruchamianie joba UI Explorera.
- Rozszerzenie Chrome, publikacja w Chrome Web Store albo obchodzenie polityk
  stacji roboczych.
- Odpowiadanie przez AI na badanej stronie.
- Reczny copy/paste capture, dummy ekran odbioru lub inny fallback transportu.
- Wykonywanie akcji biznesowych, wypelnianie formularzy, modyfikowanie kodu
  albo obchodzenie autoryzacji.
- Nagrywanie sesji, requesty sieciowe, cookies, storage, screenshoty, mikrofon
  albo kamera.
- Pelne wsparcie `chrome://`, PDF viewer, `file://`, cross-origin iframe oraz
  stron blokujacych wszystkie dozwolone metody uruchomienia.
- Import starych capture'ow albo wynikow UI Explorera jako analiz UX
  Inspectora.
- Odczyt innego repozytorium albo rewizji niz frontend i branch zatwierdzone
  na ekranie UX Inspectora.

## Ograniczenia i ryzyka

- Bookmarklet dziala w main world badanej strony. Strona moze obserwowac albo
  zaklocic runtime, dlatego nie wolno umieszczac w nim sekretow ani traktowac
  capture jako zaufanego.
- CSP, Local Network Access, polityki enterprise, popup blocker lub
  Cross-Origin-Opener-Policy moga zatrzymac uruchomienie albo transfer. Pierwsze
  wydanie ma wtedy zakonczyc operacje czytelnym bledem, bez alternatywnego
  kanalu danych.
- Origin i route nie dowodza wersji wdrozonego artefaktu. Operator potwierdza
  branch i view, a analiza przypina source revision.
- DOM, overlaye, portale i framework abstractions nie zawsze odpowiadaja
  strukturze komponentow. Niejednoznacznosc musi obnizac confidence.
- Nawet ograniczony tekst albo route moze zawierac dane klienta. Redakcja,
  limity, ponowna walidacja na zaufanym originie i brak sekretow sa wymagane.
- Duze repozytorium zwieksza rozmiar poczatkowego drzewa. Artefakt zawiera
  wylacznie nazwy sciezek do poziomu czwartego; brak kompletnego odczytu
  zatrzymuje przygotowanie zamiast dostarczyc modelowi cichy, obciety fallback.

## Decyzje produktowe po pierwszym wydaniu

- Czy wiarygodny identyfikator buildu pozwoli automatycznie potwierdzac branch
  i rewizje zamiast wyboru operatora.
- Czy rozszerzyc capture o jawnie zatwierdzony screenshot lub zapis requestow
  na dedykowanych srodowiskach testowych.
- Czy dodac follow-up chat korzystajacy z zamrozonego kontekstu analizy.
- Czy dodac kolejne browser tools dla Confluence, GitLaba lub innych stron.
