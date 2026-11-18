import type { FormTourStep } from '../../core/models/form-tour';

export function incidentFormTour(elasticsearch: boolean, csvUpload: boolean): FormTourStep[] {
  const steps: FormTourStep[] = [];
  if (elasticsearch) {
    steps.push({
      anchor: 'incident-correlation-id',
      title: 'Correlation ID',
      description: 'Wklej identyfikator żądania z logów lub zgłoszenia. Po nim Incident Analysis wyszuka powiązane wpisy w Elasticsearch.'
    });
  }
  if (csvUpload) {
    steps.push({
      anchor: 'incident-csv',
      title: 'CSV file',
      description: elasticsearch
        ? 'Kliknij Choose CSV, jeśli masz zapisane logi. Po dołączeniu pliku analiza skorzysta z CSV nawet wtedy, gdy pole Correlation ID jest uzupełnione.'
        : 'Kliknij Choose CSV i wybierz eksport logów zawierający wpisy dotyczące incydentu.'
    });
  }
  return [
    ...steps,
    {
      anchor: 'incident-ai',
      title: 'Model i poziom rozumowania',
      description: 'Do pierwszej analizy możesz użyć ustawień domyślnych. Wybrany model określa dostępne poziomy rozumowania. Wyższy poziom może wydłużyć analizę.'
    },
    {
      anchor: 'incident-description',
      title: 'Problem description',
      description: 'Opcjonalnie opisz objaw i oczekiwane zachowanie. Możesz wpisać „Po zapisaniu kontaktu dane na liście CRM odświeżają się z opóźnieniem”.'
    },
    {
      anchor: 'incident-run',
      title: 'Run analysis',
      description: 'Po uzupełnieniu danych źródłowych uruchom analizę. Jeśli przycisk jest zablokowany przez brak dostępu do Copilota, skonfiguruj PAT w Workspace Settings.'
    },
    {
      anchor: 'incident-import',
      title: 'Import',
      description: 'Wczytaj wcześniejszy wynik Incident Analysis z wyeksportowanego pliku JSON.'
    }
  ];
}
