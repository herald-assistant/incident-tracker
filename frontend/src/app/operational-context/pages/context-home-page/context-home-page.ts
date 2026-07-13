import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';

import {
  ExplainableAggregateDto,
  OpenQuestionDto,
  OperationalContextAiApiPreview,
  OperationalContextAiApiPreviewEndpoint,
  OperationalContextAiSearchPreview,
  OperationalContextCatalogRow,
  OperationalContextEntityDetailDto,
  OperationalContextReadModelProfile,
  OperationalContextSearchResultDto,
  SourceReferenceDto,
  OperationalContextSummaryDto,
  ValidationFindingDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import { AiApiPreviewPanelComponent } from '../../components/ai-api-preview-panel/ai-api-preview-panel';
import {
  ContextCatalogColumn,
  ContextCatalogTableComponent
} from '../../components/context-catalog-table/context-catalog-table';
import { ContextEntityDrawerComponent } from '../../components/context-entity-drawer/context-entity-drawer';
import { WhyPopoverComponent } from '../../components/why-popover/why-popover';
import { copyTextToClipboard } from '../../../core/utils/clipboard.utils';

type ContextTab =
  | 'overview'
  | 'signal-resolver'
  | 'systems'
  | 'repositories'
  | 'code-search-scopes'
  | 'processes'
  | 'integrations'
  | 'bounded-contexts'
  | 'teams'
  | 'glossary'
  | 'handoff'
  | 'validation'
  | 'open-questions';

interface ContextTabItem {
  id: ContextTab;
  label: string;
}

interface ContextTableHeader {
  label: string;
  tooltip: string;
}

interface ContextDataState {
  systems: OperationalContextCatalogRow[];
  repositories: OperationalContextCatalogRow[];
  codeSearchScopes: OperationalContextCatalogRow[];
  processes: OperationalContextCatalogRow[];
  integrations: OperationalContextCatalogRow[];
  boundedContexts: OperationalContextCatalogRow[];
  teams: OperationalContextCatalogRow[];
  glossary: OperationalContextCatalogRow[];
  handoffRules: OperationalContextCatalogRow[];
  validation: ValidationFindingDto[];
  openQuestions: OpenQuestionDto[];
}

const EMPTY_STATE: ContextDataState = {
  systems: [],
  repositories: [],
  codeSearchScopes: [],
  processes: [],
  integrations: [],
  boundedContexts: [],
  teams: [],
  glossary: [],
  handoffRules: [],
  validation: [],
  openQuestions: []
};

const TABS: ContextTabItem[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'signal-resolver', label: 'Signal Resolver' },
  { id: 'systems', label: 'Systems' },
  { id: 'repositories', label: 'Repositories' },
  { id: 'code-search-scopes', label: 'Code Search' },
  { id: 'processes', label: 'Processes' },
  { id: 'integrations', label: 'Integrations' },
  { id: 'bounded-contexts', label: 'Bounded Contexts' },
  { id: 'teams', label: 'Teams' },
  { id: 'glossary', label: 'Glossary' },
  { id: 'handoff', label: 'Handoff' },
  { id: 'validation', label: 'Validation' },
  { id: 'open-questions', label: 'Open Questions' }
];

function column(
  key: string,
  label: string,
  tooltip: string,
  type?: ContextCatalogColumn['type']
): ContextCatalogColumn {
  return type ? { key, label, tooltip, type } : { key, label, tooltip };
}

function header(label: string, tooltip: string): ContextTableHeader {
  return { label, tooltip };
}

