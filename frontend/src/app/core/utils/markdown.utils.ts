import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.use({
  async: false,
  breaks: true,
  gfm: true
});

export function markdownToHtml(markdown: string | null | undefined): string {
  const normalized = String(markdown || '').replace(/\r\n/g, '\n').trim();
  if (!normalized) {
    return '<p>Brak treści.</p>';
  }

  const rendered = marked.parse(normalized) as string;
  const sanitized = DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true }
  }).trim();

  return sanitized || '<p>Brak treści.</p>';
}
