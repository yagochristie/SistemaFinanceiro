const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { createRequire } = require('node:module');

// Usa o downloader realmente resolvido pelo empacotador, sem acessar a internet.
// O domínio .invalid só responde se a requisição passar pelo proxy local do teste.
const payload = Buffer.from('SoftDarf: artefato ficticio para teste de download\n');
const checksum = crypto.createHash('sha256').update(payload).digest('hex');
const tempRoot = path.resolve(__dirname, '..', '.tmp', 'proxy-tests');
fs.mkdirSync(tempRoot, { recursive: true });
const tempDir = fs.mkdtempSync(path.join(tempRoot, 'download-'));
let originRequests = 0;
const proxyRequests = [];
const origin = http.createServer((_req, res) => { originRequests++; res.end(payload); });
const proxy = http.createServer((req, res) => { proxyRequests.push(req.url); res.end(payload); });

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve(server.address().port));
  });
}

async function main() {
  try {
    const originPort = await listen(origin);
    const proxyPort = await listen(proxy);
    process.env.ELECTRON_GET_USE_PROXY = '1';
    process.env.ELECTRON_GET_NO_PROGRESS = '1';
    process.env.GLOBAL_AGENT_HTTP_PROXY = 'http://127.0.0.1:' + proxyPort;
    process.env.GLOBAL_AGENT_HTTPS_PROXY = process.env.GLOBAL_AGENT_HTTP_PROXY;
    process.env.GLOBAL_AGENT_NO_PROXY = '127.0.0.1';
    delete process.env.GLOBAL_AGENT_ENVIRONMENT_VARIABLE_NAMESPACE;

    const builderRequire = createRequire(require.resolve('app-builder-lib/package.json'));
    const downloader = builderRequire('@electron/get');
    assert.equal(global.GLOBAL_AGENT.HTTP_PROXY, process.env.GLOBAL_AGENT_HTTP_PROXY, 'Proxy precisa inicializar pelo @electron/get');
    const artifactName = 'security-fixture-v44.9.0-win32-x64.zip';
    const download = (url, expected = checksum) => downloader.downloadArtifact({
      version: '44.9.0', artifactName: 'security-fixture', platform: 'win32', arch: 'x64',
      tempDirectory: tempDir, cacheRoot: tempDir, cacheMode: downloader.ElectronDownloadCacheMode.Bypass,
      checksums: { [artifactName]: expected },
      mirrorOptions: { resolveAssetURL: async () => url },
      downloadOptions: { quiet: true, retry: { limit: 0 }, timeout: { request: 5000 } },
    });

    const proxiedURL = 'http://download.softdarf.invalid/fixture.zip';
    assert.deepEqual(fs.readFileSync(await download(proxiedURL)), payload);
    assert.deepEqual(proxyRequests, [proxiedURL]);
    assert.equal(originRequests, 0);

    assert.deepEqual(fs.readFileSync(await download('http://127.0.0.1:' + originPort + '/fixture.zip')), payload);
    assert.equal(originRequests, 1, 'NO_PROXY precisa preservar o download direto');
    assert.equal(proxyRequests.length, 1);

    await assert.rejects(download(proxiedURL, '0'.repeat(64)), /checksum/i);
    assert.equal(proxyRequests.length, 2);
    console.log('download proxy smoke OK — proxy, NO_PROXY, integridade SHA-256 e rejeicao de arquivo alterado');
  } finally {
    origin.closeAllConnections();
    proxy.closeAllConnections();
    await Promise.all([origin, proxy].map(server => new Promise(resolve => server.close(resolve))));
    const resolvedTemp = path.resolve(tempDir);
    assert.equal(path.dirname(resolvedTemp), tempRoot);
    assert.ok(path.basename(resolvedTemp).startsWith('download-'));
    fs.rmSync(resolvedTemp, { recursive: true, force: true });
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
