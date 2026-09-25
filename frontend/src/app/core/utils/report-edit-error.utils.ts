export function reportEditErrorMessage(error: unknown): string {
  const response = error as { error?: { code?: string; message?: string } } | null;
  switch (response?.error?.code) {
    case 'REPORT_EDIT_STALE':
      return 'Raport zmienił się w czasie edycji. Odśwież wynik i ponów zmianę.';
    case 'REPORT_EDIT_BUSY':
      return 'Trwa inna operacja na tym wyniku. Spróbuj ponownie po jej zakończeniu.';
    case 'REPORT_EDIT_UNAVAILABLE':
      return 'Tego wyniku nie można teraz edytować. Odśwież zadanie.';
    case 'REPORT_EDIT_INVALID':
    case 'REPORT_EDIT_TOO_LARGE':
      return 'Zmiana nie przeszła walidacji. Sprawdź treść i rozmiar sekcji.';
  }
  return response?.error?.message || 'Nie udało się zapisać raportu.';
}