const COLUMNS: Record<string, ContextCatalogColumn[]> = {
  systems: [
    column(
      'name',
      'System',
      'Kanoniczna nazwa logicznego systemu w indeksie wiedzy. To do tego bytu AI mapuje sygnaly z katalogu, repozytoriow, procesow i integracji, zeby nie mylic pojedynczego technicznego sygnalu z rzeczywistym systemem analizowanym.'
    ),
    column(
      'kind',
      'Kind',
      'Typ systemu, na przyklad aplikacja wewnetrzna, komponent platformowy albo system zewnetrzny. Pomaga AI dobrac sposob analizy, oczekiwany ownership i to, czy problem dotyczy kodu aplikacji, platformy czy zaleznosci zewnetrznej.'
    ),
    column(
      'owner',
      'Owner',
      'Owner rozstrzygniety z ownershipu systemu. Jezeli katalog nie ma jawnego zespolu, backend pokazuje inferowana strone odpowiedzialna zamiast sklejania ownera z relacji technicznych.',
      'owner'
    ),
    column(
      'repositories',
      'Repositories',
      'Repozytoria skladajace sie na systemowy code-search scope. Pokazuja, ile projektow agent powinien traktowac jako wspolny indeks kodu dla tego systemu, bez przywracania bezposrednich referencji system -> repo.',
      'aggregate'
    ),
    column(
      'relations',
      'Relations',
      'Powiazania systemu z procesami, repozytoriami, integracjami, bounded contextami, terminami i zespolami. Te relacje buduja graf wiedzy, ktory pozwala AI przejsc od technicznego sygnalu do funkcji biznesowej i sasiednich systemow.',
      'aggregate'
    ),
    column(
      'signals',
      'Signals',
      'Sygnaly rozpoznania systemu, takie jak aliasy, use cases, route hints albo terminy katalogowe. To material do szybkiego dopasowania pytania lub evidence do wlasciwego systemu bez utrzymywania inwentarza technicznego.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Wynik resolvera ownershipu dla systemu: wskazuje wlasciciela, sciezke rozstrzygniecia i ograniczenia widocznosci, bez utrzymywania osobnych wskazowek handoffu na poziomie technicznym.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Jakosc wpisu systemu w katalogu: brakujace relacje, niespojne referencje lub problemy modelowania. Im lepszy status, tym pewniej AI moze uzyc systemu jako punktu kotwiczenia analizy.',
      'aggregate'
    )
  ],
  repositories: [
    column(
      'project',
      'Repository',
      'Repozytorium lub projekt GitLaba powiazany z systemem, procesem albo kontekstem. AI uzywa tej kolumny do celowanego wyboru projektu w GitLab tools zamiast szerokiego zgadywania.'
    ),
    column(
      'owner',
      'Owner',
      'Owner repozytorium rozstrzygniety przez powiazany bounded context lub system. Repozytorium nie definiuje osobnego wlasciciela utrzymaniowego w katalogu.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy, dla ktorych repozytorium jest istotne. Dzieki temu AI wie, z jakim logicznym systemem laczyc projekt podczas dalszej analizy.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Bounded contexty powiazane z repozytorium. Pomagaja AI laczyc kod z jezykiem domenowym i poprawniej nazwac obszar funkcjonalny, ktorego dotyczy analiza.',
      'aggregate'
    ),
    column(
      'processes',
      'Processes',
      'Procesy powiazane z repozytorium. Pomagaja przejsc od projektu do use caseu albo flow biznesowego bez utrzymywania szczegolow ukladu kodu.',
      'aggregate'
    ),
    column(
      'integrations',
      'Integrations',
      'Integracje powiazane z repozytorium. Pomagaja ustalic, czy projekt jest czescia handoffu lub komunikacji miedzy systemami.',
      'aggregate'
    ),
    column(
      'codeSearchScopes',
      'Search scopes',
      'Zakresy wyszukiwania kodu, w ktorych repozytorium uczestniczy. Ta kolumna mowi AI, z jakimi innymi projektami nalezy czytac kod razem, zeby nie konczyc analizy na pierwszym trafieniu.',
      'aggregate'
    ),
    column(
      'codeSearchRoles',
      'Scope roles',
      'Rola repozytorium w danym scope. Pomaga AI nadac priorytet odczytom i rozumiec, dlaczego projekt nalezy czytac razem z innymi repozytoriami.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Wynik ownership resolvera dla repozytorium na podstawie jego powiazan z bounded contextem, systemem i zakresem kodu.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Jakosc wpisu repozytorium w katalogu. Bledy w tej kolumnie oznaczaja, ze AI moze gorzej dobrac projekt, scope kodu albo maintainerow do dalszej analizy.',
      'aggregate'
    )
  ],
  'code-search-scopes': [
    column(
      'name',
      'Scope',
      'Nazwa semantycznego zakresu wyszukiwania kodu. Scope nie jest komponentem uruchomieniowym, tylko mapa, ktore repozytoria czytac razem dla danego systemu, procesu, kontekstu albo integracji.'
    ),
    column(
      'scopeType',
      'Type',
      'Rodzaj semantycznego zakresu, na przyklad bounded-context, process, system albo integration. Pomaga szybko ocenic, jaki byt katalogowy jest targetem wspolnego czytania repozytoriow.'
    ),
    column(
      'lifecycleStatus',
      'Lifecycle',
      'Stan aktualnosci scope, na przyklad aktywny, planowany albo historyczny. Pomaga AI nie opierac sie bezrefleksyjnie na przestarzalym zakresie kodu.'
    ),
    column(
      'target',
      'Target',
      'Pojedynczy kanoniczny target semantyczny, dla ktorego scope wskazuje repozytoria do wspolnego przeszukania.',
      'aggregate'
    ),
    column(
      'repositories',
      'Repositories',
      'Repozytoria wchodzace w scope wraz z rola i priorytetem. To klucz do wielorepozytoryjnego grounding: AI powinno czytac te projekty razem, a nie traktowac pierwszego repo jako calego systemu.',
      'aggregate'
    ),
    column(
      'searchBoundary',
      'Search boundary',
      'Jawna granica wyszukiwania dla kazdego repozytorium w scope: cale repozytorium albo konkretne prefiksy sciezek/moduly. Pomaga AI ograniczyc GitLab search do semantycznie istotnych fragmentow repo.',
      'aggregate'
    ),
    column(
      'limitations',
      'Limitations',
      'Jawne ograniczenia scope, na przyklad brak widocznosci partnera albo celowe pominiecie repozytoriow spoza katalogu. To material do visibility limits, nie instrukcja eksploracji kodu.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Ocena kompletnosci i spojnosci scope. Problemy w tej kolumnie oznaczaja ryzyko, ze AI pominie wazne repozytorium albo oprze wniosek na zbyt waskim fragmencie kodu.',
      'aggregate'
    )
  ],
  processes: [
    column(
      'name',
      'Process',
      'Nazwa procesu biznesowego, technicznego, scheduled albo event-driven. Pozwala AI tlumaczyc blad techniczny na funkcje operacyjna, ktora operator rozumie.'
    ),
    column(
      'owner',
      'Owner',
      'Owner procesu rozstrzygniety z bounded contextu albo systemu powiazanego z procesem. Proces nie utrzymuje osobnego ownera na nizszym poziomie szczegolowosci.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy uczestniczace w procesie jako glowne lub wspierajace. Pomagaja AI wskazac, ktory element flow jest dotkniety i gdzie szukac kolejnego dowodu.',
      'aggregate'
    ),
    column(
      'externalSystems',
      'External systems',
      'Zewnetrzne systemy lub partnerzy bioracy udzial w procesie. To wazne dla rozroznienia, czy problem jest lokalny, integracyjny, czy wymaga handoffu poza zespol aplikacji.',
      'aggregate'
    ),
    column(
      'repositories',
      'Repositories',
      'Repozytoria zawierajace implementacje albo konfiguracje procesu. AI moze przejsc z nazwy procesu do konkretnych projektow GitLaba bez szerokiego zgadywania.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Bounded contexty zwiazane z procesem. Pomagaja nazwac affectedBoundedContext i dopasowac lokalny jezyk domeny do evidence.',
      'aggregate'
    ),
    column(
      'steps',
      'Steps',
      'Kroki procesu i ich lokalne sygnaly. Dzieki nim AI moze okreslic, na ktorym etapie przeplywu pojawil sie problem, zamiast opisywac caly proces zbyt ogolnie.',
      'aggregate'
    ),
    column(
      'completionSignals',
      'Completion signals',
      'Sygnaly zakonczenia albo sukcesu procesu, takie jak statusy, zdarzenia lub artefakty. Pomagaja odroznic blad blokujacy od problemu po zakonczeniu glownego flow.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Jakosc modelu procesu w katalogu. Braki tutaj ograniczaja zdolnosc AI do mapowania technicznych symptomow na etap procesu i czytelna rekomendacje operacyjna.',
      'aggregate'
    )
  ],
  integrations: [
    column(
      'name',
      'Integration',
      'Nazwa kontraktu lub polaczenia miedzy systemami. AI uzywa jej do rozpoznania, czy symptom dotyczy komunikacji, zaleznosci zewnetrznej, gatewaya, kolejki albo wymiany danych.'
    ),
    column(
      'sourceSystem',
      'Source',
      'System inicjujacy komunikacje lub lokalna strona integracji. Pomaga ustalic kierunek przeplywu i strone odpowiedzialnosci bez utrzymywania technicznego inventory.'
    ),
    column(
      'targetSystems',
      'Targets',
      'Systemy docelowe, w tym finalne targety za mediatorem lub gatewayem. Dzieki temu AI moze odroznic problem w systemie posrednim od problemu w docelowej usludze.'
    ),
    column(
      'category',
      'Category',
      'Ogolna kategoria integracji lub handoffu. Pomaga odroznic lokalna zaleznosc, partnera, gateway albo zewnetrzny kontrakt bez duplikowania technicznej konfiguracji.'
    ),
    column(
      'integrationStyle',
      'Style',
      'Styl integracji, na przyklad synchroniczny request, event-driven, batch albo gateway. Wplywa na interpretacje symptomow i rekomendowanego handoffu.'
    ),
    column(
      'flowDirection',
      'Direction',
      'Kierunek przeplywu z perspektywy systemu zrodlowego. Pomaga ustalic, czy analiza powinna isc do systemu docelowego, partnera albo procesu nadrzednego.'
    ),
    column(
      'owner',
      'Owner',
      'Glowny owner integracji rozstrzygniety przez bounded contexty lub systemy uczestniczace w granicy komunikacji.',
      'owner'
    ),
    column(
      'partnerOwners',
      'Partner owners',
      'Partnerzy wynikajacy z resolvera ownershipu dla problemu na granicy systemow albo bounded contextow. Gdy katalog nie zna zespolu, backend pokazuje inferowanego wlasciciela strony.',
      'aggregate'
    ),
    column(
      'processes',
      'Processes',
      'Procesy, w ktorych integracja bierze udzial. Pozwalaja AI powiazac blad komunikacji z konkretnym use caseem albo etapem operacyjnym.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Bounded contexty powiazane z integracja. Pomagaja rozumiec semantyke kontraktu i lokalny jezyk uzywany w procesie albo handoffie.',
      'aggregate'
    ),
    column(
      'signals',
      'Signals',
      'Sygnaly rozpoznania integracji utrzymywane jako jezyk katalogowy: aliasy, terminy, kontrakty, role albo wskazowki handoffu. Szczegoly techniczne powinny byc odkrywane przez dedykowane tools.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Wynik resolvera dla granicy integracyjnej: owner lokalny, partnerzy i widoczne ograniczenia katalogu.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Jakosc wpisu integracji. Niespojne strony, braki relacji albo niepelny handoff obnizaja pewnosc AI przy diagnozie problemow komunikacyjnych.',
      'aggregate'
    )
  ],
  'bounded-contexts': [
    column(
      'name',
      'Context',
      'Nazwa bounded contextu, czyli granicy znaczenia w domenie. AI uzywa jej do tlumaczenia sygnalow i pytan na obszar funkcjonalny, ale tylko gdy pasuje to do evidence.'
    ),
    column(
      'owner',
      'Owner',
      'Owner bounded contextu. Ten poziom ma pierwszenstwo przy rozstrzyganiu wlasciciela problemu domenowego lub endpointu powiazanego z kontekstem.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy implementujace lub wykorzystujace ten kontekst. Dzieki temu AI moze sprawdzic, ktore aplikacje sa zwiazane z danym fragmentem funkcji biznesowej.',
      'aggregate'
    ),
    column(
      'terms',
      'Terms',
      'Terminy glossary nalezace do kontekstu. Pomagaja juniorowi i AI zrozumiec lokalny jezyk, aliasy i akronimy widoczne w logach lub kodzie.',
      'aggregate'
    ),
    column(
      'relations',
      'Relations',
      'Relacje z innymi bounded contextami, procesami i integracjami. Te powiazania pokazuja, gdzie kontekst styka sie z innymi czesciami systemu i gdzie moga powstac bledy interpretacji.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Jakosc wpisu bounded contextu. Braki w tej kolumnie oznaczaja, ze AI powinno ostrozniej nazywac affectedBoundedContext i jawnie wskazywac ograniczenia widocznosci.',
      'aggregate'
    )
  ],
  teams: [
    column(
      'name',
      'Team',
      'Nazwa zespolu, strony odpowiedzialnosci lub partnera zewnetrznego. AI uzywa jej do rekomendacji kontaktu tylko wtedy, gdy katalog pokazuje konkretna role zespolu.'
    ),
    column(
      'ownsSystems',
      'Systems',
      'Systemy, za ktore zespol odpowiada operacyjnie, produktowo lub technicznie. Pomaga odroznic ownera systemu od zespolu utrzymujacego powiazane repozytorium.',
      'aggregate'
    ),
    column(
      'ownsRepositories',
      'Repositories',
      'Repozytoria utrzymywane przez zespol. Ta kolumna wspiera handoff przy zmianach kodu, ale sama nie przesadza, ze zespol jest wlascicielem incydentu.',
      'aggregate'
    ),
    column(
      'ownsProcesses',
      'Processes',
      'Procesy, za ktore zespol odpowiada jako owner lub uczestnik. Daje AI kontekst operacyjny, komu przekazac temat, gdy problem dotyczy przeplywu end-to-end.',
      'aggregate'
    ),
    column(
      'ownsContexts',
      'Contexts',
      'Bounded contexty stewardowane przez zespol. Pomaga przy pytaniach o logike funkcjonalna, znaczenie terminow i odpowiedzialnosc za model domeny.',
      'aggregate'
    ),
    column(
      'ownsIntegrations',
      'Integrations',
      'Integracje, w ktorych zespol ma role wlasciciela, partnera lub supportu. To wazne przy awariach na granicy systemow, gdzie potrzebna jest koordynacja kilku stron.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Handoff',
      'Czy dla zespolu sa opisane role, warunki uzycia i wskazowki przekazania. Im lepsza gotowosc, tym bardziej konkretna moze byc rekomendacja AI dla operatora.',
      'aggregate'
    ),
    column(
      'validation',
      'Issues',
      'Problemy jakosciowe zwiazane z wpisem zespolu lub jego referencjami. Pokazuja, gdzie katalog moze wprowadzac niepewnosc w ownershipie i handoffie.',
      'aggregate'
    )
  ],
  glossary: [
    column(
      'term',
      'Term',
      'Lokalny termin domenowy, akronim, alias albo marker techniczny. Pomaga AI i analitykowi zrozumiec, co oznaczaja skroty widoczne w logach, kodzie i rozmowach operacyjnych.'
    ),
    column(
      'category',
      'Category',
      'Kategoria terminu, na przyklad domena, blad, status, proces albo technologia. Ulatwia filtrowanie znaczenia i zmniejsza ryzyko pomylenia podobnych nazw.'
    ),
    column(
      'definition',
      'Definition',
      'Krotkie wyjasnienie terminu w lokalnym jezyku systemu. AI moze dzieki temu pisac odpowiedz zrozumiale dla operatora, ale definicja nie jest dowodem przyczyny awarii.'
    ),
    column(
      'matchSignals',
      'Match signals',
      'Aliasowe sygnaly, po ktorych termin moze zostac rozpoznany w logach, kodzie lub pytaniu uzytkownika. Pomagaja szybciej powiazac surowy tekst z kanonicznym znaczeniem.',
      'aggregate'
    ),
    column(
      'canonicalReferences',
      'Canonical references',
      'Powiazania terminu z systemami, procesami, contextami lub integracjami. Dzieki nim AI wie, gdzie termin ma znaczenie i kiedy powinno go uzyc w analizie.',
      'aggregate'
    )
  ],
  handoff: [
    column(
      'title',
      'Rule',
      'Nazwa reguly handoffu, czyli instrukcji kiedy i jak przekazac temat dalej. Regula pomaga koordynowac prace, ale nie zastepuje evidence technicznego.'
    ),
    column(
      'useWhen',
      'Use when',
      'Warunki, sygnaly lub sytuacje, w ktorych regula ma sens. Dzieki temu AI nie przekazuje sprawy automatycznie po nazwie systemu, tylko sprawdza czy pasuje kontekst incydentu.',
      'aggregate'
    ),
    column(
      'requiredEvidence',
      'Required evidence',
      'Minimalne fakty, ktore trzeba miec przed handoffem, na przyklad scenariusz biznesowy, system docelowy, wlasciciel, logi albo znany kontekst. To chroni odbiorce przed niepelna i nieakcjonowalna eskalacja.',
      'aggregate'
    ),
    column(
      'expectedFirstAction',
      'Expected first action',
      'Pierwsza praktyczna czynnosc oczekiwana po przekazaniu sprawy. Pomaga AI formulowac rekomendacje jako dzialanie do wykonania, a nie ogolna sugestie.'
    )
  ]
};

