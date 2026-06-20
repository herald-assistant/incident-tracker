export async function readJsonFile(file: File, invalidJsonMessage: string): Promise<unknown> {
  const content = await file.text();
  try {
    return JSON.parse(content) as unknown;
  } catch {
    throw new Error(invalidJsonMessage);
  }
}

export function downloadJsonFile(fileName: string, payload: unknown): void {
  const blob = new Blob([JSON.stringify(payload, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function sanitizeFileNamePart(value: string): string {
  const normalized = String(value || '')
    .trim()
    .replace(/[^a-zA-Z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalized ? normalized.substring(0, 48) : 'snapshot';
}

export function formatFileTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'snapshot';
  }

  return (
    [date.getFullYear(), pad2(date.getMonth() + 1), pad2(date.getDate())].join('') +
    '-' +
    [pad2(date.getHours()), pad2(date.getMinutes()), pad2(date.getSeconds())].join('')
  );
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}
