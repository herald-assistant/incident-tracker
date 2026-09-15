import { FEATURE_DEFINITIONS } from '../feature-definitions';
import { sendExtensionMessage } from '../platform/message';
import {
  originStateMessage,
  setOriginFeatureMessage,
  type OriginStateResponse
} from '../platform/runtime-messages';
import { inspectablePage, type InspectablePage } from '../platform/url';

const pageTitle = requiredElement<HTMLElement>('current-page-title');
const originLabel = requiredElement<HTMLElement>('current-origin');
const pageStatus = requiredElement<HTMLElement>('page-status');
const featureList = requiredElement<HTMLElement>('feature-list');
const feedback = requiredElement<HTMLElement>('feedback');
const optionsButton = requiredElement<HTMLButtonElement>('open-options');
const extensionVersion = requiredElement<HTMLElement>('extension-version');

let activeTab: chrome.tabs.Tab | null = null;
let page: InspectablePage | null = null;
let originState: OriginStateResponse | null = null;
let busyFeatureId: string | null = null;

extensionVersion.textContent = chrome.runtime.getManifest().version;
optionsButton.addEventListener('click', () => void chrome.runtime.openOptionsPage());

void initialize();

async function initialize(): Promise<void> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  activeTab = tab ?? null;
  page = inspectablePage(tab?.url);
  pageTitle.textContent = tab?.title || 'Biezaca karta';

  if (!page || activeTab?.id === undefined) {
    originLabel.textContent = tab?.url ?? 'Brak adresu karty';
    setPageStatus('Ta strona nie obsluguje rozszerzen Chrome.', 'blocked');
    renderFeatures();
    return;
  }

  originLabel.textContent = page.origin;
  const response = await sendExtensionMessage<OriginStateResponse>(originStateMessage(page.origin));
  if (!response.ok) {
    setFeedback(response.error.message);
    setPageStatus('Nie mozna odczytac konfiguracji.', 'blocked');
    renderFeatures();
    return;
  }
  originState = response.data;
  setPageStatus(
    originState.enabledFeatureIds.length > 0
      ? 'Rozszerzenie jest aktywne na tym originie.'
      : 'Wybierz funkcje, ktore maja dzialac na tym originie.',
    originState.enabledFeatureIds.length > 0 ? 'ready' : 'idle'
  );
  renderFeatures();
}

function renderFeatures(): void {
  featureList.replaceChildren();
  for (const definition of FEATURE_DEFINITIONS) {
    const enabled = originState?.enabledFeatureIds.includes(definition.id) ?? false;
    const card = document.createElement('article');
    card.className = 'feature-card';
    const copy = document.createElement('div');
    const heading = document.createElement('h3');
    heading.textContent = definition.name;
    const description = document.createElement('p');
    description.textContent = definition.description;
    copy.append(heading, description);
    if (definition.id === 'ui-explorer') {
      const hint = document.createElement('small');
      hint.textContent = enabled ? 'Przytrzymaj Ctrl + Alt na stronie' : 'Wymaga zgody dla originu';
      copy.append(hint);
    }

    const button = document.createElement('button');
    button.type = 'button';
    button.className = `toggle-button${enabled ? ' enabled' : ''}`;
    button.textContent = busyFeatureId === definition.id ? 'Czekaj…' : enabled ? 'Wylacz' : 'Wlacz';
    button.disabled = !page || activeTab?.id === undefined || busyFeatureId !== null;
    button.setAttribute('aria-pressed', String(enabled));
    button.addEventListener('click', () => void toggleFeature(definition.id, !enabled));
    card.append(copy, button);
    featureList.append(card);
  }
}

async function toggleFeature(featureId: string, enabled: boolean): Promise<void> {
  if (!page || activeTab?.id === undefined) {
    return;
  }
  clearFeedback();
  busyFeatureId = featureId;
  renderFeatures();
  try {
    if (enabled) {
      const granted = await chrome.permissions.request({ origins: [page.permissionPattern] });
      if (!granted) {
        setFeedback('Chrome nie przyznal dostepu do tego originu.');
        return;
      }
    }
    const response = await sendExtensionMessage<OriginStateResponse>(
      setOriginFeatureMessage({
        origin: page.origin,
        featureId,
        enabled,
        tabId: activeTab.id
      })
    );
    if (!response.ok) {
      setFeedback(response.error.message);
      return;
    }
    originState = response.data;
    setPageStatus(
      enabled
        ? 'Funkcja jest aktywna. Wroc na strone i uzyj Ctrl + Alt.'
        : 'Funkcja zostala wylaczona na tym originie.',
      enabled ? 'ready' : 'idle'
    );
  } catch (error) {
    setFeedback(error instanceof Error ? error.message : 'Nie udalo sie zmienic uprawnienia.');
  } finally {
    busyFeatureId = null;
    renderFeatures();
  }
}

function setPageStatus(message: string, kind: 'ready' | 'blocked' | 'idle'): void {
  pageStatus.textContent = message;
  pageStatus.className = `status-pill${kind === 'idle' ? '' : ` ${kind}`}`;
}

function setFeedback(message: string): void {
  feedback.textContent = message;
  feedback.classList.remove('hidden');
}

function clearFeedback(): void {
  feedback.textContent = '';
  feedback.classList.add('hidden');
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Missing popup element #${id}.`);
  }
  return element as T;
}
