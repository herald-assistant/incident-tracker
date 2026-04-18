import { markdownToHtml } from './markdown.utils';

describe('markdownToHtml', () => {
  it('should render bold text, lists, code spans and separators', () => {
    const html = markdownToHtml(`
**Najważniejszy fakt**

- Punkt pierwszy
- Punkt drugi z \`NewLimitOrderDomainRepository\`

---
`);

    expect(html).toContain('<strong>Najważniejszy fakt</strong>');
    expect(html).toContain('<ul>');
    expect(html).toContain('<li>Punkt pierwszy</li>');
    expect(html).toContain('<li>Punkt drugi z <code>NewLimitOrderDomainRepository</code></li>');
    expect(html).toContain('</ul>');
    expect(html).toContain('<hr>');
  });

  it('should sanitize unsafe html from markdown input', () => {
    const html = markdownToHtml(`
Bezpieczna treść
<script>alert('xss')</script>
`);

    expect(html).toContain('<p>Bezpieczna treść</p>');
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('alert(');
  });

  it('should return a fallback paragraph when content is empty', () => {
    expect(markdownToHtml('')).toBe('<p>Brak treści.</p>');
  });
});
