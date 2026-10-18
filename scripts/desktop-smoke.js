const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');

// Exercita a checagem real de disponibilidade sem abrir o banco ou a janela do usuário.
let statusCode = 200;
let body = '{"status":"UP"}';
const context = vm.createContext({
  __dirname: path.resolve(__dirname, '..', 'desktop'),
  process,
  setTimeout,
  require(name) {
    if (name === 'electron') return { app: {} };
    if (name === 'http') return {
      get(_url, callback) {
        const response = new EventEmitter();
        response.statusCode = statusCode;
        const request = new EventEmitter();
        request.setTimeout = () => {};
        request.destroy = () => {};
        callback(response);
        process.nextTick(() => { response.emit('data', body); response.emit('end'); });
        return request;
      },
    };
    return require(name);
  },
});
const source = fs.readFileSync(path.join(__dirname, '..', 'desktop', 'main.js'), 'utf8');
vm.runInContext(source.slice(0, source.indexOf('const gotSingleInstanceLock')), context);
vm.runInContext('backendUrl = "http://127.0.0.1:12345";', context);

(async () => {
  for (const caso of [
    [200, '{"status":"UP"}', true],
    [200, '{"status":"DOWN"}', false],
    [404, '{"status":"UP"}', false],
    [503, '{"status":"DOWN"}', false],
    [200, '<html>iniciando</html>', false],
  ]) {
    [statusCode, body] = caso;
    assert.equal(await context.pingBackend(), caso[2], 'Verificacao de disponibilidade: ' + statusCode + ' ' + body);
  }
  console.log('desktop smoke OK — backend pronto, indisponivel, erro HTTP e resposta invalida');
})().catch((error) => { console.error(error); process.exitCode = 1; });
