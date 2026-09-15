# Change Verification - wynik prowadzony przez reguly zrodlowe

Analityk opisuje oczekiwane zachowanie w Jira, Confluence oraz instrukcjach
repozytorium i chce po zakonczeniu Change Verification od razu zobaczyc, ktore
z tych regul zostaly spelnione, ktore nie zostaly spelnione, a ktorych nie da
sie zweryfikowac na podstawie dostepnego materialu.

Obecny wynik rozdziela te same ustalenia pomiedzy `Priorytet review`,
`Potwierdzony zakres`, `Pelny material`, sugestie AI i appendix. Powoduje to
powtorzenia, utrudnia powiazanie wniosku z regula autora i zmusza analityka do
odszukiwania szczegolow przed przekazaniem zadania do implementacji.

Potrzebny jest jeden kanoniczny rejestr regul, w ktorym kazda regula zrodlowa
wystepuje dokladnie raz, zachowuje oryginalny zapis i zrodlo, a obok ma
jednoznaczny rezultat, uzasadnienie, dowody, brakujacy material i ewentualne
dzialanie. Dodatkowe kontrole zaproponowane przez AI musza byc jawnie oddzielone
od regul autora i nie moga zmieniac werdyktu dla zakresu zrodlowego.

Widok ma wspierac szybka decyzje release oraz doglebienie pojedynczej reguly,
bez rownoleglych, konkurencyjnych reprezentacji tego samego wyniku.
