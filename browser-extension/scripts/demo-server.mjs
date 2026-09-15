import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, resolve, sep } from 'node:path';

const demoRoot = resolve(import.meta.dirname, '..', 'demo');
const port = 4175;
const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8'
};

createServer(async (request, response) => {
  const requestUrl = new URL(request.url ?? '/', `http://localhost:${port}`);
  const relativePath = requestUrl.pathname === '/' ? 'crm-demo.html' : requestUrl.pathname.slice(1);
  const candidate = resolve(demoRoot, relativePath);

  if (candidate !== demoRoot && !candidate.startsWith(`${demoRoot}${sep}`)) {
    response.writeHead(403).end('Forbidden');
    return;
  }

  try {
    const fileStat = await stat(candidate);
    if (!fileStat.isFile()) {
      throw new Error('Not a file');
    }
    response.writeHead(200, {
      'Cache-Control': 'no-store',
      'Content-Type': contentTypes[extname(candidate)] ?? 'application/octet-stream'
    });
    createReadStream(candidate).pipe(response);
  } catch {
    response.writeHead(404).end('Not found');
  }
}).listen(port, '127.0.0.1', () => {
  process.stdout.write(`CRM demo: http://localhost:${port}/crm-demo.html\n`);
  process.stdout.write('Press Ctrl+C to stop.\n');
});
