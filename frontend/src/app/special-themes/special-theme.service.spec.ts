import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { SpecialThemeService } from './special-theme.service';

const DISABLED_KEY = 'team-delivery-workspace.special-themes.disabled';

describe('SpecialThemeService', () => {
  let themeDocument: Document;

  beforeEach(() => {
    localStorage.removeItem(DISABLED_KEY);
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    themeDocument = document.implementation.createHTMLDocument();
    Object.defineProperty(themeDocument, 'defaultView', { value: window });
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: themeDocument }]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    localStorage.removeItem(DISABLED_KEY);
  });

  it.each([
    [new Date(2026, 11, 14, 23, 59), null],
    [new Date(2026, 11, 15), 'christmas'],
    [new Date(2027, 0, 6, 23, 59), 'christmas'],
    [new Date(2027, 0, 7), null]
  ])('resolves the theme for the local day %s', (day, expected) => {
    vi.setSystemTime(day);
    const service = TestBed.inject(SpecialThemeService);
    TestBed.tick();

    expect(service.activeTheme()?.id ?? null).toBe(expected);
    expect(themeDocument.documentElement.getAttribute('data-special-theme')).toBe(expected);
  });

  it('clears the theme at midnight with the tab still open', () => {
    vi.setSystemTime(new Date(2027, 0, 6, 23, 59, 59));
    const service = TestBed.inject(SpecialThemeService);
    expect(service.activeTheme()?.id).toBe('christmas');

    vi.advanceTimersByTime(1000);
    TestBed.tick();

    expect(service.activeTheme()).toBeUndefined();
    expect(themeDocument.documentElement.hasAttribute('data-special-theme')).toBe(false);
  });

  it('honors an existing opt-out and clears it when re-enabled', () => {
    vi.setSystemTime(new Date(2026, 11, 20));
    localStorage.setItem(DISABLED_KEY, 'true');
    const service = TestBed.inject(SpecialThemeService);

    expect(service.enabled()).toBe(false);
    expect(service.activeTheme()).toBeUndefined();

    service.setEnabled(true);
    expect(localStorage.getItem(DISABLED_KEY)).toBeNull();
    expect(service.activeTheme()?.id).toBe('christmas');

    service.setEnabled(false);
    expect(localStorage.getItem(DISABLED_KEY)).toBe('true');
    expect(service.activeTheme()).toBeUndefined();
  });

  it('applies a preference change from another tab', () => {
    vi.setSystemTime(new Date(2026, 11, 20));
    const service = TestBed.inject(SpecialThemeService);

    localStorage.setItem(DISABLED_KEY, 'true');
    window.dispatchEvent(new StorageEvent('storage', { key: DISABLED_KEY }));

    expect(service.enabled()).toBe(false);
    expect(service.activeTheme()).toBeUndefined();
  });
});
