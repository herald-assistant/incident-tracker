import { Pipe, PipeTransform } from '@angular/core';

const ACRONYMS_BY_TOKEN: Record<string, string> = {
  ai: 'AI',
  api: 'API',
  db: 'DB',
  gitlab: 'GitLab',
  http: 'HTTP',
  https: 'HTTPS',
  id: 'ID',
  sql: 'SQL',
  uri: 'URI',
  url: 'URL'
};

@Pipe({
  name: 'attributeName',
  standalone: true
})
export class AttributeNamePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatAttributeName(value);
  }
}

export function formatAttributeName(value: string | null | undefined): string {
  const normalized = String(value ?? '').trim();

  if (!normalized) {
    return '';
  }

  const hasExplicitWordBoundaries = /[_-]/.test(normalized);
  const hasCamelCaseBoundaries =
    /([A-Z]+)([A-Z][a-z])/.test(normalized) || /([a-z0-9])([A-Z])/.test(normalized);

  if (/\s/.test(normalized) && !hasExplicitWordBoundaries && !hasCamelCaseBoundaries) {
    return normalized;
  }

  return normalized
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .map(formatToken)
    .join(' ');
}

function formatToken(token: string): string {
  const normalizedToken = token.toLowerCase();
  const acronym = ACRONYMS_BY_TOKEN[normalizedToken];

  if (acronym) {
    return acronym;
  }

  return normalizedToken.charAt(0).toUpperCase() + normalizedToken.slice(1);
}