const OVERVIEW_COLUMNS: ContextTableHeader[] = [
  header(
    'Area',
    'Obszar katalogu, ktory buduje indeks wiedzy: systemy, repozytoria, procesy, integracje, zespoly, glossary albo handoff. Pokazuje, ktora czesc grafu pomaga AI kojarzyc fakty.'
  ),
  header(
    'Indexed facts',
    'Liczba rozpoznanych faktow w danym obszarze. Im wiecej poprawnie opisanych faktow, tym szybciej AI moze przejsc od sygnalu technicznego do wlasciwego systemu, kodu, procesu lub wlasciciela.'
  ),
  header(
    'Readiness',
    'Syntetyczna ocena gotowosci obszaru do uzycia w analizie. Informuje, czy dane sa wystarczajaco kompletne, czy wymagaja review, albo czy AI powinno traktowac je jako ograniczona widocznosc.'
  ),
  header(
    'Why it matters',
    'Wyjasnienie, dlaczego dany obszar jest wazny dla AI-augmented system analysis. Pomaga analitykowi zrozumiec, jaki efekt przynosi utrzymywanie tych danych w indeksie wiedzy.'
  )
];

const SIGNAL_RESOLVER_COLUMNS: ContextTableHeader[] = [
  header(
    'Match',
    'Dopasowana encja katalogu, na przyklad system, repozytorium, integracja, proces lub termin. Pokazuje, jak sygnal z pytania, relacji albo katalogu zostal przypisany do kanonicznego faktu w indeksie wiedzy.'
  ),
  header(
    'Type',
    'Typ dopasowanej encji. Jest wazny, bo AI musi wiedziec, czy wynik traktowac jako system, repozytorium, proces, context, termin czy regule handoffu, a kazdy typ wnosi inny rodzaj kontekstu.'
  ),
  header(
    'Confidence',
    'Pewnosc dopasowania liczona z sygnalow katalogowych. Pomaga ocenic, czy AI moze uzyc wyniku jako mocnego kontekstu, czy powinno potwierdzic szczegoly przez opctx_get_entity lub inne evidence.'
  ),
  header(
    'Why matched',
    'Wyjasnienie, ktore pola i sygnaly spowodowaly dopasowanie. Daje audytowalnosc: analityk widzi, czy wynik wynika z aliasu, repozytorium, terminu, handoffu czy relacji katalogowej.'
  ),
  header(
    'Actions',
    'Akcje pozwalajace otworzyc szczegoly encji. To przejscie od szybkiego wyniku wyszukiwania do pelnego kontekstu: relacji, sygnalow, source refs, handoffu i otwartych pytan.'
  )
];

