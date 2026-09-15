import { describe, expect, it } from 'vitest';
import { inspectablePage, normalizeTdwBaseUrl, registrationIdForOrigin } from './url';

describe('URL boundaries', () => {
  it('accepts http pages and limits permission to their origin', () => {
    expect(inspectablePage('https://crm.example.com/customers/42?tab=profile')).toEqual({
      url: 'https://crm.example.com/customers/42?tab=profile',
      origin: 'https://crm.example.com',
      pathname: '/customers/42',
      permissionPattern: 'https://crm.example.com/*'
    });
  });

  it('rejects protected and non-web pages', () => {
    expect(inspectablePage('chrome://extensions')).toBeNull();
    expect(inspectablePage('file:///C:/example.html')).toBeNull();
    expect(inspectablePage(undefined)).toBeNull();
  });

  it('stores only the TDW origin', () => {
    expect(normalizeTdwBaseUrl(' https://tdw.example.com/ui-explorer ')).toBe(
      'https://tdw.example.com'
    );
    expect(normalizeTdwBaseUrl('javascript:alert(1)')).toBeNull();
  });

  it('creates stable registration ids without leaking the hostname', () => {
    const first = registrationIdForOrigin('https://crm.example.com');
    expect(first).toBe(registrationIdForOrigin('https://crm.example.com'));
    expect(first).not.toContain('crm.example.com');
  });
});
