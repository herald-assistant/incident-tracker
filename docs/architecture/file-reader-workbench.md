# File Reader — Tool Workbench

## Cel i granice

`/file-reader` jest laboratorium operatora do sprawdzania plików bez AI.
Nie tworzy rozmowy, analizy, historii ani sesji SDK. Nie wymaga PAT, wyboru
modelu ani włączonego System Assistanta. Pliki i wyniki pozostają w pamięci
żądania/przeglądarki, bez zapisu w workspace lub logowania zawartości.

Operator wybiera wiele plików tak jak przy załącznikach chatu i uruchamia
`Odczytaj pliki`. Lista i usuwanie są blokowane podczas pracy. Pliki są
odczytywane kolejno; błąd pojedynczego pliku pozostaje widoczny i nie blokuje
następnych. Zmiana listy unieważnia wcześniejsze wyniki. Opuszczenie widoku
anuluje bieżący odczyt i nie uruchamia kolejnych żądań.

## Preparacja i walidacja

`common.documents.DocumentAttachmentPreparer` jest wspólnym mechanizmem
preparacji dla Workbench i załączników rozmowy System Assistanta. MIME
pochodzi z rozszerzenia pliku (PNG/JPG/JPEG/WebP/GIF/BMP/PDF/DOCX), jak w chacie.
Adapter feature'a mapuje przygotowane bajty na SDK BlobAttachment;
operator API nie zależy od SDK ani feature'a.

- PDF: wszystkie strony jako JPEG 144 DPI przez `PdfPageRenderer`, w tej
  samej kolejności i z tymi samymi nazwami co w chacie; także skany i diagramy.
- PNG/JPEG/WebP/GIF: oryginalne bajty i MIME, bez konwersji lub OCR.
- Dla BMP nagłówek wyniku opisuje plik źródłowy, a podgląd zawiera bezstratny PNG przygotowany dla AI.
- DOCX: wyłącznie wynik sprawdzenia odczytu przez `DocxContentReader`.
  ZIP musi zawierać `word/document.xml` z prawidłowym korzeniem dokumentu
  Word i poprawnym XML. DTD i encje zewnętrzne są wyłączone. Odpowiedź nie
  zawiera tekstu, obrazów ani oryginalnych bajtów DOCX.

Walidacja DOCX potwierdza techniczny odczyt i gotowość do przekazania jako
załącznik; nie jest pełną walidacją wszystkich schematów Office ani dowodem
interpretacji przez konkretny model. Chat nadal przekazuje DOCX natywnie;
Workbench nie zmienia tej ścieżki na ekstrakcję tekstu.

## API i UI

`POST /api/files/preview?name={filename}` przyjmuje jedno pełne body
`application/octet-stream`. Nazwa jest normalizowana do basename.
Odpowiedź zawiera `name`, `mimeType`, `size`, `status` (`READY`/`FAILED`),
`validationOnly`, `message` i `images: [{name,mimeType,size,data}]`.
`data` to base64 obrazu przeznaczonego do podglądu; nie ma binariów DOCX.
HTTP 200 oznacza gotowy podgląd lub poprawny odczyt DOCX, 400 nieobsługiwany
format/nazwę, 422 pusty albo nieczytelny dokument. Nie ma limitów liczby
lub rozmiaru w TDW; obowiązują zasoby i ograniczenia transportu.

Wynik pod formularzem pokazuje obrazy/strony PDF i metadane albo samą
walidację DOCX. Nazwa, MIME, rozmiar i status są w jednym kompaktowym nagłówku.
Pojedynczy niezmieniony obraz nie powtarza nazwy/rozmiaru w podpisie ani
ogólnego komunikatu sukcesu. PDF zachowuje podpisy z numerem strony i rozmiarem
przygotowanego obrazu, bez ponownego wyświetlania nazwy dokumentu.
Komunikaty walidacji DOCX i błędów pozostają widoczne.
Zwijane Request preview i JSON odpowiedzi oferują kopiowanie
oraz pobranie JSON. Podgląd żądania zawiera metadane plików, bez ich bajtów.
Informacja o capability pozostaje w standardowym topbarze; nawigacja w Tool
Workbench, bez drugiego nagłówka ekranu i bez kontrolek AI.

## Weryfikacja

`FilePreviewControllerTest` sprawdza pełny PDF, niezmienione obrazy,
walidację DOCX bez ujawnienia treści, niepoprawny ZIP/XML i błędy HTTP.
Testy załączników System Assistanta chronią istniejące blobs i rendering.
Test komponentu obejmuje kolejność wielu plików, częściowy błąd, blokady,
usuwanie/ponowny wybór i anulowanie po opuszczeniu widoku; test shella
sprawdza route, sidebar oraz informację capability. Architecture guards
chronią brak zależności common/API od feature'a.
