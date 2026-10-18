const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..');

async function runBackendAndUI() {
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const jar = path.join(root, 'wheel', 'target', 'wheel-0.0.1-SNAPSHOT.jar');
  assert.ok(fs.existsSync(jar), 'Gere o backend antes: npm run jar');
  const backend = spawn(java, ['-jar', jar, '--server.port=0', '--spring.datasource.url=jdbc:h2:mem:system-smoke', '--spring.jpa.hibernate.ddl-auto=create-drop'], {
    cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
  });
  let ui;
  let log = '';
  try {
    const port = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Backend nao iniciou em 60 segundos: ' + log.slice(-2000))), 60000);
      backend.once('error', (error) => { clearTimeout(timer); reject(error); });
      backend.once('exit', (code) => { clearTimeout(timer); reject(new Error('Backend encerrou: ' + code + '\n' + log.slice(-2000))); });
      backend.stdout.on('data', (chunk) => {
        log += chunk;
        const match = log.match(/Tomcat started on port (\d+)/);
        if (match) { clearTimeout(timer); resolve(Number(match[1])); }
      });
      backend.stderr.on('data', (chunk) => { log += chunk; });
    });
    const env = { ...process.env, SOFTDARF_TEST_API: 'http://127.0.0.1:' + port };
    delete env.ELECTRON_RUN_AS_NODE;
    ui = spawn(require('electron'), [__filename], { cwd: root, env, windowsHide: true, stdio: 'inherit' });
    const code = await new Promise((resolve, reject) => { ui.once('error', reject); ui.once('exit', resolve); });
    assert.equal(code, 0, 'Teste da interface falhou');
  } finally {
    if (ui && ui.exitCode === null) ui.kill();
    backend.kill();
  }
}

async function runUI() {
  const { app, BrowserWindow } = require('electron');
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'softdarf-system-test-'));
  app.setPath('userData', profile);
  await app.whenReady();
  const win = new BrowserWindow({ show: false, width: 1280, height: 900, webPreferences: { contextIsolation: true, nodeIntegration: false } });
  const errors = [];
  win.webContents.on('console-message', (details) => { if (details.level === 'error' && /Uncaught|ReferenceError|TypeError/.test(details.message)) errors.push(details.message); });
  await win.loadFile(path.join(root, 'teste.html'), { query: { apiBase: process.env.SOFTDARF_TEST_API } });
  const evaluate = (fn, ...args) => win.webContents.executeJavaScript('(' + fn.toString() + ')(...' + JSON.stringify(args) + ')', true);
  const wait = async (fn) => {
    const started = Date.now();
    while (!(await evaluate(fn))) {
      if (Date.now() - started > 20000) throw new Error('Tela nao concluiu o carregamento');
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  };
  try {
    assert.equal(await evaluate(() => document.getElementById('auth-screen').style.display), 'block');
    const login = 'desktop-test-' + Date.now();
    await evaluate(async (login) => {
      // Respostas de mercado determinísticas; cadastro, login e carteiras usam o backend real.
      const original = window.fetch;
      window.fetch = function(url, options) {
        const target = String(url);
        if (target.includes('/mercado/cambio/dolar')) return Promise.resolve(new Response(JSON.stringify({taxa:5, origem:'Teste'}), {status:200}));
        if (target.includes('/mercado/cripto') || target.includes('/cotacoes')) return Promise.resolve(new Response('[]', {status:200}));
        if (target.includes('/proventos')) return Promise.resolve(new Response('{"eventos":[],"fonte":"Teste"}', {status:200}));
        return original(url, options);
      };
      document.getElementById('cadastro-nome').value = 'Conta fictícia do teste';
      document.getElementById('cadastro-login').value = login;
      document.getElementById('cadastro-senha').value = 'senha-de-teste';
      await criarUsuario();
    }, login);
    await wait(() => !!usuarioAtual && !dadosUsuarioCarregando);
    assert.equal(await evaluate(() => historicoCompleto.length), 0);
    const firstUser = await evaluate(() => usuarioAtual.id);
    await evaluate(async () => {
      navegarPara('br');
      document.getElementById('mov-data').value = '2026-10-01';
      document.getElementById('mov-tipo').value = 'APORTE';
      document.getElementById('mov-valor').value = '1000';
      await salvarMovimentoCarteira();
    });
    assert.equal(await evaluate(() => carteiraMovimentos.length), 1);
    assert.equal(await evaluate(() => snapshotCarteiraPatrimonial([]).aporteLiquido), 1000);
    await evaluate(async () => {
      navegarPara('cripto');
      document.getElementById('crypto-symbol').value = 'btc';
      document.getElementById('crypto-quantity').value = '0.125';
      document.getElementById('crypto-cost-usd').value = '50000';
      await salvarPosicaoCripto();
    });
    assert.equal(await evaluate(() => criptoPosicoes.length), 1);
    assert.equal(await evaluate(() => document.getElementById('crypto-list').textContent.includes('BTC')), true);
    for (const page of ['inicio', 'br', 'us', 'cripto']) {
      await evaluate((page) => navegarPara(page), page);
      assert.equal(await evaluate(() => document.body.getAttribute('data-page')), page);
    }
    await evaluate(() => { alternarModoPrivacidade(); alternarModoPrivacidade(); });
    await evaluate(() => sair());
    assert.equal(await evaluate(() => usuarioAtual), null);
    await evaluate(async (login) => {
      document.getElementById('login-login').value = login;
      document.getElementById('login-senha').value = 'senha-de-teste';
      await entrar();
    }, login);
    await wait(() => !!usuarioAtual && !dadosUsuarioCarregando);
    assert.equal(await evaluate(() => usuarioAtual.id), firstUser);
    assert.equal(await evaluate(() => movimentosPorMercado.BR.length), 1);
    assert.equal(await evaluate(() => criptoPosicoes.length), 1);
    await evaluate(() => sair());
    await evaluate(async (login) => {
      document.getElementById('cadastro-login').value = login + '-segunda';
      await criarUsuario();
    }, login);
    await wait(() => !!usuarioAtual && !dadosUsuarioCarregando);
    assert.equal(await evaluate(() => movimentosPorMercado.BR.length + criptoPosicoes.length + historicoCompleto.length), 0);
    assert.equal(await evaluate(async (id) => (await fetch(API_URL + '?usuarioId=' + id)).status, firstUser), 403);
    assert.deepEqual(errors, []);
    fs.mkdirSync(path.join(root, '.tmp'), { recursive: true });
    fs.writeFileSync(path.join(root, '.tmp', 'system-ui.png'), (await win.webContents.capturePage()).toPNG());
    console.log('system smoke OK — cadastro, login, aporte, cripto, navegacao, privacidade, recarga e isolamento entre contas');
    win.destroy();
    app.exit(0);
  } catch (error) {
    console.error(error);
    app.exit(1);
  }
}

if (process.versions.electron) runUI().catch((error) => { console.error(error); require('electron').app.exit(1); });
else runBackendAndUI().catch((error) => { console.error(error); process.exitCode = 1; });
