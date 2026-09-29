# UX Inspector: jedno przekazanie obserwacji wybranego elementu

Status: potrzeba opisana; plan do zatwierdzenia.

## Problem i odbiorcy

Operator wskazuje element w badanej aplikacji, a nastepnie konczy formularz
analizy w TDW. Obserwacja elementu, widoczne pola formularza i stan aplikacji
przechodza dzis kilkoma etapami. Ich osobne przekazywanie, potwierdzanie i
zapisywanie komplikuje utrzymanie oraz obsluge bledow. Odwiezenie formularza
przed startem moze utracic juz zebrane dane.

Podglad obserwacji zajmuje znaczna czesc formularza, chociaz operator zwykle
chce przede wszystkim zadac pytanie. Powinien moc sprawdzic zebrane dane na
zadanie, bez koniecznosci ogladania technicznych szczegolow przy kazdej analizie.

## Oczekiwany rezultat

- Wskazanie elementu przekazuje wszystkie aktualnie zbierane rodzaje danych
  jako jedna obserwacje i pozwala przejsc do formularza z jej identyfikatorem.
- Formularz odczytuje te sama zamrozona obserwacje, takze po odswiezeniu karty,
  dopoki backend nie zostanie uruchomiony ponownie.
- Kompaktowy opis potwierdza wybor elementu; szczegoly, pola formularza i
  stan aplikacji sa domyslnie zwiniete i dostepne przed uruchomieniem analizy.
- Zadanie pytania i start nie wymagaja ponownego zbierania ani wysylania
  danych z badanej strony.
- Model otrzymuje te same rodzaje materialu, ktore otrzymuje obecnie:
  obserwacje i pola w poczatkowym kontekscie oraz celowany odczyt stanu aplikacji.

## Ustalenia operatora

- Nieuzyte obserwacje sa przechowywane tylko w pamieci backendu. Restart
  usuwa je; nie wymagamy ich odtwarzania.
- Nie wprowadzamy limitu rozmiaru zapisu, liczby oczekujacych obserwacji ani
  automatycznego wygasania przed startem analizy.
- Po rozpoczeciu analizy stan aplikacji jest utrwalany jak obecnie, aby
  narzedzia i kontynuacja zakonczonej analizy dzialaly takze po restarcie.
- Nie wymagamy kompatybilnosci z wczesniejszymi analizami ani formatami.
  Zmiane traktujemy jako wdrozenie przed pierwszym uzyciem feature'a.
- Zachowujemy dotychczasowy zakres obserwacji, wykluczanie sekretow i jawne
  oznaczanie niedostepnych danych. Brak opcjonalnego odczytu nie blokuje
  analizy poprawnie wskazanego elementu.

## Kryteria sukcesu

1. Jedno wskazanie elementu prowadzi do jednego kompletnego zapisu obserwacji.
2. Po przejsciu do TDW i odswiezeniu strony mozna przejrzec te same dane oraz
   uruchomic analize bez kontaktu z badana karta.
3. Start analizy korzysta ze wskazanego zapisu; dane nie sa ponownie zbierane
   ani przesylane z formularza.
4. Restart przed startem daje czytelny komunikat o braku obserwacji. Restart
   po zakonczonej analizie nie usuwa zapisanego wyniku ani stanu potrzebnego
   do jej kontynuacji.
5. Szczegoly obserwacji nie dominuja nad pytaniem i konfiguracja analizy;
   mozna je rozwinac klawiatura i sprawdzic wszystkie zebrane dane.

## Ograniczenia i non-goals

Brak limitow aplikacyjnych nie oznacza nieograniczonej pamieci przegladarki
lub JVM ani nieograniczonego kontekstu modelu. Znaczne obserwacje i ich liczba
moga zwiekszac zuzycie pamieci; jest to konsekwencja wybranego przechowywania.

Potrzeba nie zmienia odpowiedzi merytorycznej UX Inspectora, wyboru kodu i
rewizji, modelu AI, zasad pracy tools ani funkcji UI Explorera. Nie obejmuje
zbierania nowych kategorii danych, ruchu sieciowego, screenshotow ani
odtwarzania historycznych analiz.

Plan wykonania: [UX Inspector: capture przez REST i snapshot w pamieci](../plans/ux-inspector-capture-snapshot.md).
