# Podlaczanie repozytorium do analiz Operational Context

## Problem

Asysta AI jest najczesciej uruchamiana po to, aby nowy projekt GitLab stal sie
zrodlem kodu dla analiz. Sam opis w wolnym tekscie nie przypomina operatorowi
o faktach, ktorych nie da sie wiarygodnie odczytac z repozytorium: czy projekt
jest wdrazanym systemem, niewdrazana biblioteka albo czescia juz znanego
systemu. Obecny kreator zawsze zaczyna propozycje od nowego systemu. Przy
bibliotece moze to utworzyc fikcyjny byt, a sam wpis repozytorium bez
powiazania z systemem nie pojawi sie w jego sciezce system -> kod.

Problem dotyczy operatora, ktory zna przeznaczenie projektu, lecz nie zna
struktury YAML ani zasad code-search scope. Przy wielu polach formularza latwo
porzuci jednak asyste i wrocic do swobodnego opisu.

## Oczekiwany rezultat

- Operator nadal zaczyna od opisu i jednego projektu GitLab, a kilka krotkich,
  opcjonalnych odpowiedzi pomaga mu podac kluczowe fakty o roli repozytorium.
- Pytania zalezne od odpowiedzi pojawiaja sie tylko wtedy, gdy sa potrzebne.
  Operator nie musi znac `repositoryType`, `systemSubtype`, GitLab group path
  ani struktury code-search scope.
- Niewdrazana biblioteka moze zostac opisana jako repozytorium bez tworzenia
  sztucznego systemu. Gdy znane sa systemy korzystajace z biblioteki, asysta
  proponuje bezpieczne powiazanie ich sciezek system -> kod.
- Dla nowego wdrazanego systemu operator moze podac jego nazwe oraz, jesli zna,
  nazwe widoczna w logach. Dla kodu istniejacego systemu wybiera ten system
  zamiast tworzyc duplikat.
- AI odroznia odpowiedzi operatora od obserwacji w plikach GitLab. Kazda zmiana
  pozostaje propozycja do przegladu i osobnego zapisu.

## Kryteria sukcesu

- Obok opisu widac najwyzej jeden staly wybor o roli repozytorium; pozostale
  pola pojawiaja sie warunkowo. Wybor "nie wiem" jest poprawna odpowiedzia.
- W sciezce biblioteki asysta nie tworzy systemu. Bez wskazanych konsumentow
  pokazuje brak powiazania z analizami systemow jako pytanie lub ograniczenie.
- Wskazany istniejacy system jest weryfikowany wobec biezacego katalogu. Jego
  code-search scope moze otrzymac nowe repozytorium bez zmiany primary repo.
  Brak jednoznacznego scope nie powoduje zgadywania celu.
- Dla nowego wdrazanego systemu asysta nie nadaje potwierdzonego ownera ani
  subtype frontendu z samego repozytorium. Nazwa w logach trafia do sygnalow
  tylko jako jawna informacja operatora.
- Starszy scenariusz tworzenia obszaru bez projektu GitLab nadal dziala, a
  bledne lub nieznane pola formularza nie sa przyjmowane przez API.

## Ograniczenia i non-goals

- To nie jest automatyczny import calego GitLaba ani skan wszystkich zaleznosci.
- Nie pytamy na starcie o ownera, zespol, technologie, subtype, prefixy kodu,
  deployment manifest ani proces biznesowy. Te fakty wymagaja osobnego review
  albo mieszczą sie w opisie, gdy sa istotne.
- Wpis repozytorium bez powiazania z systemowym scope moze pomoc w discovery,
  ale nie jest jeszcze pelnym zrodlem code search dla analizy danego systemu.
