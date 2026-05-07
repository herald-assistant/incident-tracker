const CLIPBOARD_STYLE_PROPERTIES = [
  'background-color',
  'border',
  'border-bottom',
  'border-left',
  'border-radius',
  'border-right',
  'border-top',
  'box-shadow',
  'box-sizing',
  'color',
  'display',
  'font-family',
  'font-size',
  'font-style',
  'font-weight',
  'gap',
  'letter-spacing',
  'line-height',
  'list-style-position',
  'list-style-type',
  'margin',
  'margin-bottom',
  'margin-left',
  'margin-right',
  'margin-top',
  'max-width',
  'min-width',
  'overflow-wrap',
  'padding',
  'padding-bottom',
  'padding-left',
  'padding-right',
  'padding-top',
  'text-align',
  'text-decoration',
  'text-transform',
  'vertical-align',
  'white-space',
  'word-break'
];

const DEFAULT_COPY_EXCLUDE_SELECTOR = '[data-clipboard-exclude]';

export async function copyTextToClipboard(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return true;
    }
  } catch {
    // Fallback below.
  }

  return copyPlainTextWithLegacySelection(value);
}

export async function copyElementToClipboard(
  element: HTMLElement,
  excludedSelector = DEFAULT_COPY_EXCLUDE_SELECTOR
): Promise<boolean> {
  const clonedElement = cloneElementForClipboard(element, excludedSelector);
  if (!clonedElement) {
    return false;
  }

  const text = clipboardTextFromElement(clonedElement);
  const html = `<!doctype html><html><body>${clonedElement.outerHTML}</body></html>`;

  try {
    if (navigator.clipboard?.write && typeof ClipboardItem !== 'undefined') {
      await navigator.clipboard.write([
        new ClipboardItem({
          'text/html': new Blob([html], { type: 'text/html' }),
          'text/plain': new Blob([text], { type: 'text/plain' })
        })
      ]);
      return true;
    }
  } catch {
    // Rich clipboard may be unavailable outside secure contexts; try legacy copy.
  }

  if (copyRichHtmlWithLegacySelection(clonedElement)) {
    return true;
  }

  return copyTextToClipboard(text);
}

function cloneElementForClipboard(
  element: HTMLElement,
  excludedSelector: string
): HTMLElement | null {
  const clone = cloneNodeWithInlineStyles(element, excludedSelector);
  return clone instanceof HTMLElement ? clone : null;
}

function cloneNodeWithInlineStyles(node: Node, excludedSelector: string): Node | null {
  if (node.nodeType === Node.TEXT_NODE) {
    return document.createTextNode(node.textContent || '');
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return null;
  }

  const sourceElement = node as Element;
  if (sourceElement.matches(excludedSelector)) {
    return null;
  }

  const clonedElement = sourceElement.cloneNode(false) as Element;
  if (sourceElement instanceof HTMLElement && clonedElement instanceof HTMLElement) {
    applyInlineStyles(sourceElement, clonedElement);
  }

  sourceElement.childNodes.forEach((childNode) => {
    const clonedChild = cloneNodeWithInlineStyles(childNode, excludedSelector);
    if (clonedChild) {
      clonedElement.appendChild(clonedChild);
    }
  });

  return clonedElement;
}

function applyInlineStyles(sourceElement: HTMLElement, clonedElement: HTMLElement): void {
  const computedStyle = window.getComputedStyle(sourceElement);
  const declarations = CLIPBOARD_STYLE_PROPERTIES
    .map((property) => {
      const value = computedStyle.getPropertyValue(property);
      return value ? `${property}: ${value}` : '';
    })
    .filter(Boolean);

  if (declarations.length) {
    clonedElement.setAttribute('style', declarations.join('; '));
  }
}

function clipboardTextFromElement(element: HTMLElement): string {
  return (element.innerText || element.textContent || '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function copyRichHtmlWithLegacySelection(element: HTMLElement): boolean {
  const wrapper = document.createElement('div');
  wrapper.style.position = 'fixed';
  wrapper.style.top = '-9999px';
  wrapper.style.left = '-9999px';
  wrapper.style.width = '1px';
  wrapper.style.height = '1px';
  wrapper.style.overflow = 'hidden';
  wrapper.appendChild(element.cloneNode(true));

  try {
    document.body.appendChild(wrapper);
    const selection = window.getSelection();
    if (!selection) {
      return false;
    }

    const range = document.createRange();
    range.selectNodeContents(wrapper);
    selection.removeAllRanges();
    selection.addRange(range);
    const copied = document.execCommand('copy');
    selection.removeAllRanges();
    return copied;
  } catch {
    return false;
  } finally {
    wrapper.remove();
  }
}

function copyPlainTextWithLegacySelection(value: string): boolean {
  try {
    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.setAttribute('readonly', 'true');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    const copied = document.execCommand('copy');
    textarea.remove();
    return copied;
  } catch {
    return false;
  }
}