const VALIDATION_COLUMNS: ContextTableHeader[] = [
  header(
    'Severity',
    'Waga problemu jakosci katalogu. Error moze blokowac zaufanie AI do relacji, warning oznacza ryzyko interpretacji, a info zwykle wskazuje usprawnienie bez krytycznego wplywu.'
  ),
  header(
    'Category',
    'Kategoria walidacji, na przyklad integralnosc referencji, ownership, kompletnosc, jakosc sygnalow albo gotowosc handoffu. Pomaga szybko zrozumiec, jaka czesc indeksu wiedzy wymaga poprawy.'
  ),
  header(
    'Entity',
    'Konkretny byt katalogu, ktorego dotyczy finding. Dzieki temu wiadomo, czy trzeba poprawic system, repozytorium, proces, integracje, zespol, termin czy regule handoffu.'
  ),
  header(
    'Problem',
    'Opis niespojnosci albo braku w katalogu. To informacja, ktora mowi analitykowi, dlaczego AI moze miec gorsze dopasowanie lub mniej pewny wniosek.'
  ),
  header(
    'Suggested fix',
    'Proponowany sposob naprawy danych. Daje praktyczna wskazowke, co uzupelnic, zeby kolejne analizy szybciej trafialy w dobry system, repo, proces lub wlasciciela.'
  ),
  header(
    'Impact',
    'Wplyw problemu na analize. Tlumaczy, jaki blad moze popelnic AI, jesli katalog pozostanie niepoprawny: zly handoff, pominiete repozytorium, zbyt szeroki scope albo niepewny context.'
  ),
  header(
    'Maintenance target',
    'Konkretny plik, path i encja do poprawy w katalogu. Pozwala szybko przejsc od findingu w UI do utrzymania operational-context jako versioned knowledge index.'
  ),
  header(
    'Actions',
    'Szybkie akcje utrzymaniowe: otwarcie encji albo skopiowanie celu poprawki.'
  )
];

