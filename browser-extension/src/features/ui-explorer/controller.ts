import type { MountedContentFeature } from '../../platform/feature';
import { sendExtensionMessage } from '../../platform/message';
import { captureUiElement, describeElement } from './capture';
import { uiExplorerDummyStartMessage } from './messages';
import { isUiExplorerActivationChord } from './modifier';
import { UI_EXPLORER_STYLES } from './styles';
import type { UiExplorerCapturePreview, UiExplorerDummyAccepted } from './types';

type InteractionState = 'idle' | 'selecting' | 'modal' | 'submitting';

export class UiExplorerController implements MountedContentFeature {
  private readonly host: HTMLDivElement;
  private readonly shadowRoot: ShadowRoot;
  private readonly shield: HTMLDivElement;
  private readonly highlight: HTMLDivElement;
  private readonly highlightLabel: HTMLSpanElement;
  private readonly extensionVersion: string;
  private state: InteractionState = 'idle';
  private target: Element | null = null;
  private modalLayer: HTMLDivElement | null = null;
  private previousFocus: HTMLElement | null = null;
  private pointerX = 0;
  private pointerY = 0;
  private pointerKnown = false;
  private animationFrame: number | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private disposed = false;

  constructor(
    extensionVersion: string,
    options: { readonly shadowMode?: ShadowRootMode } = {}
  ) {
    this.extensionVersion = extensionVersion;
    this.host = document.createElement('div');
    this.host.setAttribute('data-tdw-browser-extension-root', 'ui-explorer');
    this.shadowRoot = this.host.attachShadow({ mode: options.shadowMode ?? 'closed' });

    const style = document.createElement('style');
    style.textContent = UI_EXPLORER_STYLES;
    this.shield = createElement('div', 'tdw-selection-shield');
    this.shield.dataset['active'] = 'false';
    this.shield.setAttribute('aria-hidden', 'true');
    this.highlight = createElement('div', 'tdw-highlight');
    this.highlight.dataset['visible'] = 'false';
    this.highlightLabel = createElement('span', 'tdw-highlight-label');
    this.highlight.append(this.highlightLabel);
    this.shadowRoot.append(style, this.shield, this.highlight);
    document.documentElement.append(this.host);

    window.addEventListener('keydown', this.onKeyDown, true);
    window.addEventListener('keyup', this.onKeyUp, true);
    window.addEventListener('blur', this.onWindowBlur, true);
    window.addEventListener('scroll', this.onViewportChange, true);
    window.addEventListener('resize', this.onViewportChange, true);
    document.addEventListener('visibilitychange', this.onVisibilityChange, true);
    this.shield.addEventListener('pointermove', this.onPointerMove, true);
    this.shield.addEventListener('pointerdown', this.blockShieldEvent, true);
    this.shield.addEventListener('mousedown', this.blockShieldEvent, true);
    this.shield.addEventListener('mouseup', this.blockShieldEvent, true);
    this.shield.addEventListener('contextmenu', this.blockShieldEvent, true);
    this.shield.addEventListener('click', this.onShieldClick, true);

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.updateHighlightGeometry());
    }
  }

  dispose(): void {
    this.disposed = true;
    this.cancelAnimationFrame();
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    window.removeEventListener('keydown', this.onKeyDown, true);
    window.removeEventListener('keyup', this.onKeyUp, true);
    window.removeEventListener('blur', this.onWindowBlur, true);
    window.removeEventListener('scroll', this.onViewportChange, true);
    window.removeEventListener('resize', this.onViewportChange, true);
    document.removeEventListener('visibilitychange', this.onVisibilityChange, true);
    this.shield.removeEventListener('pointermove', this.onPointerMove, true);
    this.shield.removeEventListener('pointerdown', this.blockShieldEvent, true);
    this.shield.removeEventListener('mousedown', this.blockShieldEvent, true);
    this.shield.removeEventListener('mouseup', this.blockShieldEvent, true);
    this.shield.removeEventListener('contextmenu', this.blockShieldEvent, true);
    this.shield.removeEventListener('click', this.onShieldClick, true);
    this.closeModal(false);
    this.host.remove();
    this.state = 'idle';
    this.target = null;
  }

  private readonly onKeyDown = (event: KeyboardEvent): void => {
    if (event.key === 'Escape') {
      if (this.state !== 'idle') {
        consumeEvent(event);
      }
      if (this.state === 'modal' || this.state === 'submitting') {
        if (this.state !== 'submitting') {
          this.closeModal();
        }
      } else {
        this.stopSelecting();
      }
      return;
    }

    if (this.state === 'idle' && isUiExplorerActivationChord(event)) {
      consumeEvent(event);
      this.startSelecting();
    } else if (this.state === 'selecting' && isUiExplorerActivationChord(event)) {
      consumeEvent(event);
    }
  };

  private readonly onKeyUp = (event: KeyboardEvent): void => {
    if (this.state === 'selecting') {
      consumeEvent(event);
      if (!isUiExplorerActivationChord(event)) {
        this.stopSelecting();
      }
    }
  };

  private readonly onWindowBlur = (): void => {
    if (this.state === 'selecting') {
      this.stopSelecting();
    }
  };

  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState !== 'visible' && this.state === 'selecting') {
      this.stopSelecting();
    }
  };

  private readonly onViewportChange = (): void => {
    if (this.state !== 'selecting' || !this.pointerKnown) {
      return;
    }
    this.scheduleTargetUpdate();
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.state !== 'selecting') {
      return;
    }
    this.pointerX = event.clientX;
    this.pointerY = event.clientY;
    this.pointerKnown = true;
    this.scheduleTargetUpdate();
  };

  private readonly blockShieldEvent = (event: Event): void => {
    if (this.state !== 'selecting') {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
  };

  private readonly onShieldClick = (event: MouseEvent): void => {
    this.blockShieldEvent(event);
    if (this.state !== 'selecting' || event.button !== 0 || !this.target) {
      return;
    }

    const capture = captureUiElement(this.target, this.extensionVersion);
    this.hideSelectionVisuals();
    this.state = 'modal';
    this.showModal(capture);
  };

  private startSelecting(): void {
    this.state = 'selecting';
    this.shield.dataset['active'] = 'true';
    this.target = null;
    this.pointerKnown = false;
    this.hideHighlight();
  }

  private stopSelecting(): void {
    if (this.state !== 'selecting') {
      return;
    }
    this.hideSelectionVisuals();
    this.state = 'idle';
  }

  private hideSelectionVisuals(): void {
    this.shield.dataset['active'] = 'false';
    this.target = null;
    this.resizeObserver?.disconnect();
    this.hideHighlight();
  }

  private scheduleTargetUpdate(): void {
    if (this.animationFrame !== null) {
      return;
    }
    this.animationFrame = window.requestAnimationFrame(() => {
      this.animationFrame = null;
      this.updateTargetAtPointer();
    });
  }

  private updateTargetAtPointer(): void {
    if (this.state !== 'selecting' || !this.pointerKnown) {
      return;
    }
    const target = document
      .elementsFromPoint(this.pointerX, this.pointerY)
      .find(
        (element) =>
          element !== this.host &&
          !this.host.contains(element) &&
          element.getRootNode() !== this.shadowRoot
      );

    if (!target || !target.isConnected) {
      this.target = null;
      this.resizeObserver?.disconnect();
      this.hideHighlight();
      return;
    }

    if (this.target !== target) {
      this.resizeObserver?.disconnect();
      this.target = target;
      this.resizeObserver?.observe(target);
    }
    this.updateHighlightGeometry();
  }

  private updateHighlightGeometry(): void {
    if (this.state !== 'selecting' || !this.target?.isConnected) {
      this.hideHighlight();
      return;
    }
    const rect = this.target.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      this.hideHighlight();
      return;
    }
    const descriptor = describeElement(this.target);
    this.highlight.style.left = `${rect.left}px`;
    this.highlight.style.top = `${rect.top}px`;
    this.highlight.style.width = `${rect.width}px`;
    this.highlight.style.height = `${rect.height}px`;
    this.highlightLabel.textContent = [
      descriptor.tag,
      descriptor.role ? `role=${descriptor.role}` : null,
      descriptor.accessibleName
    ]
      .filter(Boolean)
      .join(' · ');
    this.highlight.dataset['visible'] = 'true';
  }

  private hideHighlight(): void {
    this.highlight.dataset['visible'] = 'false';
  }

  private showModal(capture: UiExplorerCapturePreview): void {
    this.previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const layer = createElement('div', 'tdw-modal-layer');
    const panel = createElement('section', 'tdw-modal');
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'tdw-ui-explorer-title');

    const header = createElement('header', 'tdw-modal-header');
    const headingGroup = document.createElement('div');
    const eyebrow = createElement('span', 'tdw-modal-eyebrow', 'TDW Browser Companion');
    const title = createElement('h2', 'tdw-modal-title', 'Zapytaj o zaznaczony element');
    title.id = 'tdw-ui-explorer-title';
    const subtitle = createElement(
      'p',
      'tdw-modal-subtitle',
      'UI Explorer · prototyp interakcji bez polaczenia z backendem'
    );
    headingGroup.append(eyebrow, title, subtitle);
    const closeButton = createButton('tdw-icon-button', '×');
    closeButton.setAttribute('aria-label', 'Zamknij modal');
    closeButton.addEventListener('click', () => this.closeModal());
    header.append(headingGroup, closeButton);

    const form = document.createElement('form');
    form.className = 'tdw-modal-body';
    const banner = createElement(
      'div',
      'tdw-demo-banner',
      'Tryb demo: dane nie opuszczaja przegladarki, a jobId jest generowany lokalnie.'
    );
    banner.setAttribute('role', 'status');

    const target = capture.selection.target;
    const targetLabel = target.accessibleName ?? target.text ?? 'element bez dostepnej nazwy';
    const summary = createElement('div', 'tdw-selection-summary');
    summary.append(
      createElement('strong', '', `${target.tag} · ${target.role ?? 'brak jawnej roli'}`),
      document.createTextNode(targetLabel)
    );

    const question = document.createElement('textarea');
    question.name = 'question';
    question.required = true;
    question.maxLength = 4000;
    question.placeholder = 'Np. Dlaczego ten przycisk jest wyszarzony?';
    const questionField = field('Pytanie lub polecenie', question);

    const model = document.createElement('select');
    addOption(model, 'tdw-default', 'Domyslny model TDW (demo)');
    addOption(model, 'demo-fast', 'Szybki model demonstracyjny');
    addOption(model, 'demo-deep', 'Model do glebszej analizy demonstracyjnej');
    const modelField = field('Model AI', model);

    const effort = document.createElement('select');
    addOption(effort, 'low', 'Low');
    addOption(effort, 'medium', 'Medium');
    addOption(effort, 'high', 'High');
    effort.value = 'medium';
    const effortField = field('Reasoning effort', effort);
    const preferences = createElement('div', 'tdw-grid');
    preferences.append(modelField, effortField);

    const preview = document.createElement('details');
    preview.className = 'tdw-preview';
    const previewSummary = document.createElement('summary');
    previewSummary.textContent = 'Dane capture, ktore trafiłyby do TDW';
    const previewCode = document.createElement('pre');
    previewCode.textContent = JSON.stringify(capture, null, 2);
    preview.append(previewSummary, previewCode);

    const result = createElement('div', 'tdw-result tdw-hidden');
    result.setAttribute('role', 'status');
    result.setAttribute('aria-live', 'polite');

    const actions = createElement('div', 'tdw-actions');
    const cancelButton = createButton('tdw-button tdw-button-secondary', 'Anuluj');
    const submitButton = createButton('tdw-button tdw-button-primary', 'Rozpocznij analize demo');
    submitButton.type = 'submit';
    cancelButton.addEventListener('click', () => this.closeModal());
    actions.append(cancelButton, submitButton);

    form.append(banner, summary, questionField, preferences, preview, result, actions);
    panel.append(header, form);
    layer.append(panel);
    layer.addEventListener('mousedown', (event) => {
      if (event.target === layer && this.state !== 'submitting') {
        this.closeModal();
      }
    });
    panel.addEventListener('keydown', (event) => this.trapFocus(event, panel));
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      if (!form.reportValidity() || this.state === 'submitting') {
        return;
      }
      this.state = 'submitting';
      submitButton.disabled = true;
      cancelButton.disabled = true;
      closeButton.disabled = true;
      submitButton.textContent = 'Uruchamianie…';
      result.classList.add('tdw-hidden');

      void this.startDummyAnalysis({
        question: question.value.trim(),
        model: model.value,
        reasoningEffort: effort.value,
        capture
      }).then((response) => {
        if (this.disposed) {
          return;
        }
        this.state = 'modal';
        submitButton.disabled = false;
        cancelButton.disabled = false;
        closeButton.disabled = false;
        if (!response.ok) {
          submitButton.textContent = 'Sprobuj ponownie';
          showResult(result, 'error', 'Nie udalo sie uruchomic demo', response.error.message);
          return;
        }
        submitButton.disabled = true;
        submitButton.textContent = 'Przyjeto';
        cancelButton.textContent = 'Zamknij';
        showResult(
          result,
          'success',
          'Analiza demonstracyjna rozpoczeta',
          `Status ${response.data.status} · jobId ${response.data.jobId}`
        );
      });
    });

    this.modalLayer = layer;
    this.shadowRoot.append(layer);
    window.setTimeout(() => question.focus({ preventScroll: true }), 0);
  }

  private startDummyAnalysis(payload: Parameters<typeof uiExplorerDummyStartMessage>[0]) {
    return sendExtensionMessage<UiExplorerDummyAccepted>(uiExplorerDummyStartMessage(payload));
  }

  private trapFocus(event: KeyboardEvent, panel: HTMLElement): void {
    if (event.key !== 'Tab') {
      return;
    }
    const focusable = [...panel.querySelectorAll<HTMLElement>(
      'button:not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex="-1"])'
    )];
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0]!;
    const last = focusable.at(-1)!;
    if (event.shiftKey && this.shadowRoot.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && this.shadowRoot.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private closeModal(restoreFocus = true): void {
    this.modalLayer?.remove();
    this.modalLayer = null;
    if (restoreFocus && this.previousFocus?.isConnected) {
      this.previousFocus.focus({ preventScroll: true });
    }
    this.previousFocus = null;
    if (this.state === 'modal' || this.state === 'submitting') {
      this.state = 'idle';
    }
  }

  private cancelAnimationFrame(): void {
    if (this.animationFrame !== null) {
      window.cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
  }
}

function field(labelText: string, control: HTMLElement): HTMLLabelElement {
  const label = document.createElement('label');
  label.className = 'tdw-field';
  label.append(createElement('span', '', labelText), control);
  return label;
}

function createElement<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = '',
  text = ''
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  if (className) {
    element.className = className;
  }
  if (text) {
    element.textContent = text;
  }
  return element;
}

function createButton(className: string, text: string): HTMLButtonElement {
  const button = createElement('button', className, text);
  button.type = 'button';
  return button;
}

function addOption(select: HTMLSelectElement, value: string, label: string): void {
  const option = document.createElement('option');
  option.value = value;
  option.textContent = label;
  select.append(option);
}

function showResult(
  container: HTMLElement,
  kind: 'success' | 'error',
  title: string,
  detail: string
): void {
  container.replaceChildren(createElement('strong', '', title), document.createTextNode(detail));
  container.dataset['kind'] = kind;
  container.classList.remove('tdw-hidden');
}

function consumeEvent(event: Event): void {
  event.preventDefault();
  event.stopPropagation();
  event.stopImmediatePropagation();
}
