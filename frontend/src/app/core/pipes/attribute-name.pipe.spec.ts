import { formatAttributeName } from './attribute-name.pipe';

describe('formatAttributeName', () => {
  it('should split camelCase attribute names into title case labels', () => {
    expect(formatAttributeName('toolName')).toBe('Tool Name');
    expect(formatAttributeName('toolCaptureOrder')).toBe('Tool Capture Order');
    expect(formatAttributeName('recommendedNextReadCount')).toBe('Recommended Next Read Count');
  });

  it('should format common acronyms without losing word boundaries', () => {
    expect(formatAttributeName('displayId')).toBe('Display ID');
    expect(formatAttributeName('apiResponseUrl')).toBe('API Response URL');
    expect(formatAttributeName('sqlQuery')).toBe('SQL Query');
  });

  it('should support snake and kebab case keys', () => {
    expect(formatAttributeName('gitlab_find_flow_context')).toBe('GitLab Find Flow Context');
    expect(formatAttributeName('database-alias')).toBe('Database Alias');
  });

  it('should keep labels that are already human readable', () => {
    expect(formatAttributeName('Rola pliku')).toBe('Rola pliku');
    expect(formatAttributeName('Tool Name')).toBe('Tool Name');
  });
});
