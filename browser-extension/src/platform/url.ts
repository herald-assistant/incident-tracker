export interface InspectablePage {
  readonly url: string;
  readonly origin: string;
  readonly pathname: string;
  readonly permissionPattern: string;
}

export function inspectablePage(rawUrl: string | undefined): InspectablePage | null {
  if (!rawUrl) {
    return null;
  }
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }
    return {
      url: url.href,
      origin: url.origin,
      pathname: url.pathname,
      permissionPattern: `${url.origin}/*`
    };
  } catch {
    return null;
  }
}

export function normalizeTdwBaseUrl(rawValue: string): string | null {
  try {
    const url = new URL(rawValue.trim());
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }
    return url.origin;
  } catch {
    return null;
  }
}

export function registrationIdForOrigin(origin: string): string {
  let hash = 2166136261;
  for (let index = 0; index < origin.length; index += 1) {
    hash ^= origin.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `tdw-origin-${(hash >>> 0).toString(16).padStart(8, '0')}`;
}
