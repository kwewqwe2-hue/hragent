import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';

const port = Number(process.env.PORT || 3000);
const maxBytes = 50 * 1024 * 1024;
const knowledgeRoot = path.resolve('/knowledge-files');

class DOMMatrixPolyfill {
  constructor() {
    this.a = 1;
    this.b = 0;
    this.c = 0;
    this.d = 1;
    this.e = 0;
    this.f = 0;
  }
}

globalThis.DOMMatrix ??= DOMMatrixPolyfill;
globalThis.ImageData ??= class ImageData {};
globalThis.Path2D ??= class Path2D {};

const pnpmRoot = '/usr/local/lib/node_modules/n8n/node_modules/.pnpm';
const pdfPackage = fs.readdirSync(pnpmRoot).find((name) => name.startsWith('pdfjs-dist@'));
if (!pdfPackage) throw new Error('pdfjs-dist is not available in the n8n image');

const pdfJsPath = path.join(pnpmRoot, pdfPackage, 'node_modules/pdfjs-dist/legacy/build/pdf.mjs');
const { getDocument } = await import(pdfJsPath);

function sendJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
  });
  response.end(body);
}

async function readBody(request) {
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > maxBytes) throw new Error('Request body is too large');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function parseJson(body) {
  try {
    return JSON.parse(body.toString('utf8'));
  } catch {
    throw new Error('Request body must be JSON');
  }
}

function safeKnowledgeFileName(value) {
  const fileName = String(value || '').trim();
  if (!fileName || fileName === '.' || fileName === '..' || fileName !== path.basename(fileName)) {
    throw new Error('fileName must be a single file name');
  }
  if (fileName.includes('\0') || fileName.includes('..')) throw new Error('invalid fileName');
  const filePath = path.resolve(knowledgeRoot, fileName);
  if (path.dirname(filePath) !== knowledgeRoot) throw new Error('invalid file path');
  return { fileName, filePath };
}

async function extractText(pdfBytes) {
  const document = await getDocument({ data: new Uint8Array(pdfBytes), disableWorker: true }).promise;
  const pages = [];
  for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
    const page = await document.getPage(pageNumber);
    const content = await page.getTextContent();
    pages.push(content.items.map((item) => item.str || '').join(' '));
  }
  return { pages: document.numPages, text: pages.join('\n').trim() };
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === 'POST' && request.url === '/delete') {
      const payload = parseJson(await readBody(request));
      const { fileName, filePath } = safeKnowledgeFileName(payload.fileName);
      const existed = fs.existsSync(filePath);
      if (existed) fs.unlinkSync(filePath);
      sendJson(response, 200, { success: true, fileName, deleted: existed });
      return;
    }

    if (request.method !== 'POST' || request.url !== '/extract') {
      sendJson(response, 404, { success: false, message: 'Not found' });
      return;
    }

    const payload = parseJson(await readBody(request));
    if (!payload.data) throw new Error('PDF data is required');
    const result = await extractText(Buffer.from(payload.data, 'base64'));
    if (!result.text) {
      sendJson(response, 422, { success: false, message: 'PDF has no searchable text; OCR is required', pages: result.pages });
      return;
    }
    sendJson(response, 200, { success: true, fileName: payload.fileName || '', pages: result.pages, text: result.text });
  } catch (error) {
    sendJson(response, 400, { success: false, message: error instanceof Error ? error.message : String(error) });
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`PDF parser listening on ${port}`);
});
