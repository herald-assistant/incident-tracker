import {AssistantAuthProfile, AssistantAuthType} from './system-assistant.models';

export const AUTH_TYPES = [
  {value: 'BASIC', label: 'Basic', hint: 'Login i hasło'},
  {value: 'BEARER', label: 'Bearer', hint: 'Gotowy token dostępu'},
  {value: 'OAUTH2', label: 'OAuth2', hint: 'Konto techniczne · Client Credentials'},
  {value: 'OIDC', label: 'OIDC', hint: 'Logowanie użytkownika, np. przez Keycloak'}
];

export function newAuthProfile(type: AssistantAuthType, id: string, name: string, origin: string): AssistantAuthProfile {
  return {id, name, type, username: null, password: null, token: null, tokenUrl: null, clientId: null, clientSecret: null,
    scopes: type === 'OIDC' ? 'openid profile' : null, issuerUrl: null,
    redirectUri: type === 'OIDC' ? `${origin}/api/system-assistant/auth/callback` : null,
    clientAuthentication: type === 'OIDC' ? 'none' : type === 'OAUTH2' ? 'client_secret_basic' : null};
}

export function authErrors(profile: AssistantAuthProfile): Record<string, string> {
  const errors: Record<string, string> = {};
  const required = (key: keyof AssistantAuthProfile, message: string) => { if (!profile[key]?.trim()) errors[key] = message; };
  required('name', 'Podaj nazwę uwierzytelniania.');
  if (profile.name.length > 100) errors['name'] = 'Nazwa może mieć maksymalnie 100 znaków.';
  const url = (key: 'tokenUrl' | 'issuerUrl' | 'redirectUri') => {
    try { const value = new URL(profile[key] || ''); if (!['http:', 'https:'].includes(value.protocol) || value.username || value.password || value.search || value.hash) throw new Error(); }
    catch { errors[key] = 'Podaj pełny adres HTTP(S) bez query i fragmentu.'; }
  };
  if (profile.type === 'BASIC') {
    required('username', 'Podaj login.'); required('password', 'Podaj hasło.');
    if (profile.username?.includes(':')) errors['username'] = 'Login nie może zawierać dwukropka.';
  } else if (profile.type === 'BEARER') {
    required('token', 'Podaj token.');
    if (profile.token && !/^[A-Za-z0-9._~+/=-]+$/.test(profile.token)) errors['token'] = 'Wklej sam token, bez prefiksu Bearer i spacji.';
  } else if (profile.type === 'OAUTH2' || profile.type === 'OIDC') {
    required('clientId', 'Podaj client ID.');
    if (profile.type === 'OAUTH2') { url('tokenUrl'); required('clientSecret', 'Podaj client secret.'); }
    else {
      url('issuerUrl'); url('redirectUri');
      if (!profile.scopes?.trim().split(/\s+/).includes('openid')) errors['scopes'] = 'Scopes muszą zawierać openid.';
      if (profile.redirectUri && !profile.redirectUri.endsWith('/api/system-assistant/auth/callback')) errors['redirectUri'] = 'Adres powrotu musi wskazywać /api/system-assistant/auth/callback w TDW.';
    }
    const methods = profile.type === 'OIDC' && !profile.clientSecret?.trim() ? ['none'] : ['client_secret_basic', 'client_secret_post'];
    if (!methods.includes(profile.clientAuthentication || '')) errors['clientAuthentication'] = 'Wybierz sposób uwierzytelniania zgodny z sekretem klienta.';
  } else errors['type'] = 'Wybierz obsługiwany typ uwierzytelniania.';
  for (const [field, value] of Object.entries(profile)) {
    if (typeof value === 'string' && (/[\r\n]/.test(value) || value.length > 16384)) errors[field] = 'Pole jest zbyt długie lub zawiera znak nowej linii.';
  }
  return errors;
}
