import { featureDefinition } from '../feature-definitions';
import { sendExtensionMessage } from '../platform/message';
import { setOriginFeatureMessage, type OriginStateResponse } from '../platform/runtime-messages';
import {
  loadSettings,
  saveSettings,
  withTdwBaseUrl,
  type ExtensionSettings,
  type OriginSettings
} from '../platform/settings';
import { normalizeTdwBaseUrl } from '../platform/url';

const form = requiredElement<HTMLFormElement>('settings-form');
const baseUrlInput = requiredElement<HTMLInputElement>('tdw-base-url');
const feedback = requiredElement<HTMLElement>('save-feedback');
const originList = requiredElement<HTMLElement>('origin-list');
const emptyOrigins = requiredElement<HTMLElement>('empty-origins');
const revokeAllButton = requiredElement<HTMLButtonElement>('revoke-all');

let settings: ExtensionSettings;

form.addEventListener('submit', (event) => void saveConfiguration(event));
revokeAllButton.addEventListener('click', () => void revokeAllOrigins());

void initialize();

async function initialize(): Promise<void> {
  settings = await loadSettings();
  baseUrlInput.value = settings.tdwBaseUrl;
  renderOrigins();
}

async function saveConfiguration(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const normalized = normalizeTdwBaseUrl(baseUrlInput.value);
  if (!normalized) {
    showFeedback('Podaj poprawny adres HTTP(S) TDW.', true);
    return;
  }
  settings = withTdwBaseUrl(settings, normalized);
  await saveSettings(settings);
  baseUrlInput.value = normalized;
  showFeedback('Ustawienia zapisane. Transport REST pozostaje w trybie demo.', false);
}

function renderOrigins(): void {
  const origins = Object.values(settings.origins).sort((left, right) =>
    left.origin.localeCompare(right.origin)
  );
  originList.replaceChildren();
  emptyOrigins.classList.toggle('hidden', origins.length > 0);
  revokeAllButton.disabled = origins.length === 0;

  for (const origin of origins) {
    originList.append(originRow(origin));
  }
}

function originRow(origin: OriginSettings): HTMLElement {
  const row = document.createElement('article');
  row.className = 'origin-row';
  const copy = document.createElement('div');
  copy.className = 'origin-copy';
  const title = document.createElement('strong');
  title.textContent = origin.origin;
  const detail = document.createElement('small');
  detail.textContent = origin.enabledFeatureIds
    .map((featureId) => featureDefinition(featureId)?.name ?? featureId)
    .join(' · ');
  copy.append(title, detail);

  const revoke = document.createElement('button');
  revoke.type = 'button';
  revoke.className = 'secondary-button';
  revoke.textContent = 'Odbierz dostep';
  revoke.addEventListener('click', () => void revokeOrigin(origin, revoke));
  row.append(copy, revoke);
  return row;
}

async function revokeOrigin(origin: OriginSettings, button: HTMLButtonElement): Promise<void> {
  button.disabled = true;
  for (const featureId of origin.enabledFeatureIds) {
    const response = await sendExtensionMessage<OriginStateResponse>(
      setOriginFeatureMessage({ origin: origin.origin, featureId, enabled: false })
    );
    if (!response.ok) {
      showFeedback(response.error.message, true);
      button.disabled = false;
      return;
    }
  }
  settings = await loadSettings();
  renderOrigins();
  showFeedback(`Odebrano dostep do ${origin.origin}.`, false);
}

async function revokeAllOrigins(): Promise<void> {
  revokeAllButton.disabled = true;
  const origins = Object.values(settings.origins);
  for (const origin of origins) {
    for (const featureId of origin.enabledFeatureIds) {
      const response = await sendExtensionMessage<OriginStateResponse>(
        setOriginFeatureMessage({ origin: origin.origin, featureId, enabled: false })
      );
      if (!response.ok) {
        showFeedback(response.error.message, true);
        settings = await loadSettings();
        renderOrigins();
        return;
      }
    }
  }
  settings = await loadSettings();
  renderOrigins();
  showFeedback('Odebrano wszystkie uprawnienia stron.', false);
}

function showFeedback(message: string, error: boolean): void {
  feedback.textContent = message;
  feedback.classList.toggle('error', error);
  feedback.classList.remove('hidden');
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Missing options element #${id}.`);
  }
  return element as T;
}
