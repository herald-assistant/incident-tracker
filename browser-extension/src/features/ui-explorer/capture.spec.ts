import { beforeEach, describe, expect, it, vi } from 'vitest';
import { captureUiElement, describeElement } from './capture';

describe('UI Explorer capture preview', () => {
  beforeEach(() => {
    document.title = 'CRM contact profile';
    document.documentElement.lang = 'pl';
    document.body.replaceChildren();
  });

  it('captures semantic ancestry to the document root without form values', () => {
    const main = document.createElement('main');
    const form = document.createElement('form');
    form.id = 'contact-form';
    const label = document.createElement('label');
    label.textContent = 'Kod kontaktu';
    const input = document.createElement('input');
    input.name = 'contactCode';
    input.required = true;
    input.value = 'SECRET-CUSTOMER-VALUE';
    label.append(input);
    form.append(label);
    main.append(form);
    document.body.append(main);
    vi.spyOn(input, 'getBoundingClientRect').mockReturnValue({
      x: 10,
      y: 20,
      left: 10,
      top: 20,
      right: 210,
      bottom: 60,
      width: 200,
      height: 40,
      toJSON: () => ({})
    });

    const capture = captureUiElement(
      input,
      '0.1.0-test',
      'https://crm.example.com/contacts/42?tab=profile&token=forbidden',
      '2026-09-15T10:00:00Z'
    );
    const serialized = JSON.stringify(capture);

    expect(capture.page.queryParameterNames).toEqual(['tab']);
    expect(capture.page.pathname).toBe('/contacts/:value');
    expect(capture.selection.target.text).toBeNull();
    expect(capture.selection.target.state.required).toBe(true);
    expect(capture.selection.ancestors.map((item) => item.tag)).toContain('form');
    expect(capture.selection.traversal.reachedDocumentRoot).toBe(true);
    expect(serialized).not.toContain('SECRET-CUSTOMER-VALUE');
    expect(serialized).not.toContain('forbidden');
  });

  it('captures a disabled button and stable test id', () => {
    const button = document.createElement('button');
    button.disabled = true;
    button.dataset['testid'] = 'contact-save';
    button.textContent = 'Zapisz kontakt';
    document.body.append(button);

    expect(describeElement(button)).toMatchObject({
      tag: 'button',
      role: 'button',
      accessibleName: 'Zapisz kontakt',
      stableAttributes: { 'data-testid': 'contact-save' },
      state: { disabled: true }
    });
  });

  it('drops sensitive and dynamic identifiers', () => {
    const element = document.createElement('div');
    element.id = 'customer-123456789';
    element.className = 'summary token-secret stable-card';
    element.setAttribute('data-testid', 'customer-123456789');
    document.body.append(element);

    const descriptor = describeElement(element);
    expect(descriptor.stableAttributes).toEqual({});
    expect(descriptor.classes).toEqual(['summary', 'stable-card']);
  });

  it('redacts common personal identifiers from visible text', () => {
    const element = document.createElement('p');
    element.textContent = 'Kontakt user@example.com, identyfikator 123456789.';
    document.body.append(element);

    expect(describeElement(element).text).toBe(
      'Kontakt [EMAIL], identyfikator [NUMBER].'
    );
  });

  it('keeps the document root when a semantic ancestry is deeper than the payload limit', () => {
    let parent: Element = document.body;
    for (let index = 0; index < 40; index += 1) {
      const section = document.createElement('section');
      section.setAttribute('aria-label', `Poziom ${index}`);
      parent.append(section);
      parent = section;
    }
    const target = document.createElement('button');
    target.textContent = 'Akcja CRM';
    parent.append(target);

    const capture = captureUiElement(target, '0.1.0-test', 'https://crm.example.com/contact');

    expect(capture.selection.ancestors.length).toBeLessThanOrEqual(24);
    expect(capture.selection.ancestors.at(-1)?.tag).toBe('html');
    expect(capture.selection.traversal.omittedNodeCount).toBeGreaterThan(0);
  });
});