const OPEN_QUESTION_COLUMNS: ContextTableHeader[] = [
  header(
    'Question',
    'Otwarte pytanie opisujace brak lub niepewnosc w katalogu. Dla AI jest to jawne ograniczenie widocznosci, a dla analityka lista faktow, ktore warto doprecyzowac.'
  ),
  header(
    'Maintenance target',
    'Plik i encja zwiazane z pytaniem. Pokazuje, gdzie utrzymywany jest brakujacy fragment wiedzy i gdzie nalezy go dopisac po wyjasnieniu.'
  ),
  header(
    'Entity',
    'Encja katalogu zwiazana z pytaniem. Pomaga ocenic, czy luka dotyczy konkretnego systemu, procesu, integracji, repozytorium, zespolu albo ogolnego modelu.'
  ),
  header(
    'Severity',
    'Waga luki w wiedzy. Wyzsza waga oznacza, ze brak moze realnie pogorszyc dopasowanie evidence, wybor tooli, handoff albo opis affected function/process/context.'
  ),
  header(
    'Status',
    'Stan pracy nad pytaniem, na przyklad open lub resolved. Pozwala oddzielic aktywne luki katalogu od spraw juz wyjasnionych, zeby AI i operator nie wracali do nieaktualnych niepewnosci.'
  ),
  header(
    'Actions',
    'Szybkie akcje utrzymaniowe dla pytania: skopiowanie celu poprawki albo przejscie do zwiazanej encji.'
  )
];

