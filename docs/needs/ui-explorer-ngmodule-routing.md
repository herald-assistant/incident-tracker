# UI Explorer dla aplikacji Angular z NgModule

Operator po zarejestrowaniu frontendu Angular moze zobaczyc pusta liste widokow,
choc aplikacja ma statycznie zdefiniowane trasy. Dotyczy aplikacji uruchamianych
przez modul glowny, a nie przez standalone bootstrap.

Oczekiwany rezultat: UI Explorer pokazuje widoki osiagalne z produkcyjnego
startu takiej aplikacji, wraz z rewizja zrodla i jawnymi diagnostykami, gdy
powiazania nie da sie bezpiecznie rozstrzygnac.

Sukces mierzymy tym, ze fikcyjny frontend CRM z modulowym startem i routingiem
zwraca wybieralne widoki, a istniejące przypadki standalone, niejednoznaczne
starty i ograniczenia scope nadal zachowuja dotychczasowe zasady.

Poza zakresem sa wykonywanie aplikacji, skanowanie calego repozytorium,
zgadywanie tras dynamicznych i automatyczne dolaczanie widokow bez potwierdzonej
osiagalnosci od produkcyjnego startu.
