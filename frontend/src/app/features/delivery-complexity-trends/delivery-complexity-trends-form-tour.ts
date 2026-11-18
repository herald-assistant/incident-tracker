import type { FormTourStep } from '../../core/models/form-tour';

export const DELIVERY_TRENDS_IMPORT_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'delivery-trends-import',
    title: 'Wybierz pliki CSV',
    description: 'Wybierz raporty z kolejnych okresów z jednego rodzaju assessmentu. Zwróć uwagę na zgodność ustawień AI między runami. Pliki są przetwarzane w tej przeglądarce.'
  }
];

export const DELIVERY_TRENDS_FORM_TOUR: readonly FormTourStep[] = [
  ...DELIVERY_TRENDS_IMPORT_TOUR,
  {
    anchor: 'delivery-trends-period',
    title: 'Okres',
    description: 'Wybierz, czy grupować wyniki według dni, tygodni, miesięcy czy kwartałów.'
  },
  {
    anchor: 'delivery-trends-team',
    title: 'Zespół',
    description: 'Ogranicz trend do jednostek dostarczenia przypisanych do wybranego zespołu. Pusta opcja obejmuje wszystkie zespoły.'
  },
  {
    anchor: 'delivery-trends-author',
    title: 'Autor MR',
    description: 'Zawęź widok do jednostek zawierających merge request wybranego autora. Do trendu trafia pełna wartość każdej pasującej jednostki.'
  },
  {
    anchor: 'delivery-trends-issue-types',
    title: 'Typ issue',
    description: 'Opcjonalnie zaznacz typy zgłoszeń. Wystarczy jeden pasujący typ w jednostce. Pusty wybór obejmuje wszystkie typy.'
  },
  {
    anchor: 'delivery-trends-from',
    title: 'Od',
    description: 'Opcjonalnie ustaw początek zakresu dat widocznych w trendzie.'
  },
  {
    anchor: 'delivery-trends-to',
    title: 'Do',
    description: 'Opcjonalnie ustaw ostatni dzień zakresu włącznie. Wyniki przeliczają się po zmianie filtrów.'
  },
  {
    anchor: 'delivery-trends-reset',
    title: 'Wyczyść filtry',
    description: 'Przywróć pełny zakres importowanych danych i domyślne grupowanie okresów.'
  }
];
