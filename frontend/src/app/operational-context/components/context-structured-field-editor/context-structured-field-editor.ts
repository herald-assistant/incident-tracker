import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  OperationalContextReferenceOption,
  OperationalContextReferenceOptions,
  OperationalContextWritableType
} from '../../models/operational-context-maintenance.models';
import { OperationalContextFormField } from '../context-entity-editor-drawer/operational-context-form-adapter';

type JsonObject = Record<string, unknown>;
type ParticipantGroup = 'source' | 'targets' | 'intermediaries' | 'finalTargets';
type ProcessSystemRole = 'primarySystems' | 'supportingSystems' | 'externalSystems' | 'platformComponents';
type SignalStrength = 'exact' | 'strong' | 'medium' | 'weak';

interface MatchSignalRow {
  strength: SignalStrength;
  key: string;
  values: string[];
}

interface StructuredListGroup {
  key: string;
  label: string;
  tooltip: string;
}

interface ReferenceGroup {
  key: string;
  label: string;
  type: OperationalContextWritableType;
}

interface ParticipantRow {
  group: ParticipantGroup;
  index: number;
  label: string;
  removable: boolean;
}

const REFERENCE_GROUPS: Record<string, ReferenceGroup[]> = {
  system: [
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('teams', 'Teams', 'team'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  repository: [
    group('systems', 'Systems', 'system'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  process: [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  integration: [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  'bounded-context': [
    group('systems', 'Systems', 'system'),
    group('processes', 'Processes', 'process'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('teams', 'Teams', 'team'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  'handoff-rule': [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term')
  ]
};

const PROCESS_SYSTEM_ROLES: Array<{ key: ProcessSystemRole; label: string }> = [
  { key: 'primarySystems', label: 'Primary systems' },
  { key: 'supportingSystems', label: 'Supporting systems' },
  { key: 'externalSystems', label: 'External systems' },
  { key: 'platformComponents', label: 'Platform components' }
];

const PROCESS_STEP_REFERENCE_GROUPS: ReferenceGroup[] = [
  group('systems', 'Systems', 'system'),
  group('repositories', 'Repositories', 'repository'),
  group('boundedContexts', 'Bounded contexts', 'bounded-context'),
  group('integrations', 'Integrations', 'integration'),
  group('terms', 'Glossary terms', 'glossary-term'),
  group('handoffRules', 'Handoff rules', 'handoff-rule')
];

const SIGNAL_STRENGTHS: Array<{ key: SignalStrength; label: string }> = [
  { key: 'exact', label: 'Exact' },
  { key: 'strong', label: 'Strong' },
  { key: 'medium', label: 'Medium' },
  { key: 'weak', label: 'Weak' }
];

const SIGNAL_KEY_SUGGESTIONS: Partial<Record<OperationalContextWritableType, string[]>> = {
  system: ['serviceNames', 'deploymentNames', 'applicationNames', 'routes', 'endpoints', 'projectNames', 'projectPaths', 'businessTerms', 'aliases', 'configKeys'],
  repository: ['projectPaths', 'projectNames', 'buildCoordinates', 'groupIds', 'artifactIds', 'configKeys', 'aliases'],
  process: ['businessTerms', 'routes', 'operationNames', 'eventNames', 'exchanges', 'routingKeys', 'aliases'],
  integration: ['routes', 'endpoints', 'operationNames', 'bindings', 'exchanges', 'routingKeys', 'consumerGroups', 'hostPatterns', 'configKeys', 'artifactIds', 'businessTerms'],
  'bounded-context': ['businessTerms', 'routes', 'packagePrefixes', 'classNames', 'dbTables', 'schedulerNames', 'exchanges', 'routingKeys', 'consumerGroups', 'configKeys', 'artifactIds'],
  team: ['teamNames', 'aliases', 'emailAliases', 'collaborationIds'],
  'glossary-term': ['phrases', 'aliases', 'fieldNames', 'businessTerms', 'word', 'phrase', 'field', 'class', 'const', 'enum', 'variable', 'value', 'endpoint', 'table', 'system', 'integration', 'bounded-context']
};

const RELATION_TARGET_TYPES: ReferenceGroup[] = [
  group('system', 'System', 'system'),
  group('repository', 'Repository', 'repository'),
  group('code-search-scope', 'Code-search scope', 'code-search-scope'),
  group('process', 'Process', 'process'),
  group('integration', 'Integration', 'integration'),
  group('bounded-context', 'Bounded context', 'bounded-context'),
  group('team', 'Team', 'team'),
  group('glossary-term', 'Glossary term', 'glossary-term'),
  group('handoff-rule', 'Handoff rule', 'handoff-rule')
];

const DATA_ARTIFACT_GROUPS: StructuredListGroup[] = [
  { key: 'primaryObjects', label: 'Primary business objects', tooltip: 'dataPrimaryObjects' },
  { key: 'inputArtifacts', label: 'Input artifacts', tooltip: 'dataInputArtifacts' },
  { key: 'outputArtifacts', label: 'Output artifacts', tooltip: 'dataOutputArtifacts' },
  { key: 'persistedEntities', label: 'Persisted entities', tooltip: 'dataPersistedEntities' },
  { key: 'readModels', label: 'Read models', tooltip: 'dataReadModels' },
  { key: 'auditArtifacts', label: 'Audit artifacts', tooltip: 'dataAuditArtifacts' },
  { key: 'notes', label: 'Notes', tooltip: 'dataArtifactNotes' }
];

const PROCESS_BOUNDARY_LIST_GROUPS: StructuredListGroup[] = [
  { key: 'startsWhen', label: 'Starts when', tooltip: 'boundaryStartsWhen' },
  { key: 'endsWhen', label: 'Ends when', tooltip: 'boundaryEndsWhen' },
  { key: 'includes', label: 'Includes', tooltip: 'boundaryIncludes' },
  { key: 'excludes', label: 'Excludes', tooltip: 'boundaryExcludes' },
  { key: 'assumptions', label: 'Assumptions', tooltip: 'boundaryAssumptions' }
];

const PROCESS_LIFECYCLE_LIST_GROUPS: StructuredListGroup[] = [
  { key: 'entryCriteria', label: 'Entry criteria', tooltip: 'lifecycleEntryCriteria' },
  { key: 'statuses', label: 'Statuses', tooltip: 'lifecycleStatuses' },
  { key: 'terminalStates', label: 'Terminal states', tooltip: 'lifecycleTerminalStates' }
];

const PROCESS_LIFECYCLE_OUTCOME_GROUPS: StructuredListGroup[] = [
  { key: 'successOutcomes', label: 'Success outcomes', tooltip: 'lifecycleSuccessOutcomes' },
  { key: 'partialOutcomes', label: 'Partial outcomes', tooltip: 'lifecyclePartialOutcomes' },
  { key: 'failedOutcomes', label: 'Failed outcomes', tooltip: 'lifecycleFailedOutcomes' },
  { key: 'cancellationOutcomes', label: 'Cancellation outcomes', tooltip: 'lifecycleCancellationOutcomes' }
];

const COMPLETION_SIGNAL_GROUPS: StructuredListGroup[] = [
  { key: 'successful', label: 'Successful', tooltip: 'completionSuccessful' },
  { key: 'partial', label: 'Partial', tooltip: 'completionPartial' },
  { key: 'failed', label: 'Failed', tooltip: 'completionFailed' },
  { key: 'cancelled', label: 'Cancelled', tooltip: 'completionCancelled' }
];

const BOUNDED_SCOPE_GROUPS: StructuredListGroup[] = [
  { key: 'includes', label: 'Includes', tooltip: 'boundedScopeIncludes' },
  { key: 'excludes', label: 'Excludes', tooltip: 'boundedScopeExcludes' },
  { key: 'businessCapabilities', label: 'Business capabilities', tooltip: 'boundedScopeCapabilities' },
  { key: 'coreEntities', label: 'Core entities', tooltip: 'boundedScopeEntities' },
  { key: 'keyDecisions', label: 'Key decisions', tooltip: 'boundedScopeDecisions' }
];

const BOUNDED_SEMANTIC_GROUPS: StructuredListGroup[] = [
  { key: 'coreConcepts', label: 'Core concepts', tooltip: 'boundedCoreConcepts' },
  { key: 'localConcepts', label: 'Local concepts', tooltip: 'boundedLocalConcepts' },
  { key: 'canonicalEntities', label: 'Canonical entities', tooltip: 'boundedCanonicalEntities' },
  { key: 'commands', label: 'Commands', tooltip: 'boundedCommands' },
  { key: 'events', label: 'Events', tooltip: 'boundedEvents' },
  { key: 'invariants', label: 'Invariants', tooltip: 'boundedInvariants' },
  { key: 'ownsLanguage', label: 'Owns language', tooltip: 'boundedOwnsLanguage' },
  { key: 'doesNotOwn', label: 'Does not own', tooltip: 'boundedDoesNotOwn' }
];

const PROCESS_TRIGGER_TYPES = ['api', 'event', 'command'];

const SOURCE_COVERAGE_STATUSES = ['complete', 'partial', 'unknown'];
const GAP_SEVERITIES = ['error', 'warning', 'info'];
const GAP_STATUSES = ['open', 'resolved'];

const FIELD_TOOLTIPS: Record<string, string> = {
  ownershipStatus: 'Wybierz explicit, gdy odpowiedzialność jest potwierdzona, albo unknown, gdy pozostaje nieustalona. Jawny właściciel obszaru domenowego ma pierwszeństwo przed właścicielem systemu.',
  ownerTeamIds: 'Wybierz istniejące zespoły odpowiedzialne za ten wpis. Wybór nie zmienia danych zespołu.',
  ownerLabel: 'Wpisz nazwę strony odpowiedzialnej tylko wtedy, gdy nie ma jej na liście zespołów.',
  confidence: 'Określ pewność informacji: high, medium albo low. Nie jest to ocena bieżącego działania systemu.',
  source: 'Podaj trwałe źródło potwierdzające odpowiedzialność, np. dokumentację obszaru CRM.',
  notes: 'Dodaj trwałe wyjaśnienia, po jednym w wierszu. Same notatki nie przypisują odpowiedzialności.',
  systemExternalOwner: 'Wpisz nazwę zewnętrznej organizacji tylko wtedy, gdy to ona obsługuje cały system.',
  runtimeConfigurationDirectory: 'Podaj ścieżkę do katalogu konfiguracji względem głównego katalogu repozytorium. Służy do porównywania konfiguracji; nazwy usług wpisuj w sygnałach rozpoznania.',
  repositoryEvidenceSourceRef: 'Podaj trwałą ścieżkę lub nazwę dokumentu potwierdzającego powiązanie repozytorium. Aplikacja nie odczyta go automatycznie.',
  repositoryEvidenceType: 'Określ rodzaj źródła, np. definicja budowania, dokumentacja repozytorium lub decyzja architektoniczna.',
  repositoryEvidenceNote: 'Krótko opisz, jaki fakt potwierdza źródło. Nie wklejaj danych klientów ani pełnej treści dokumentu.',
  repositoryAnswerWhenMentioned: 'Wpisz zwroty, przy których warto sprawdzić to repozytorium, po jednym w wierszu. Nie zmienia to uprawnień do kodu.',
  repositoryDisambiguateFrom: 'Wymień podobne repozytoria lub pojęcia, z którymi nie należy mylić tego projektu.',
  boundedLocalLanguage: 'Wyjaśnij miejscowe znaczenie terminu w tym obszarze, po jednym pełnym zdaniu w wierszu.',
  boundedScopeIncludes: 'Wpisz odpowiedzialności należące do tego obszaru domenowego, po jednej w wierszu.',
  boundedScopeExcludes: 'Wpisz sąsiednie odpowiedzialności, które nie należą do tego obszaru.',
  boundedScopeCapabilities: 'Wymień trwałe możliwości biznesowe zapewniane przez ten obszar, po jednej w wierszu.',
  boundedScopeEntities: 'Wymień rodzaje głównych obiektów biznesowych tego obszaru. Nie wpisuj rzeczywistych rekordów ani identyfikatorów klientów.',
  boundedScopeDecisions: 'Wymień decyzje biznesowe podejmowane w tym obszarze. Ten opis nie zmienia reguł aplikacji.',
  boundedCoreConcepts: 'Wymień pojęcia niezbędne do zrozumienia tego obszaru, po jednym w wierszu.',
  boundedLocalConcepts: 'Wymień terminy, które mają w tym obszarze szczególne znaczenie.',
  boundedCanonicalEntities: 'Wymień główne nazwy obiektów domenowych używane w tym obszarze.',
  boundedCommands: 'Wymień nazwy działań biznesowych przyjmowanych przez ten obszar. Katalog ich nie wykonuje.',
  boundedEvents: 'Wymień nazwy zdarzeń domenowych związanych z tym obszarem. Obecność na liście nie potwierdza, że zdarzenie wystąpiło.',
  boundedInvariants: 'Wpisz reguły, które muszą być spełnione w tym obszarze. Katalog nie sprawdza ich podczas działania systemu.',
  boundedOwnsLanguage: 'Wymień zwroty, których znaczenie ustala ten obszar domenowy.',
  boundedDoesNotOwn: 'Wymień pojęcia lub odpowiedzialności należące do innego obszaru.',
  boundedEvidenceSourceRef: 'Podaj trwałą ścieżkę lub nazwę dokumentu potwierdzającego opis obszaru. Aplikacja nie odczyta go automatycznie.',
  boundedEvidenceType: 'Określ rodzaj źródła, np. dokumentacja domeny, przegląd słownika lub opis kontraktu.',
  boundedEvidenceNote: 'Krótko opisz fakt potwierdzany przez źródło. Nie wklejaj danych klientów ani pełnej treści dokumentu.',
  boundedAnswerWhenMentioned: 'Wpisz zwroty, które powinny naprowadzać na ten obszar, po jednym w wierszu.',
  boundedDisambiguateFrom: 'Wymień podobne obszary lub pojęcia, z którymi nie należy mylić tego obszaru.',
  boundedUsefulSearchKeywords: 'Dodaj trwałe, niesekretne słowa pomagające odnaleźć ten obszar. Nie zmieniają one dostępu do kodu.',
  boundedExplanationStyle: 'Podaj krótką wskazówkę, jak wyjaśniać rolę tego obszaru. Nie zmienia ona faktów ani odpowiedzialności.',
  reference: 'Wybierz istniejącą pozycję katalogu. Powiązanie pojawi się w szczegółach i może zablokować jej usunięcie.',
  targetType: 'Wybierz system albo obszar domenowy, którego kod obejmuje ten zakres.',
  targetId: 'Wybierz istniejący system lub obszar domenowy. Ten wybór łączy go z podanymi repozytoriami.',
  repoId: 'Wybierz repozytorium, w którym należy szukać kodu tego obszaru.',
  role: 'Wyjaśnij rolę repozytorium w zakresie. Wartość primary oznacza główne źródło kodu.',
  priority: 'Podaj dodatnią liczbę. Wartość 1 oznacza repozytorium sprawdzane jako pierwsze.',
  searchMode: 'Wybierz przeszukiwanie całego repozytorium albo tylko wskazanych ścieżek.',
  pathPrefixes: 'Przy wyszukiwaniu według ścieżek podaj katalogi względem głównego katalogu repozytorium, po jednym w wierszu. Nie używaj / na początku ani ..',
  reason: 'Wyjaśnij, dlaczego kod tego repozytorium należy do wybranego obszaru.',
  readFor: 'Wpisz pytania, na które powinien odpowiedzieć przegląd kodu, po jednym w wierszu. To cele poszukiwań, nie gotowe odpowiedzi.',
  participantSystem: 'Wybierz system uczestniczący w integracji.',
  participantContext: 'Jeśli znasz właściwy obszar domenowy uczestnika, wybierz go z katalogu.',
  participantRole: 'Opisz rolę uczestnika, np. klient, serwer, nadawca, odbiorca lub pośrednik.',
  participantExternalOwner: 'Podaj nazwę zewnętrznej strony odpowiedzialnej tylko wtedy, gdy uczestnik jest poza lokalnym katalogiem zespołów.',
  participantNotes: 'Dodaj wyjaśnienia dotyczące tego uczestnika, po jednym w wierszu.',
  gitProvider: 'Wpisz gitlab. Ten katalog obsługuje projekty GitLab.',
  gitGroup: 'Podaj grupę GitLab względem serwera, bez adresu strony.',
  gitProject: 'Podaj krótką nazwę projektu. Do odnalezienia repozytorium wymagana jest także pełna ścieżka projektu.',
  gitProjectPath: 'Podaj ścieżkę grupa/projekt w GitLab, bez adresu serwera. To główny identyfikator repozytorium.',
  gitDefaultBranch: 'Podaj domyślną gałąź projektu. Analiza konkretnego uruchomienia może korzystać z innej gałęzi.',
  gitUrl: 'Opcjonalnie podaj adres strony projektu w GitLab. Do wyszukiwania repozytorium nadal służy ścieżka projektu.',
  gitAliases: 'Podaj inne trwałe nazwy projektu, po jednej w wierszu. Ułatwią jego rozpoznanie.',
  processActors: 'Wymień role osób lub stron uczestniczących w procesie, po jednej w wierszu. Nie przypisuje to właściciela procesu.',
  processSystemRole: 'Wybierz istniejący system i jego rolę w procesie.',
  stepId: 'Podaj stały identyfikator etapu małymi literami i z myślnikami. Musi być unikalny w tym procesie.',
  stepName: 'Krótko nazwij etap tak, aby można go było rozpoznać na liście.',
  stepType: 'Określ rodzaj etapu, np. działanie użytkownika, krok biznesowy, krok systemu lub przekazanie między systemami.',
  stepSummary: 'Opisz, co można zaobserwować na tym etapie procesu.',
  stepReference: 'Wybierz pozycje katalogu bezpośrednio uczestniczące w tym etapie.',
  stepStrongTerms: 'Podaj trwałe zwroty biznesowe, po jednym w wierszu. Pomogą rozpoznać ten etap.',
  stepOrder: 'Przesuń kartę w górę lub w dół, aby zmienić kolejność etapów. Ich identyfikatory pozostaną bez zmian.',
  boundaryBusinessCapability: 'Nazwij trwałą możliwość biznesową realizowaną przez proces. Nie tworzy to osobnego wpisu katalogu.',
  boundaryStartsWhen: 'Wpisz obserwowalne warunki rozpoczęcia procesu, po jednym w wierszu.',
  boundaryEndsWhen: 'Wpisz warunki oznaczające koniec zakresu tego procesu, po jednym w wierszu.',
  boundaryIncludes: 'Wymień działania należące do procesu, po jednym w wierszu. To opis, nie wykonywana lista kroków.',
  boundaryExcludes: 'Wymień sąsiednie działania, które nie należą do tego procesu.',
  boundaryAssumptions: 'Wpisz założenia potrzebne do zrozumienia procesu. Założenia nie są potwierdzonymi faktami.',
  lifecycleTriggerType: 'Wybierz sposób rozpoczęcia procesu: żądanie do API (api), zdarzenie (event) albo polecenie (command). Pole opisuje proces; nie tworzy punktu wejścia w aplikacji.',
  lifecycleTriggerName: 'Nazwij żądanie, zdarzenie lub polecenie rozpoczynające proces.',
  lifecycleTriggerExchange: 'Dla wiadomości możesz podać trwałą nazwę kanału lub tematu. Nie wpisuj haseł ani identyfikatorów pojedynczych wiadomości.',
  lifecycleEntryCriteria: 'Wpisz warunki, które muszą być spełnione, zanim proces się rozpocznie, po jednym w wierszu.',
  lifecycleStatuses: 'Wymień trwałe stany procesu, po jednym w wierszu. Ten opis nie steruje działaniem aplikacji.',
  lifecycleTransitionFrom: 'Wpisz stan początkowy przejścia. Pozostaw pusty tylko dla przejścia rozpoczynającego proces.',
  lifecycleTransitionTo: 'Wpisz stan, do którego prowadzi przejście. Nie zmienia to rzeczywistego stanu procesu.',
  lifecycleTransitionTrigger: 'Opisz działanie lub zdarzenie powodujące przejście między stanami.',
  lifecycleTerminalStates: 'Wymień stany, po których proces już nie przechodzi dalej, po jednym w wierszu.',
  lifecycleSuccessOutcomes: 'Wymień możliwe rezultaty pomyślnego zakończenia, po jednym w wierszu. Oznaki ich wystąpienia wpisz osobno.',
  lifecyclePartialOutcomes: 'Opisz możliwe częściowe rezultaty, gdy proces nie osiągnął jeszcze pełnego celu.',
  lifecycleFailedOutcomes: 'Wymień możliwe rezultaty niepowodzenia. Sam wpis nie potwierdza wystąpienia awarii.',
  lifecycleCancellationOutcomes: 'Wymień rezultaty świadomego anulowania procesu, odróżniając je od błędu technicznego.',
  completionSuccessful: 'Wpisz obserwowalne oznaki osiągnięcia oczekiwanego rezultatu, po jednej w wierszu.',
  completionPartial: 'Wpisz obserwowalne oznaki postępu bez pełnego zakończenia.',
  completionFailed: 'Wpisz obserwowalne oznaki niepowodzenia. Opisuj objawy, nie zgadywaną przyczynę.',
  completionCancelled: 'Wpisz obserwowalne oznaki świadomego anulowania procesu.',
  signalStrength: 'Wybierz siłę wskazówki: exact oznacza jednoznaczne dopasowanie, strong mocną wskazówkę, medium wymaga dodatkowego kontekstu, a weak służy tylko do wstępnego szukania.',
  signalKey: 'Wybierz rodzaj danych, z których pochodzi sygnał, np. nazwa usługi lub adres ścieżki.',
  signalValues: 'Wpisz trwałe wartości sygnału, po jednej w wierszu. Nie podawaj sekretów ani jednorazowych identyfikatorów.',
  relationType: 'Podaj rodzaj powiązania, np. depends-on (zależy od), uses (używa) lub supports (wspiera). Wpis nie uruchamia żadnego działania.',
  relationTargetType: 'Wybierz rodzaj pozycji katalogu, z którą ten wpis jest powiązany.',
  relationTarget: 'Wybierz istniejącą pozycję katalogu. Nie można powiązać wpisu z samym sobą.',
  relationExternalTarget: 'Wpisz nazwę strony zewnętrznej tylko wtedy, gdy nie ma jej w katalogu.',
  relationVia: 'Opcjonalnie wskaż integracje, przez które przebiega to powiązanie.',
  relationEvidence: 'Wyjaśnij, skąd wiadomo o tym powiązaniu. Opis sam w sobie nie podnosi pewności dopasowania.',
  processFailureId: 'Podaj stały identyfikator scenariusza niepowodzenia małymi literami i z myślnikami. To nie jest identyfikator incydentu.',
  processFailureName: 'Krótko nazwij możliwy sposób niepowodzenia procesu.',
  processFailureSummary: 'Opisz, co można zaobserwować, gdy proces nie przebiega poprawnie. Nie przesądzaj przyczyny.',
  processFailureStep: 'Opcjonalnie podaj identyfikator etapu procesu, którego dotyczy ten scenariusz.',
  processFailureSignals: 'Wpisz obserwowalne objawy, po jednym w wierszu. Nie wpisuj niepotwierdzonych przyczyn.',
  integrationFailureName: 'Krótko nazwij możliwy problem na granicy integracji.',
  integrationFailureType: 'Podaj trwały rodzaj problemu, np. timeout, odrzucona wiadomość albo niedostępność.',
  integrationFailureSymptom: 'Opisz, co operator lub system wywołujący może zaobserwować przy tym problemie.',
  integrationFailureImpact: 'Opisz możliwy skutek dla procesu lub systemu. Sam opis nie potwierdza, że problem wystąpił.',
  dataPrimaryObjects: 'Wymień główne rodzaje obiektów biznesowych procesu, po jednym w wierszu.',
  dataInputArtifacts: 'Wymień rodzaje wiadomości, dokumentów lub żądań przyjmowanych przez proces. Nie wklejaj rzeczywistych danych.',
  dataOutputArtifacts: 'Wymień rodzaje wiadomości, dokumentów lub potwierdzeń wytwarzanych przez proces.',
  dataPersistedEntities: 'Wymień rodzaje danych zapisywanych przez proces. Ten opis nie daje dostępu do bazy danych.',
  dataReadModels: 'Wymień widoki lub zestawienia danych używane przez proces.',
  dataAuditArtifacts: 'Wymień rodzaje zapisów audytowych tworzonych przez proces, bez rzeczywistych identyfikatorów osób.',
  dataArtifactNotes: 'Dodaj trwałe wyjaśnienia znaczenia wymienionych danych, po jednym w wierszu.',
  coverageStatus: 'Określ zakres sprawdzenia wpisu: pełny (complete), częściowy (partial) albo nieznany (unknown).',
  coverageScanned: 'Wymień źródła lub obszary repozytorium, które rzeczywiście sprawdzono.',
  coverageExpected: 'Wymień źródła, których jeszcze nie sprawdzono, choć mogą mieć znaczenie.',
  coverageLimitations: 'Opisz, czego nie da się obecnie potwierdzić. Ograniczenia będą widoczne przy korzystaniu z katalogu.',
  gapId: 'Opcjonalnie podaj stały identyfikator pytania małymi literami i z myślnikami.',
  gapType: 'Określ, dlaczego brakuje informacji, np. niepotwierdzona odpowiedzialność lub niejasna granica obszaru.',
  gapSummary: 'Napisz jedno konkretne pytanie lub brak do wyjaśnienia. Pojawi się na liście otwartych pytań.',
  gapSeverity: 'Ustal ważność pytania: błąd (error), ostrzeżenie (warning) albo informacja (info). Nie oznacza to, że wystąpił incydent.',
  gapStatus: 'Oznacz pytanie jako otwarte (open) albo rozwiązane (resolved), gdy odpowiedź została potwierdzona.',
  gapNextSources: 'Wskaż źródła, które warto sprawdzić w następnej kolejności. Nie zostaną pobrane automatycznie.'
};

function group(key: string, label: string, type: OperationalContextWritableType): ReferenceGroup {
  return { key, label, type };
}

@Component({
  selector: 'app-context-structured-field-editor',
  imports: [MatIconModule, MatTooltipModule],
  templateUrl: './context-structured-field-editor.html',
  styleUrl: './context-structured-field-editor.scss'
})
export class ContextStructuredFieldEditorComponent {
  readonly field = input.required<OperationalContextFormField>();
  readonly entityType = input.required<OperationalContextWritableType>();
  readonly entityId = input('');
  readonly value = input<unknown>(null);
  readonly referenceOptions = input<OperationalContextReferenceOptions>({});
  readonly readonly = input(false);
  readonly valueChange = output<unknown>();

  tooltip(key: string): string {
    return FIELD_TOOLTIPS[key] || 'Ta wartość zostanie zapisana w katalogu i będzie widoczna w szczegółach wpisu.';
  }

  object(): JsonObject {
    return asObject(this.value());
  }

  objectText(key: string): string {
    return text(this.object()[key]);
  }

  objectList(key: string): string[] {
    return stringList(this.object()[key]);
  }

  updateObject(key: string, value: unknown): void {
    const next = { ...this.object() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateObjectList(key: string, raw: string): void {
    this.updateObject(key, lines(raw));
  }

  repositoryEvidence(): JsonObject[] {
    return objectList(this.value());
  }

  addRepositoryEvidence(): void {
    this.valueChange.emit([...this.repositoryEvidence(), { sourceRef: '', evidenceType: '' }]);
  }

  updateRepositoryEvidence(index: number, key: string, value: unknown): void {
    const evidence = this.repositoryEvidence().map((item) => ({ ...item }));
    assignOrDelete(evidence[index], key, value);
    this.valueChange.emit(evidence);
  }

  removeRepositoryEvidence(index: number): void {
    this.valueChange.emit(this.repositoryEvidence().filter((_, itemIndex) => itemIndex !== index));
  }

  boundedLocalLanguage(): string[] {
    return stringList(this.value());
  }

  updateBoundedLocalLanguage(raw: string): void {
    this.valueChange.emit(lines(raw));
  }

  boundedScopeGroups(): StructuredListGroup[] {
    return BOUNDED_SCOPE_GROUPS;
  }

  boundedSemanticGroups(): StructuredListGroup[] {
    return BOUNDED_SEMANTIC_GROUPS;
  }

  boundedEvidence(): JsonObject[] {
    return objectList(this.value());
  }

  addBoundedEvidence(): void {
    this.valueChange.emit([...this.boundedEvidence(), { sourceRef: '', evidenceType: '' }]);
  }

  updateBoundedEvidence(index: number, key: string, value: unknown): void {
    const evidence = this.boundedEvidence().map((item) => ({ ...item }));
    assignOrDelete(evidence[index], key, value);
    this.valueChange.emit(evidence);
  }

  removeBoundedEvidence(index: number): void {
    this.valueChange.emit(this.boundedEvidence().filter((_, itemIndex) => itemIndex !== index));
  }

  processSystemRoles(): Array<{ key: ProcessSystemRole; label: string }> {
    return PROCESS_SYSTEM_ROLES;
  }

  selectedProcessSystems(role: ProcessSystemRole): string[] {
    return stringList(this.object()[role]);
  }

  availableProcessSystems(role: ProcessSystemRole): OperationalContextReferenceOption[] {
    const selected = new Set(PROCESS_SYSTEM_ROLES.flatMap((candidate) => this.selectedProcessSystems(candidate.key)));
    return this.optionsFor('system').filter((option) => !selected.has(option.id) || this.selectedProcessSystems(role).includes(option.id));
  }

  addProcessSystem(role: ProcessSystemRole, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject(role, unique([...this.selectedProcessSystems(role), id]));
    resetSelect(event);
  }

  removeProcessSystem(role: ProcessSystemRole, id: string): void {
    this.updateObject(role, this.selectedProcessSystems(role).filter((value) => value !== id));
  }

  ownershipTeamOptions(): OperationalContextReferenceOption[] {
    return this.optionsFor('team').filter((option) => !this.objectList('ownerTeamIds').includes(option.id));
  }

  addOwnerTeam(event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject('ownerTeamIds', unique([...this.objectList('ownerTeamIds'), id]));
    resetSelect(event);
  }

  removeOwnerTeam(id: string): void {
    this.updateObject('ownerTeamIds', this.objectList('ownerTeamIds').filter((value) => value !== id));
  }

  referenceGroups(): ReferenceGroup[] {
    return REFERENCE_GROUPS[this.entityType()] || [];
  }

  selectedReferences(key: string): string[] {
    return stringList(this.object()[key]);
  }

  availableReferences(referenceGroup: ReferenceGroup): OperationalContextReferenceOption[] {
    const selected = this.selectedReferences(referenceGroup.key);
    return this.optionsFor(referenceGroup.type).filter((option) =>
      !selected.includes(option.id)
      && !(referenceGroup.type === this.entityType() && option.id === this.entityId())
    );
  }

  addReference(referenceGroup: ReferenceGroup, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject(referenceGroup.key, unique([...this.selectedReferences(referenceGroup.key), id]));
    resetSelect(event);
  }

  removeReference(key: string, id: string): void {
    this.updateObject(key, this.selectedReferences(key).filter((value) => value !== id));
  }

  signalStrengths(): Array<{ key: SignalStrength; label: string }> {
    return SIGNAL_STRENGTHS;
  }

  signalRows(): MatchSignalRow[] {
    const source = this.object();
    return SIGNAL_STRENGTHS.flatMap((strength) =>
      Object.entries(asObject(source[strength.key])).map(([key, value]) => ({
        strength: strength.key,
        key,
        values: stringList(value)
      }))
    );
  }

  signalKeySuggestions(): string[] {
    return unique([
      ...(SIGNAL_KEY_SUGGESTIONS[this.entityType()] || []),
      ...this.signalRows().map((row) => row.key)
    ]).sort((left, right) => left.localeCompare(right));
  }

  addSignal(): void {
    const rows = this.signalRows();
    const usedStrongKeys = new Set(rows.filter((row) => row.strength === 'strong').map((row) => row.key));
    const key = this.signalKeySuggestions().find((candidate) => !usedStrongKeys.has(candidate)) || 'customSignal';
    this.emitSignals([...rows, { strength: 'strong', key, values: [] }]);
  }

  updateSignal(index: number, field: 'strength' | 'key', value: string): void {
    const rows = this.signalRows().map((row) => ({ ...row, values: [...row.values] }));
    if (!rows[index]) return;
    if (field === 'strength' && isSignalStrength(value)) rows[index].strength = value;
    if (field === 'key' && value.trim()) rows[index].key = value.trim();
    this.emitSignals(rows);
  }

  updateSignalValues(index: number, raw: string): void {
    const rows = this.signalRows().map((row) => ({ ...row, values: [...row.values] }));
    if (!rows[index]) return;
    rows[index].values = unique(lines(raw));
    this.emitSignals(rows);
  }

  removeSignal(index: number): void {
    this.emitSignals(this.signalRows().filter((_, rowIndex) => rowIndex !== index));
  }

  failureModes(): JsonObject[] {
    return objectList(this.value());
  }

  addFailureMode(): void {
    const item = this.entityType() === 'process'
      ? { id: '', name: '', summary: '', signals: [] }
      : { name: '', type: '', symptom: '', impact: '' };
    this.valueChange.emit([...this.failureModes(), item]);
  }

  updateFailureMode(index: number, key: string, value: unknown): void {
    const modes = this.failureModes().map((item) => ({ ...item }));
    assignOrDelete(modes[index], key, value);
    this.valueChange.emit(modes);
  }

  updateFailureModeList(index: number, key: string, raw: string): void {
    this.updateFailureMode(index, key, lines(raw));
  }

  removeFailureMode(index: number): void {
    this.valueChange.emit(this.failureModes().filter((_, rowIndex) => rowIndex !== index));
  }

  processBoundary(): JsonObject {
    return asObject(this.value());
  }

  processBoundaryGroups(): StructuredListGroup[] {
    return PROCESS_BOUNDARY_LIST_GROUPS;
  }

  processBoundaryText(key: string): string {
    return text(this.processBoundary()[key]);
  }

  processBoundaryList(key: string): string[] {
    return stringList(this.processBoundary()[key]);
  }

  updateProcessBoundary(key: string, value: unknown): void {
    const next = { ...this.processBoundary() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateProcessBoundaryList(key: string, raw: string): void {
    this.updateProcessBoundary(key, lines(raw));
  }

  processLifecycle(): JsonObject {
    return asObject(this.value());
  }

  lifecycleListGroups(): StructuredListGroup[] {
    return PROCESS_LIFECYCLE_LIST_GROUPS;
  }

  lifecycleOutcomeGroups(): StructuredListGroup[] {
    return PROCESS_LIFECYCLE_OUTCOME_GROUPS;
  }

  lifecycleList(key: string): string[] {
    return stringList(this.processLifecycle()[key]);
  }

  lifecycleTriggers(): JsonObject[] {
    return objectList(this.processLifecycle()['triggers']);
  }

  triggerTypes(): string[] {
    return PROCESS_TRIGGER_TYPES;
  }

  addLifecycleTrigger(): void {
    this.updateProcessLifecycle('triggers', [...this.lifecycleTriggers(), { type: 'api', name: '' }]);
  }

  updateLifecycleTrigger(index: number, key: string, value: unknown): void {
    const triggers = this.lifecycleTriggers().map((item) => ({ ...item }));
    assignOrDelete(triggers[index], key, value);
    this.updateProcessLifecycle('triggers', triggers);
  }

  removeLifecycleTrigger(index: number): void {
    this.updateProcessLifecycle('triggers', this.lifecycleTriggers().filter((_, rowIndex) => rowIndex !== index));
  }

  lifecycleTransitions(): JsonObject[] {
    return objectList(this.processLifecycle()['transitions']);
  }

  addLifecycleTransition(): void {
    this.updateProcessLifecycle('transitions', [...this.lifecycleTransitions(), { from: '', to: '', trigger: '' }]);
  }

  updateLifecycleTransition(index: number, key: string, value: unknown): void {
    const transitions = this.lifecycleTransitions().map((item) => ({ ...item }));
    assignOrDelete(transitions[index], key, value);
    this.updateProcessLifecycle('transitions', transitions);
  }

  removeLifecycleTransition(index: number): void {
    this.updateProcessLifecycle('transitions', this.lifecycleTransitions().filter((_, rowIndex) => rowIndex !== index));
  }

  updateLifecycleList(key: string, raw: string): void {
    this.updateProcessLifecycle(key, lines(raw));
  }

  completionSignalGroups(): StructuredListGroup[] {
    return COMPLETION_SIGNAL_GROUPS;
  }

  completionSignals(): JsonObject {
    return asObject(this.value());
  }

  completionSignalList(key: string): string[] {
    return stringList(this.completionSignals()[key]);
  }

  updateCompletionSignalList(key: string, raw: string): void {
    const next = { ...this.completionSignals() };
    assignOrDelete(next, key, lines(raw));
    this.valueChange.emit(next);
  }

  private updateProcessLifecycle(key: string, value: unknown): void {
    const next = { ...this.processLifecycle() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  dataArtifactGroups(): StructuredListGroup[] {
    return DATA_ARTIFACT_GROUPS;
  }

  updateDataArtifactList(key: string, raw: string): void {
    this.updateObjectList(key, raw);
  }

  sourceCoverage(): JsonObject {
    return asObject(this.value());
  }

  sourceCoverageText(key: string): string {
    return text(this.sourceCoverage()[key]);
  }

  sourceCoverageList(key: string): string[] {
    return stringList(this.sourceCoverage()[key]);
  }

  sourceCoverageStatuses(): string[] {
    return SOURCE_COVERAGE_STATUSES;
  }

  updateSourceCoverage(key: string, value: unknown): void {
    const next = { ...this.sourceCoverage() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateSourceCoverageList(key: string, raw: string): void {
    this.updateSourceCoverage(key, lines(raw));
  }

  gaps(): JsonObject[] {
    return objectList(this.value());
  }

  gapSeverities(): string[] {
    return GAP_SEVERITIES;
  }

  gapStatuses(): string[] {
    return GAP_STATUSES;
  }

  addGap(): void {
    this.valueChange.emit([...this.gaps(), { id: '', type: '', summary: '', severity: 'info', status: 'open', suggestedNextSources: [] }]);
  }

  updateGap(index: number, key: string, value: unknown): void {
    const gaps = this.gaps().map((item) => ({ ...item }));
    assignOrDelete(gaps[index], key, value);
    this.valueChange.emit(gaps);
  }

  updateGapList(index: number, key: string, raw: string): void {
    this.updateGap(index, key, lines(raw));
  }

  removeGap(index: number): void {
    this.valueChange.emit(this.gaps().filter((_, rowIndex) => rowIndex !== index));
  }

  relations(): JsonObject[] {
    return objectList(this.value());
  }

  addRelation(): void {
    const targetType = defaultRelationTargetType(this.entityType());
    this.valueChange.emit([...this.relations(), { type: 'related-to', targetType, target: '' }]);
  }

  updateRelation(index: number, key: string, value: unknown): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    if (!relations[index]) return;
    assignOrDelete(relations[index], key, value);
    this.valueChange.emit(relations);
  }

  removeRelation(index: number): void {
    this.valueChange.emit(this.relations().filter((_, relationIndex) => relationIndex !== index));
  }

  relationTargetTypes(): ReferenceGroup[] {
    return RELATION_TARGET_TYPES;
  }

  relationTargetType(index: number): string {
    const relation = this.relations()[index] || {};
    return text(relation['targetType']);
  }

  relationTargetId(index: number): string {
    const relation = this.relations()[index] || {};
    return text(relation['target']);
  }

  relationTargetOptions(index: number): OperationalContextReferenceOption[] {
    const type = this.relationTargetType(index) as OperationalContextWritableType;
    if (!RELATION_TARGET_TYPES.some((candidate) => candidate.type === type)) return [];
    return this.optionsFor(type).filter((option) => !(type === this.entityType() && option.id === this.entityId()));
  }

  updateRelationTargetType(index: number, event: Event): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    delete relation['target'];
    delete relation['externalSystem'];
    assignOrDelete(relation, 'targetType', eventValue(event));
    this.valueChange.emit(relations);
  }

  updateRelationTarget(index: number, event: Event): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    delete relation['externalSystem'];
    assignOrDelete(relation, 'target', eventValue(event));
    this.valueChange.emit(relations);
  }

  updateRelationExternalTarget(index: number, value: string): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    if (value.trim()) {
      delete relation['targetType'];
      delete relation['target'];
    }
    assignOrDelete(relation, 'externalSystem', value.trim());
    this.valueChange.emit(relations);
  }

  selectedRelationVia(index: number): string[] {
    return stringList(this.relations()[index]?.['via']);
  }

  relationViaOptions(index: number): OperationalContextReferenceOption[] {
    const selected = this.selectedRelationVia(index);
    return this.optionsFor('integration').filter((option) =>
      !selected.includes(option.id)
      && !(this.entityType() === 'integration' && option.id === this.entityId())
    );
  }

  addRelationVia(index: number, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateRelation(index, 'via', unique([...this.selectedRelationVia(index), id]));
    resetSelect(event);
  }

  removeRelationVia(index: number, id: string): void {
    this.updateRelation(index, 'via', this.selectedRelationVia(index).filter((value) => value !== id));
  }

  optionLabel(type: OperationalContextWritableType, id: string): string {
    return this.optionsFor(type).find((option) => option.id === id)?.label || id;
  }

  targetType(): string {
    return text(this.object()['type']);
  }

  targetOptions(): OperationalContextReferenceOption[] {
    const type = this.targetType();
    return type === 'system' || type === 'bounded-context' ? this.optionsFor(type) : [];
  }

  updateTargetType(event: Event): void {
    this.valueChange.emit({ ...this.object(), type: eventValue(event), id: '' });
  }

  repositories(): JsonObject[] {
    return objectList(this.value());
  }

  addRepository(): void {
    this.valueChange.emit([
      ...this.repositories(),
      { repoId: '', role: this.repositories().length ? 'supporting' : 'primary', priority: this.repositories().length + 1, searchMode: 'whole-repository' }
    ]);
  }

  updateRepository(index: number, key: string, value: unknown): void {
    const repositories = this.repositories().map((repository) => ({ ...repository }));
    const repository = repositories[index];
    assignOrDelete(repository, key, value);
    if (key === 'searchMode' && value === 'whole-repository') delete repository['pathPrefixes'];
    this.valueChange.emit(repositories);
  }

  updateRepositoryNumber(index: number, key: string, event: Event): void {
    const raw = eventValue(event);
    this.updateRepository(index, key, raw ? Number(raw) : null);
  }

  updateRepositoryList(index: number, key: string, raw: string): void {
    this.updateRepository(index, key, lines(raw));
  }

  removeRepository(index: number): void {
    this.valueChange.emit(this.repositories().filter((_, itemIndex) => itemIndex !== index));
  }

  repositoryOptions(index: number): OperationalContextReferenceOption[] {
    const current = text(this.repositories()[index]?.['repoId']);
    const selected = this.repositories().map((repository) => text(repository['repoId'])).filter(Boolean);
    return this.optionsFor('repository').filter((option) => option.id === current || !selected.includes(option.id));
  }

  processSteps(): JsonObject[] {
    return objectList(this.value());
  }

  addProcessStep(): void {
    this.valueChange.emit([
      ...this.processSteps(),
      { id: '', name: '', type: 'business-step', summary: '', references: {} }
    ]);
  }

  updateProcessStep(index: number, key: string, value: unknown): void {
    const steps = this.processSteps().map((step) => ({ ...step }));
    assignOrDelete(steps[index], key, value);
    this.valueChange.emit(steps);
  }

  removeProcessStep(index: number): void {
    this.valueChange.emit(this.processSteps().filter((_, itemIndex) => itemIndex !== index));
  }

  moveProcessStep(index: number, offset: -1 | 1): void {
    const target = index + offset;
    const steps = this.processSteps().map((step) => ({ ...step }));
    if (target < 0 || target >= steps.length) return;
    [steps[index], steps[target]] = [steps[target], steps[index]];
    this.valueChange.emit(steps);
  }

  processStepReferenceGroups(): ReferenceGroup[] {
    return PROCESS_STEP_REFERENCE_GROUPS;
  }

  selectedProcessStepReferences(index: number, key: string): string[] {
    const references = asObject(this.processSteps()[index]?.['references']);
    return stringList(references[key]);
  }

  availableProcessStepReferences(index: number, referenceGroup: ReferenceGroup): OperationalContextReferenceOption[] {
    const selected = this.selectedProcessStepReferences(index, referenceGroup.key);
    return this.optionsFor(referenceGroup.type).filter((option) => !selected.includes(option.id));
  }

  addProcessStepReference(index: number, referenceGroup: ReferenceGroup, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateProcessStepReferenceList(index, referenceGroup.key, unique([
      ...this.selectedProcessStepReferences(index, referenceGroup.key),
      id
    ]));
    resetSelect(event);
  }

  removeProcessStepReference(index: number, key: string, id: string): void {
    this.updateProcessStepReferenceList(
      index,
      key,
      this.selectedProcessStepReferences(index, key).filter((value) => value !== id)
    );
  }

  processStepStrongTerms(index: number): string[] {
    const matchSignals = asObject(this.processSteps()[index]?.['matchSignals']);
    return stringList(asObject(matchSignals['strong'])['terms']);
  }

  updateProcessStepStrongTerms(index: number, raw: string): void {
    const step = this.processSteps()[index] || {};
    const matchSignals = { ...asObject(step['matchSignals']) };
    const strong = { ...asObject(matchSignals['strong']) };
    assignOrDelete(strong, 'terms', lines(raw));
    assignOrDelete(matchSignals, 'strong', strong);
    this.updateProcessStep(index, 'matchSignals', matchSignals);
  }

  private updateProcessStepReferenceList(index: number, key: string, values: string[]): void {
    const step = this.processSteps()[index] || {};
    const references = { ...asObject(step['references']) };
    assignOrDelete(references, key, values);
    this.updateProcessStep(index, 'references', references);
  }

  participant(groupName: ParticipantGroup, index = 0): JsonObject {
    const participants = this.object();
    return groupName === 'source'
      ? asObject(participants['source'])
      : objectList(participants[groupName])[index] || {};
  }

  participantRows(): ParticipantRow[] {
    return [
      { group: 'source', index: 0, label: 'Source', removable: false },
      ...this.participantList('targets').map((_, index) => ({ group: 'targets' as const, index, label: `Target ${index + 1}`, removable: true })),
      ...this.participantList('intermediaries').map((_, index) => ({ group: 'intermediaries' as const, index, label: `Intermediary ${index + 1}`, removable: true })),
      ...this.participantList('finalTargets').map((_, index) => ({ group: 'finalTargets' as const, index, label: `Final target ${index + 1}`, removable: true }))
    ];
  }

  participantList(groupName: Exclude<ParticipantGroup, 'source'>): JsonObject[] {
    return objectList(this.object()[groupName]);
  }

  addParticipant(groupName: Exclude<ParticipantGroup, 'source'>): void {
    const next = { ...this.object(), [groupName]: [...this.participantList(groupName), {}] };
    this.valueChange.emit(next);
  }

  removeParticipant(groupName: Exclude<ParticipantGroup, 'source'>, index: number): void {
    const next = {
      ...this.object(),
      [groupName]: this.participantList(groupName).filter((_, itemIndex) => itemIndex !== index)
    };
    this.valueChange.emit(next);
  }

  updateParticipant(groupName: ParticipantGroup, index: number, key: string, value: unknown): void {
    const next = { ...this.object() };
    if (groupName === 'source') {
      const source = { ...asObject(next['source']) };
      assignOrDelete(source, key, value);
      next['source'] = source;
    } else {
      const participants = this.participantList(groupName).map((participant) => ({ ...participant }));
      assignOrDelete(participants[index], key, value);
      next[groupName] = participants;
    }
    this.valueChange.emit(next);
  }

  updateParticipantList(groupName: ParticipantGroup, index: number, key: string, raw: string): void {
    this.updateParticipant(groupName, index, key, lines(raw));
  }

  private optionsFor(type: OperationalContextWritableType): OperationalContextReferenceOption[] {
    return this.referenceOptions()[type] || [];
  }

  private emitSignals(rows: MatchSignalRow[]): void {
    const next: JsonObject = {};
    for (const strength of SIGNAL_STRENGTHS) {
      const bucket: JsonObject = {};
      for (const row of rows.filter((candidate) => candidate.strength === strength.key && candidate.key)) {
        bucket[row.key] = unique([...(stringList(bucket[row.key])), ...row.values]);
      }
      if (Object.keys(bucket).length) next[strength.key] = bucket;
    }
    this.valueChange.emit(next);
  }
}

function asObject(value: unknown): JsonObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : {};
}

function objectList(value: unknown): JsonObject[] {
  return Array.isArray(value) ? value.map(asObject) : [];
}

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map(text).filter(Boolean) : [];
}

function lines(value: string): string[] {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function isSignalStrength(value: string): value is SignalStrength {
  return SIGNAL_STRENGTHS.some((strength) => strength.key === value);
}

function defaultRelationTargetType(sourceType: OperationalContextWritableType): OperationalContextWritableType {
  if (sourceType === 'process') return 'process';
  if (sourceType === 'integration') return 'process';
  if (sourceType === 'bounded-context') return 'bounded-context';
  return 'system';
}

function assignOrDelete(target: JsonObject, key: string, value: unknown): void {
  const emptyArray = Array.isArray(value) && value.length === 0;
  const emptyObject = value !== null && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0;
  if (value === '' || value === null || value === undefined || emptyArray || emptyObject) delete target[key];
  else target[key] = value;
}

function eventValue(event: Event): string {
  return String((event.target as HTMLInputElement | HTMLSelectElement | null)?.value || '').trim();
}

function resetSelect(event: Event): void {
  const select = event.target as HTMLSelectElement | null;
  if (select) select.value = '';
}
