# Flow Explorer - wybor systemu z endpointami

Operator wybiera system w Flow Explorerze, aby zbadac konkretny endpoint.
Obecnie lista pokazuje rowniez jawnie sklasyfikowane frontendy. Po ich wyborze
operator czeka na katalog endpointow, ktory pozostaje pusty, i nie moze
rozpoczac analizy. To sugeruje obsluge nieistniejacego scenariusza i marnuje
czas.

Oczekiwany rezultat: frontend nie pojawia sie w wyborze systemu dla analizy
endpointu. Systemy mogace udostepniac endpointy, w tym mieszane, pozostaja
dostepne. Brak endpointow w innym systemie nadal jest jawnie widoczny po
sprawdzeniu wybranej galezi, poniewaz katalog systemow sam nie dowodzi
istnienia endpointow w kodzie.

Sukces: jawnie oznaczony frontend znika z listy, pozostale obecnie obslugiwane
systemy zachowuja dostep, a publiczny ksztalt odpowiedzi nie zmienia sie.