@Component({
  selector: 'app-context-home-page',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    MatIconModule,
    MatTooltipModule,
    AiApiPreviewPanelComponent,
    ContextCatalogTableComponent,
    ContextEntityDrawerComponent,
    WhyPopoverComponent
  ],
  templateUrl: './context-home-page.html',
  styleUrl: './context-home-page.scss'
})
export class ContextHomePageComponent {
  private readonly api = inject(OperationalContextApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = TABS;
  readonly overviewColumns = OVERVIEW_COLUMNS;
  readonly signalResolverColumns = SIGNAL_RESOLVER_COLUMNS;
  readonly validationColumns = VALIDATION_COLUMNS;
  readonly openQuestionColumns = OPEN_QUESTION_COLUMNS;
  readonly selectedTab = signal<ContextTab>('overview');
  readonly summary = signal<OperationalContextSummaryDto | null>(null);
  readonly data = signal<ContextDataState>(EMPTY_STATE);
  readonly isLoading = signal(true);
  readonly errorMessage = signal('');
  readonly detail = signal<OperationalContextEntityDetailDto | null>(null);
  readonly detailError = signal('');
  readonly aiApiPreview = signal<OperationalContextAiApiPreview | null>(null);
  readonly aiApiPreviewLoading = signal(false);
  readonly aiApiPreviewError = signal('');
  readonly aiApiPreviewProfile = signal<OperationalContextReadModelProfile>('default');
  readonly searchResults = signal<OperationalContextSearchResultDto[]>([]);
  readonly searchAiApiPreview = signal<OperationalContextAiSearchPreview | null>(null);
  readonly searchAiApiPreviewLoading = signal(false);
  readonly searchAiApiPreviewError = signal('');
  readonly searchAiApiPreviewProfile = signal<OperationalContextReadModelProfile>('default');
  private readonly searchAiApiPreviewQuery = signal('');
  private readonly selectedEntityTarget = signal<{ type: string; id: string } | null>(null);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly localFilterControl = new FormControl('', { nonNullable: true });
  readonly onlyWarningsControl = new FormControl(false, { nonNullable: true });
  readonly onlyMissingOwnerControl = new FormControl(false, { nonNullable: true });
  readonly onlyOpenQuestionsControl = new FormControl(false, { nonNullable: true });
  readonly validationSeverityControl = new FormControl('', { nonNullable: true });
  readonly validationCategoryControl = new FormControl('', { nonNullable: true });
  readonly validationEntityTypeControl = new FormControl('', { nonNullable: true });
  readonly validationSourceFileControl = new FormControl('', { nonNullable: true });
  readonly questionSeverityControl = new FormControl('', { nonNullable: true });
  readonly questionEntityTypeControl = new FormControl('', { nonNullable: true });
  readonly questionSourceFileControl = new FormControl('', { nonNullable: true });
  readonly questionStatusControl = new FormControl('', { nonNullable: true });
  readonly localFilter = signal('');
  readonly onlyWarnings = signal(false);
  readonly onlyMissingOwner = signal(false);
  readonly onlyOpenQuestions = signal(false);
  readonly validationSeverity = signal('');
  readonly validationCategory = signal('');
  readonly validationEntityType = signal('');
  readonly validationSourceFile = signal('');
  readonly questionSeverity = signal('');
  readonly questionEntityType = signal('');
  readonly questionSourceFile = signal('');
  readonly questionStatus = signal('');
  readonly copiedMaintenanceTarget = signal('');

  readonly currentColumns = computed(() => COLUMNS[this.selectedTab()] ?? []);
  readonly currentRows = computed(() => this.filteredRows(this.selectedTab()));
  readonly tableColumnCount = computed(() => Math.max(this.currentColumns().length, 1));
  readonly searchAiApiPreviewEndpoints = computed<OperationalContextAiApiPreviewEndpoint[]>(() => {
    const preview = this.searchAiApiPreview();
    return preview
      ? [{
          key: 'search',
          label: 'Search',
          url: preview.url,
          payload: preview.payload,
          error: preview.error
        }]
      : [];
  });
  readonly validationSeverityOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.severity))
  );
  readonly validationCategoryOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.category))
  );
  readonly validationEntityTypeOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.entityType))
  );
  readonly validationSourceFileOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => this.firstSourceRef(finding)?.file))
  );
  readonly questionSeverityOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.severity))
  );
  readonly questionEntityTypeOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.entityType))
  );
  readonly questionSourceFileOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.sourceFile))
  );
  readonly questionStatusOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.status))
  );
  readonly statusLabel = computed(() => this.summary()?.catalogStatus || 'loading');
  readonly statusText = computed(() => this.formatStatus(this.statusLabel()));
  readonly isIncomplete = computed(() => {
    const summary = this.summary();
    const status = summary?.catalogStatus;
    const indexedEntities = summary
      ? summary.systems
        + summary.repositories
        + summary.codeSearchScopes
        + summary.processes
        + summary.integrations
        + summary.boundedContexts
        + summary.teams
        + summary.glossaryTerms
        + summary.handoffRules
      : 0;
    return status === 'empty' || status === 'partial' || indexedEntities === 0;
  });

  constructor() {
    this.localFilterControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.localFilter.set(value));
    this.onlyWarningsControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyWarnings.set(value));
    this.onlyMissingOwnerControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyMissingOwner.set(value));
    this.onlyOpenQuestionsControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyOpenQuestions.set(value));
    this.validationSeverityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationSeverity.set(value));
    this.validationCategoryControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationCategory.set(value));
    this.validationEntityTypeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationEntityType.set(value));
    this.validationSourceFileControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationSourceFile.set(value));
    this.questionSeverityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionSeverity.set(value));
    this.questionEntityTypeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionEntityType.set(value));
    this.questionSourceFileControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionSourceFile.set(value));
    this.questionStatusControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionStatus.set(value));
    this.loadCatalogue();
  }

  selectTab(tab: ContextTab): void {
    this.selectedTab.set(tab);
  }

  runSignalSearch(event?: Event): void {
    event?.preventDefault();
    const query = this.searchControl.value.trim();
    if (!query) {
      this.searchResults.set([]);
      this.resetSearchAiApiPreview();
      return;
    }

    this.searchAiApiPreviewProfile.set('default');
    this.searchAiApiPreviewQuery.set(query);
    this.loadSearchAiApiPreview('default');
    this.api
      .search(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => this.searchResults.set(results),
        error: () => this.errorMessage.set('Could not search operational context.')
      });
  }

  loadSearchAiApiPreview(profile: OperationalContextReadModelProfile): void {
    const query = this.searchAiApiPreviewQuery() || this.searchControl.value.trim();
    if (!query) {
      this.resetSearchAiApiPreview();
      return;
    }

    this.searchAiApiPreviewQuery.set(query);
    this.searchAiApiPreviewProfile.set(profile);
    this.searchAiApiPreviewLoading.set(true);
    this.searchAiApiPreviewError.set('');
    this.api
      .getProfiledSearch(query, profile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (payload) => {
          this.searchAiApiPreview.set({
            query,
            profile,
            url: this.api.profiledSearchUrl(query, profile),
            payload,
            error: null
          });
          this.searchAiApiPreviewLoading.set(false);
        },
        error: () => {
          this.searchAiApiPreview.set({
            query,
            profile,
            url: this.api.profiledSearchUrl(query, profile),
            payload: null,
            error: 'Could not load search AI API preview.'
          });
          this.searchAiApiPreviewError.set('Could not load search AI API preview.');
          this.searchAiApiPreviewLoading.set(false);
        }
      });
  }

  openRow(row: Record<string, unknown>): void {
    const id = this.rowId(row);
    const type = this.entityTypeForTab(this.selectedTab());
    if (!id || !type) {
      return;
    }
    this.openEntity({ type, id });
  }

  openEntity(target: { type: string; id: string }): void {
    if (!target.type || !target.id) {
      return;
    }
    if (target.type === 'validation') {
      this.selectTab('validation');
      return;
    }
    if (target.type === 'open-question') {
      this.selectTab('open-questions');
      return;
    }
    if (
      ![
        'system',
        'repository',
        'code-search-scope',
        'process',
        'integration',
        'bounded-context',
        'team',
        'glossary-term',
        'handoff-rule'
      ].includes(target.type)
    ) {
      return;
    }

    this.detailError.set('');
    this.aiApiPreview.set(null);
    this.aiApiPreviewError.set('');
    this.aiApiPreviewLoading.set(true);
    this.aiApiPreviewProfile.set('default');
    this.selectedEntityTarget.set(target);
    forkJoin({
      detail: this.api.getEntity(target.type, target.id),
      aiApiPreview: this.aiApiPreviewFor(target, 'default')
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ detail, aiApiPreview }) => {
          this.detail.set(detail);
          this.aiApiPreview.set(aiApiPreview);
          this.aiApiPreviewLoading.set(false);
        },
        error: () => {
          this.aiApiPreviewLoading.set(false);
          this.detailError.set(`Could not load ${target.type}/${target.id}.`);
        }
      });
  }

  loadAiApiPreview(profile: OperationalContextReadModelProfile): void {
    const target = this.selectedEntityTarget();
    if (!target) {
      return;
    }

    this.aiApiPreviewProfile.set(profile);
    this.aiApiPreviewLoading.set(true);
    this.aiApiPreviewError.set('');
    this.aiApiPreviewFor(target, profile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preview) => {
          this.aiApiPreview.set(preview);
          this.aiApiPreviewLoading.set(false);
        },
        error: () => {
          this.aiApiPreview.set(null);
          this.aiApiPreviewError.set('Could not load AI API preview.');
          this.aiApiPreviewLoading.set(false);
        }
      });
  }

  openSearchResult(result: OperationalContextSearchResultDto): void {
    this.openEntity({ type: result.type, id: result.id });
  }

  closeDrawer(): void {
    this.detail.set(null);
    this.detailError.set('');
    this.aiApiPreview.set(null);
    this.aiApiPreviewLoading.set(false);
    this.aiApiPreviewError.set('');
    this.aiApiPreviewProfile.set('default');
    this.selectedEntityTarget.set(null);
  }

  protected validationRows(): ValidationFindingDto[] {
    const query = normalize(this.localFilter());
    return this.data()
      .validation.filter((finding) => {
        if (query && !normalize(JSON.stringify(finding)).includes(query)) {
          return false;
        }
        if (this.onlyWarnings() && !this.isWarningSeverity(finding.severity)) {
          return false;
        }
        if (this.validationSeverity() && !this.exactMatch(finding.severity, this.validationSeverity())) {
          return false;
        }
        if (this.validationCategory() && !this.exactMatch(finding.category, this.validationCategory())) {
          return false;
        }
        if (this.validationEntityType() && !this.exactMatch(finding.entityType, this.validationEntityType())) {
          return false;
        }
        if (
          this.validationSourceFile()
          && !this.exactMatch(this.firstSourceRef(finding)?.file, this.validationSourceFile())
        ) {
          return false;
        }
        return true;
      });
  }

  protected openQuestionRows(): OpenQuestionDto[] {
    const query = normalize(this.localFilter());
    return this.data()
      .openQuestions.filter((question) => {
        if (query && !normalize(JSON.stringify(question)).includes(query)) {
          return false;
        }
        if (this.onlyWarnings() && !this.isWarningSeverity(question.severity)) {
          return false;
        }
        if (this.questionSeverity() && !this.exactMatch(question.severity, this.questionSeverity())) {
          return false;
        }
        if (this.questionEntityType() && !this.exactMatch(question.entityType, this.questionEntityType())) {
          return false;
        }
        if (this.questionSourceFile() && !this.exactMatch(question.sourceFile, this.questionSourceFile())) {
          return false;
        }
        if (this.questionStatus() && !this.exactMatch(question.status, this.questionStatus())) {
          return false;
        }
        return true;
      });
  }

  protected rowId(row: Record<string, unknown>): string {
    return String(row['id'] || '');
  }

  protected aggregate(row: Record<string, unknown>, key: string): ExplainableAggregateDto | null {
    const value = row[key] as ExplainableAggregateDto | null;
    return value && typeof value === 'object' && 'count' in value ? value : null;
  }

  protected validationStatusClass(severity: string): string {
    return `validation-severity validation-severity--${severity || 'info'}`;
  }

  protected firstSourceRef(finding: ValidationFindingDto): SourceReferenceDto | null {
    return finding.sourceRefs?.[0] ?? null;
  }

  protected validationMaintenanceTarget(finding: ValidationFindingDto): string {
    const source = this.firstSourceRef(finding);
    const sourceLabel = [source?.file, source?.path].filter(Boolean).join(' ');
    return [
      sourceLabel || 'unknown source',
      `${finding.entityType}/${finding.entityId}`,
      finding.category
    ].join(' | ');
  }

  protected openQuestionMaintenanceTarget(question: OpenQuestionDto): string {
    return [
      question.sourceFile || 'unknown source',
      this.openQuestionEntityLabel(question),
      question.status
    ].filter(Boolean).join(' | ');
  }

  protected openQuestionEntityLabel(question: OpenQuestionDto): string {
    return question.entityId
      ? `${question.entityType}/${question.entityId}`
      : question.entityType;
  }

  protected async copyMaintenanceTarget(
    event: Event,
    target: string,
    key: string
  ): Promise<void> {
    event.stopPropagation();
    const copied = await copyTextToClipboard(target);
    this.copiedMaintenanceTarget.set(copied ? key : '');
  }

  protected healthReadinessLabel(severity: string): string {
    switch (String(severity || '').toLowerCase()) {
      case 'ok':
        return 'Ready';
      case 'warning':
        return 'Review';
      case 'error':
        return 'Needs fix';
      case 'unknown':
        return 'Not mapped';
      default:
        return severity || 'Unknown';
    }
  }

  protected selectedTabLabel(): string {
    return this.tabs.find((tab) => tab.id === this.selectedTab())?.label ?? '';
  }

  protected supportsWarningFilter(): boolean {
    return !['glossary', 'handoff'].includes(this.selectedTab());
  }

  protected supportsMissingOwnerFilter(): boolean {
    return ['systems', 'repositories', 'processes', 'integrations', 'bounded-contexts'].includes(this.selectedTab());
  }

  protected supportsOpenQuestionsFilter(): boolean {
    return this.selectedTab() === 'systems';
  }

  private loadCatalogue(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    forkJoin({
      summary: this.api.getSummary(),
      systems: this.api.getSystems(),
      repositories: this.api.getRepositories(),
      codeSearchScopes: this.api.getCodeSearchScopes(),
      processes: this.api.getProcesses(),
      integrations: this.api.getIntegrations(),
      boundedContexts: this.api.getBoundedContexts(),
      teams: this.api.getTeams(),
      glossary: this.api.getGlossary(),
      handoffRules: this.api.getHandoffRules(),
      validation: this.api.getValidation(),
      openQuestions: this.api.getOpenQuestions()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.summary.set(response.summary);
          this.data.set({
            systems: response.systems,
            repositories: response.repositories,
            codeSearchScopes: response.codeSearchScopes,
            processes: response.processes,
            integrations: response.integrations,
            boundedContexts: response.boundedContexts,
            teams: response.teams,
            glossary: response.glossary,
            handoffRules: response.handoffRules,
            validation: response.validation,
            openQuestions: response.openQuestions
          });
          this.isLoading.set(false);
        },
        error: () => {
          this.errorMessage.set('Could not load operational context catalogue.');
          this.isLoading.set(false);
        }
      });
  }

  private supportsReadModels(type: string): boolean {
    return [
      'system',
      'repository',
      'code-search-scope',
      'process',
      'integration',
      'bounded-context'
    ].includes(type);
  }

  private resetSearchAiApiPreview(): void {
    this.searchAiApiPreview.set(null);
    this.searchAiApiPreviewLoading.set(false);
    this.searchAiApiPreviewError.set('');
    this.searchAiApiPreviewProfile.set('default');
    this.searchAiApiPreviewQuery.set('');
  }

  private uniqueValues(values: Array<string | null | undefined>): string[] {
    return Array.from(
      new Set(
        values
          .map((value) => String(value || '').trim())
          .filter(Boolean)
      )
    ).sort((left, right) => left.localeCompare(right));
  }

  private exactMatch(left: string | null | undefined, right: string): boolean {
    return normalize(left || '') === normalize(right);
  }

  private aiApiPreviewFor(
    target: { type: string; id: string },
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextAiApiPreview> {
    const requests = this.api.getAiApiPreviewRequests(
      target.type,
      target.id,
      profile,
      this.supportsReadModels(target.type)
    );

    return forkJoin(
      requests.map((request) =>
        request.request.pipe(
          map(
            (payload): OperationalContextAiApiPreviewEndpoint => ({
              key: request.key,
              label: request.label,
              url: request.url,
              payload,
              error: null
            })
          ),
          catchError(() =>
            of({
              key: request.key,
              label: request.label,
              url: request.url,
              payload: null,
              error: `Could not load ${request.label}.`
            } satisfies OperationalContextAiApiPreviewEndpoint)
          )
        )
      )
    ).pipe(map((endpoints) => ({ profile, endpoints })));
  }

  private filteredRows(tab: ContextTab): Array<Record<string, unknown>> {
    const rows = this.rowsForTab(tab).map((row) => row as unknown as Record<string, unknown>);
    const query = normalize(this.localFilter());
    return rows.filter((row) => {
      if (query && !normalize(JSON.stringify(row)).includes(query)) {
        return false;
      }
      if (this.supportsWarningFilter() && this.onlyWarnings() && !this.rowHasWarning(row)) {
        return false;
      }
      if (this.supportsMissingOwnerFilter() && this.onlyMissingOwner() && !this.rowMissingOwner(row)) {
        return false;
      }
      if (this.supportsOpenQuestionsFilter() && this.onlyOpenQuestions() && !this.rowHasOpenQuestions(row)) {
        return false;
      }
      return true;
    });
  }

  private rowsForTab(tab: ContextTab): OperationalContextCatalogRow[] {
    const data = this.data();
    switch (tab) {
      case 'systems':
        return data.systems;
      case 'repositories':
        return data.repositories;
      case 'code-search-scopes':
        return data.codeSearchScopes;
      case 'processes':
        return data.processes;
      case 'integrations':
        return data.integrations;
      case 'bounded-contexts':
        return data.boundedContexts;
      case 'teams':
        return data.teams;
      case 'glossary':
        return data.glossary;
      case 'handoff':
        return data.handoffRules;
      default:
        return [];
    }
  }

  protected entityTypeForTab(tab: ContextTab): string {
    switch (tab) {
      case 'systems':
        return 'system';
      case 'repositories':
        return 'repository';
      case 'code-search-scopes':
        return 'code-search-scope';
      case 'processes':
        return 'process';
      case 'integrations':
        return 'integration';
      case 'bounded-contexts':
        return 'bounded-context';
      case 'teams':
        return 'team';
      case 'glossary':
        return 'glossary-term';
      case 'handoff':
        return 'handoff-rule';
      default:
        return '';
    }
  }

  private rowHasWarning(row: Record<string, unknown>): boolean {
    return Object.values(row).some(
      (value) =>
        value &&
        typeof value === 'object' &&
        'severity' in value &&
        this.isWarningSeverity(String((value as { severity?: string }).severity))
    );
  }

  private rowMissingOwner(row: Record<string, unknown>): boolean {
    const owner = row['owner'] as { label?: string; value?: string } | undefined;
    return Boolean(owner && (!owner.value || owner.label === 'Missing owner'));
  }

  private rowHasOpenQuestions(row: Record<string, unknown>): boolean {
    const openQuestions = row['openQuestions'] as { count?: number } | undefined;
    return Number(openQuestions?.count || 0) > 0;
  }

  private formatStatus(status: string): string {
    switch (status) {
      case 'ok':
        return 'OK';
      case 'warning':
        return 'Review';
      case 'error':
        return 'Needs fix';
      case 'ready':
        return 'Ready';
      case 'partial':
        return 'Partial';
      case 'empty':
        return 'Empty';
      default:
        return status || 'Loading';
    }
  }

  private isWarningSeverity(severity: string): boolean {
    return ['warning', 'error'].includes(String(severity || '').toLowerCase());
  }
}

function normalize(value: string): string {
  return value.trim().toLowerCase();
}
