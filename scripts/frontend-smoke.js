const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const html = fs.readFileSync('teste.html', 'utf8');
const script = (html.match(/<script>([\s\S]*)<\/script>/) || [])[1] || '';

new Function(script);

const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
assert.deepStrictEqual(duplicateIds, [], 'HTML must not contain duplicate ids');

const functionNames = [...script.matchAll(/function\s+([A-Za-z0-9_$]+)\s*\(/g)].map((match) => match[1]);
const duplicateFunctions = [...new Set(functionNames.filter((name, index) => functionNames.indexOf(name) !== index))];
assert.deepStrictEqual(duplicateFunctions, [], 'Global function names must not be duplicated');
assert.ok(html.includes('id="etf-rf-toggle"'), 'ETF RF panel should have a dropdown toggle');
assert.ok(html.includes('onclick="alternarDropdownEtfRendaFixa()"'), 'ETF RF dropdown toggle must call the toggle handler');
assert.ok(script.includes('function alternarDropdownEtfRendaFixa'), 'ETF RF dropdown handler must exist');
assert.ok(html.includes('id="privacy-toggle"'), 'Privacy mode button should exist in the top bar');
assert.ok(html.includes('onclick="alternarModoPrivacidade()"'), 'Privacy mode button must call the toggle handler');
assert.ok(script.includes('function alternarModoPrivacidade'), 'Privacy mode toggle handler must exist');
assert.ok(script.includes('function aplicarModoPrivacidade'), 'Privacy mode mask handler must exist');
assert.ok(script.includes('const CRIPTO_ATUALIZACAO_MS = 60000'), 'Crypto quotes must refresh every minute');
assert.ok(html.includes('Preço Coinbase'), 'Crypto table should identify Coinbase as the price source');
assert.ok(script.includes('precoMedioUsd'), 'Crypto positions must support an optional USD average cost');
[
  '#overview-dashboard .value',
  '#overview-cambio',
  '#overview-proventos-note',
  '#overview-allocation-total',
  '#overview-chart text',
  '#overview-allocation .percent',
  '#overview-dividend-list .dividend-value',
  '#crypto-list .crypto-money',
  '#crypto-list .crypto-row > div:nth-child(4) .crypto-caption',
  '#crypto-history .crypto-history-line span:last-child'
].forEach((selector) => {
  assert.ok(script.includes("'" + selector + "'"), 'Privacy mode must mask home value selector ' + selector);
});
assert.ok(html.includes('id="monthly-return-help-modal"'), 'Monthly return help dialog should exist');
assert.ok(script.includes('function abrirAjudaRetornoMensal'), 'Monthly return help handler must exist');
assert.ok(script.includes('wallet-return-help'), 'Monthly return percentage should be interactive');

const el = {
  style: {},
  classList: {
    add() {},
    remove() {},
    toggle() {},
    contains() { return false; },
  },
  addEventListener() {},
  querySelector() { return null; },
  setAttribute() {},
  focus() {},
  select() {},
  scrollIntoView() {},
  value: '',
  textContent: '',
  innerHTML: '',
};
const storageData = {};
const storage = {
  getItem(key) { return Object.prototype.hasOwnProperty.call(storageData, key) ? storageData[key] : null; },
  setItem(key, value) { storageData[key] = String(value); },
  removeItem(key) { delete storageData[key]; },
};
let respostaRede = async () => ({ ok: false, json: async () => [] });
const sandbox = {
  console,
  Date,
  setTimeout,
  setInterval,
  clearInterval,
  alert() {},
  confirm() { return true; },
  fetch: (...args) => respostaRede(...args),
  Blob,
  Response,
  DecompressionStream,
  TextDecoder,
  Uint8Array,
  ArrayBuffer,
  URLSearchParams,
  Math,
  Number,
  String,
  RegExp,
  Object,
  JSON,
  parseFloat,
  parseInt,
  isFinite,
  encodeURIComponent,
  decodeURIComponent,
  localStorage: storage,
  sessionStorage: storage,
  document: {
    addEventListener() {},
    body: { style: {}, setAttribute() {}, removeAttribute() {} },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() { return el; },
    getElementById() { return el; },
  },
};
sandbox.window = sandbox;
sandbox.location = { search: '' };
vm.createContext(sandbox);
vm.runInContext(script, sandbox);

sandbox.mercadoAtivo = 'US';
sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-09-01', tipo: 'APORTE', valor: 4000 },
];
sandbox.cotacoesCarteira = {
  'US|NU': { ticker: 'NU', mercado: 'US', preco: 14, recebidaEm: Date.now() },
};
const coveredCallOpen = [{
  id: 501,
  mercado: 'US',
  ticker: 'NU',
  putExercida: 'Carteira',
  callExercida: 'Pendente',
  callPremio: 1,
  callTaxas: 0,
  callStrike: 18,
  callVencimento: '2099-12-18',
  acaoCompra: 10,
  acaoQtd: 100,
  acaoQtdAtual: 100,
  data: '01/09/2026',
}];
const usWalletSnapshot = sandbox.snapshotCarteiraPatrimonial(coveredCallOpen);
assert.strictEqual(usWalletSnapshot.saldoCaixaEstimado, 3100, 'US wallet cash should include stock cost and option cash flow');
assert.strictEqual(usWalletSnapshot.valorMercado, 1400, 'US wallet market value should use current stock quotes');
assert.strictEqual(usWalletSnapshot.patrimonio, 4500, 'US wallet patrimony should be current cash plus market value');
assert.notStrictEqual(
  usWalletSnapshot.patrimonio,
  usWalletSnapshot.aporteLiquido + usWalletSnapshot.potencialAbertoUS,
  'US wallet patrimony must not be the covered-call exercise projection'
);

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-09-01', tipo: 'APORTE', valor: 2000 },
];
sandbox.cotacoesCarteira = {
  'US|AAPL': { ticker: 'AAPL', mercado: 'US', preco: 120, recebidaEm: Date.now() },
};
const stockWalletSnapshot = sandbox.snapshotCarteiraPatrimonial([{
  id: 502,
  mercado: 'US',
  tipoAtivo: 'ACAO',
  ticker: 'AAPL',
  putExercida: 'Carteira',
  acaoCompra: 100,
  acaoTaxaCompra: 1,
  acaoQtd: 10,
  acaoQtdAtual: 10,
  data: '01/09/2026',
}]);
assert.strictEqual(stockWalletSnapshot.saldoCaixaEstimado, 999, 'US stock purchases should reduce estimated cash');
assert.strictEqual(stockWalletSnapshot.patrimonio, 2199, 'US stock patrimony should combine remaining cash and market value');

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-09-01', tipo: 'APORTE', valor: 1000, id: 1 },
  { usuarioId: 42, mercado: 'US', data: '2026-09-02', tipo: 'RETIRADA', valor: 200, id: 2 },
  { usuarioId: 42, mercado: 'US', data: '2026-09-03', tipo: 'AJUSTE', valor: 5000, id: 3 },
  { usuarioId: 42, mercado: 'US', data: '2026-09-04', tipo: 'SALDO_REAL', valor: 9999, id: 4 },
];
const legacyUsSnapshot = sandbox.snapshotCarteiraPatrimonial([]);
assert.strictEqual(legacyUsSnapshot.patrimonio, 9999, 'US wallet should use the latest saved broker net liq when available');
assert.strictEqual(legacyUsSnapshot.patrimonioFonteCorretoraUS, true, 'US wallet should mark broker net liq as the patrimony source');

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-08-01', tipo: 'APORTE', valor: 4424.15, id: 1 },
  { usuarioId: 42, mercado: 'US', data: '2026-09-10', tipo: 'SALDO_REAL', valor: 4826.99, id: 2 },
];
const currentBrokerBalanceSnapshot = sandbox.snapshotCarteiraPatrimonial([]);
assert.strictEqual(currentBrokerBalanceSnapshot.patrimonio, 4826.99, 'US current patrimony must match the saved Account Net Liq');

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-09-10', tipo: 'SALDO_REAL', valor: 4826.99, id: 1 },
  { usuarioId: 42, mercado: 'US', data: '2026-10-06', tipo: 'SALDO_EM_CONTA', valor: 5016.29, id: 2 },
];
const updatedBrokerBalanceSnapshot = sandbox.snapshotCarteiraPatrimonial([]);
assert.strictEqual(updatedBrokerBalanceSnapshot.patrimonio, 5016.29, 'US wallet should prioritize the latest reported Account Net Liq');
assert.strictEqual(updatedBrokerBalanceSnapshot.patrimonioFonteCorretoraUS, true, 'Updated US broker balance should remain the patrimony source');

sandbox.mercadoAtivo = 'BR';
sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'BR', data: '2026-09-01', tipo: 'APORTE', valor: 1000, id: 1 },
  { usuarioId: 42, mercado: 'BR', data: '2026-09-02', tipo: 'RETIRADA', valor: 200, id: 2 },
  { usuarioId: 42, mercado: 'BR', data: '2026-09-03', tipo: 'AJUSTE', valor: 5000, id: 3 },
  { usuarioId: 42, mercado: 'BR', data: '2026-09-10', tipo: 'SALDO_REAL', valor: 9999, id: 2 },
];
const brWalletSnapshot = sandbox.snapshotCarteiraPatrimonial([]);
assert.strictEqual(brWalletSnapshot.patrimonio, 800, 'BR wallet must derive unreported cash from the account records');
assert.strictEqual(brWalletSnapshot.composicao.caixaInicial, false, 'An unrecognized fixed balance must never be applied to every account');

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'BR', data: '2026-10-03', tipo: 'APORTE', valor: 1000, id: 3 },
];
sandbox.cotacoesCarteira = {
  'BR|PETR4': { ticker: 'PETR4', mercado: 'BR', preco: 100, recebidaEm: Date.now() },
};
const brCashAfterAporteAndCompra = sandbox.snapshotCarteiraPatrimonial([{
  id: 701,
  mercado: 'BR',
  tipoAtivo: 'ACAO',
  ticker: 'PETR4',
  putExercida: 'Carteira',
  acaoCompra: 100,
  acaoTaxaCompra: 1,
  acaoQtd: 10,
  acaoQtdAtual: 10,
  data: '03/10/2026',
}]);
assert.strictEqual(brCashAfterAporteAndCompra.saldoCaixaEstimado, -1, 'BR cash must subtract the actual stock purchase and its fee from deposits');
assert.strictEqual(brCashAfterAporteAndCompra.patrimonio, 999, 'A negative cash estimate must remain part of net patrimony');

sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'BR', data: '2026-09-01', tipo: 'APORTE', valor: 1000, id: 1 },
  { usuarioId: 42, mercado: 'BR', data: '2026-09-02', tipo: 'RETIRADA', valor: 200, id: 2 },
  { usuarioId: 42, mercado: 'BR', data: '2026-09-10', tipo: 'SALDO_EM_CONTA', valor: 1250, id: 3 },
];
const brWalletWithBrokerCash = sandbox.snapshotCarteiraPatrimonial([]);
assert.strictEqual(brWalletWithBrokerCash.aporteLiquido, 800, 'BR broker cash must not change the net contribution');
assert.strictEqual(brWalletWithBrokerCash.saldoCaixaEstimado, 1250, 'BR wallet should use the latest reported broker cash over the fixed cash');
assert.strictEqual(brWalletWithBrokerCash.patrimonio, 1250, 'BR wallet patrimony should include the reported broker cash');
assert.strictEqual(brWalletWithBrokerCash.composicao.caixaInformado, true, 'BR composition should identify reported broker cash');

sandbox.mercadoAtivo = 'US';
sandbox.carteiraMovimentos = [
  { usuarioId: 42, mercado: 'US', data: '2026-08-01', tipo: 'APORTE', valor: 4424.15 },
];
const monthlyUsRows = sandbox.resultadosMensaisCarteiraUS([
  {
    id: 601,
    mercado: 'US',
    ticker: 'AUG',
    putExercida: 'Carteira',
    callExercida: 'Não',
    callPremio: 3.3264,
    callStrike: 10,
    callVencimento: '2026-08-15',
    acaoQtd: 100,
    acaoQtdAtual: 0,
    data: '01/08/2026',
  },
  {
    id: 602,
    mercado: 'US',
    ticker: 'SEP1',
    putExercida: 'Carteira',
    callExercida: 'Não',
    callPremio: 2.0831,
    callStrike: 10,
    callVencimento: '2026-09-05',
    acaoQtd: 100,
    acaoQtdAtual: 0,
    data: '01/09/2026',
  },
  {
    id: 603,
    mercado: 'US',
    ticker: 'SEP2',
    putExercida: 'Carteira',
    callExercida: 'Não',
    callPremio: 0.2861,
    callStrike: 10,
    callVencimento: '2026-09-19',
    acaoQtd: 100,
    acaoQtdAtual: 0,
    data: '02/09/2026',
  },
]);
const septemberUs = monthlyUsRows.find((row) => row.mes === '2026-09');
assert.ok(septemberUs, 'US monthly wallet should include September');
assert.ok(Math.abs(septemberUs.realizado - 236.92) < 0.001, 'September realized result should be 208.31 + 28.61');
assert.ok(Math.abs(septemberUs.resultado - 236.92) < 0.001, 'September wallet row must show the month result, not cumulative profit');
assert.ok(Math.abs(septemberUs.patrimonio - 4993.71) < 0.001, 'September wallet patrimony should remain cumulative while result stays monthly');

sandbox.abrirAjudaRetornoMensal('2026-10', 523.04, 244.28, 278.76, 4428.79, 5440.19);
assert.match(el.innerHTML, /Capital aplicado/, 'Monthly return help should explain the invested-capital base');
assert.match(el.innerHTML, /Potencial de CALL/, 'Monthly return help should disclose unrealized call potential');

const coveredCallClosingMonthly = [{
  id: 604,
  mercado: 'US',
  ticker: 'NU',
  putExercida: 'Carteira',
  callExercida: 'Fechada',
  callPremio: 2.00,
  callTaxas: 0,
  callFechamento: 2.39,
  callTaxasFechamento: 1.23,
  callDataFechamento: '2026-10-05',
  callStrike: 13,
  callVencimento: '2026-10-16',
  acaoCompra: 14.7000308,
  acaoTaxaCompra: 0,
  acaoVenda: 15.28,
  acaoTaxas: 1.45,
  acaoQtd: 1300,
  acaoQtdAtual: 0,
  data: '20/09/2026',
}];
const coveredCallClosingSummary = sandbox.apuracaoMensalUS(coveredCallClosingMonthly)['2026-10'];
assert.ok(Math.abs(coveredCallClosingSummary.premiosOpcoes + 508.23) < 0.01, 'Call buyback cost should remain accounted for');
assert.ok(Math.abs(coveredCallClosingSummary.resultadoAcoes - 752.51) < 0.01, 'Stock sale result should remain accounted for');
assert.ok(Math.abs(coveredCallClosingSummary.resultado - 244.28) < 0.01, 'Covered-call closing should keep its consolidated realized profit');
sandbox.renderMonthlyTableUS(coveredCallClosingMonthly);
assert.match(el.innerHTML, /Resultado realizado/, 'US monthly table should present the covered-call result as one realized result');

const closedCoveredCallWithStaleQuantity = [{
  id: 605,
  mercado: 'US',
  ticker: 'NU',
  putExercida: 'Carteira',
  callExercida: 'Fechada',
  callPremio: 1,
  callFechamento: 0.25,
  callVencimento: '2026-10-16',
  acaoCompra: 14,
  acaoVenda: 15,
  acaoQtd: 400,
  acaoQtdAtual: 400,
  data: '01/10/2026',
}];
assert.strictEqual(
  sandbox.quantidadeAcoesEmCarteira(closedCoveredCallWithStaleQuantity),
  0,
  'Closed covered calls that sold stock must not remain in the dashboard share count'
);

sandbox.usuarioAtual = { id: 42 };
const undoBefore = {
  id: 900,
  usuarioId: 42,
  mercado: 'US',
  ticker: 'INTC',
  fase: 'CALL coberta aberta',
  statusOperacao: 'Em andamento',
  callExercida: 'Pendente',
};
const undoAfter = {
  ...undoBefore,
  fase: 'CALL coberta encerrada',
  statusOperacao: 'Encerrada',
  callExercida: 'Fechada',
  callFechamento: 15.8,
};
sandbox.registrarUndoUltimaAcao({ tipo: 'atualizar', antes: undoBefore, depois: undoAfter });
assert.strictEqual(sandbox.ultimoSalvamento.tipo, 'atualizar', 'Undo should store update actions');
assert.ok(storageData.softdarf_undo_ultima_acao_42, 'Undo should persist per user');
sandbox.ultimoSalvamento = null;
sandbox.carregarUndoUltimaAcao();
assert.strictEqual(sandbox.ultimoSalvamento.antes.id, 900, 'Undo should reload the previous snapshot');

sandbox.historicoCompleto = [{ id: 1 }];
sandbox.historicoOperacoes = [{ id: 2 }];
sandbox.carteiraMovimentos = [{ id: 3, mercado: 'BR', tipo: 'APORTE', valor: 33000 }];
sandbox.cotacoesCarteira = { 'BR|PETR4': { ticker: 'PETR4', mercado: 'BR', preco: 33, recebidaEm: Date.now() } };
sandbox.cotacoesCarteiraTickers = ['PETR4'];
sandbox.limparDadosUsuarioAtual();
assert.strictEqual(sandbox.historicoCompleto.length, 0, 'User data reset should clear full history');
assert.strictEqual(sandbox.historicoOperacoes.length, 0, 'User data reset should clear rendered history');
assert.strictEqual(sandbox.carteiraMovimentos.length, 0, 'User data reset should clear wallet movements');
assert.strictEqual(Object.keys(sandbox.cotacoesCarteira).length, 0, 'User data reset should clear cached wallet quotes');
assert.strictEqual(sandbox.cotacoesCarteiraTickers.length, 0, 'User data reset should stop old wallet quote targets');

sandbox.mercadoAtivo = 'BR';
sandbox.cotacoesCarteira = {};
const missingBrQuoteStatus = sandbox.statusCotacoesCarteira(['PETR4']);
assert.strictEqual(missingBrQuoteStatus.pendente, true, 'BR wallet should treat missing initial quotes as pending');

const apexClosingSample = [
  'Apex Clearing Corporation TASTYWORKS, INC Acct Type B/S Trade Date Settle Date QTY SYM PRICE Principal COMM Tran Fee Fees Number Net Amount',
  '2 B 09/01/26 09/02/26 3 1.8200000 546.00 0.00 0.06 0.30 X9181 546.36 TUA0902 3 1',
  'Desc: CALL NU 09/18/26 13 NU HOLDINGS LTD UNSOLICITED CLOSING CONTRACT',
  'Currency: USD Trailer: UNSOLICITED, CLOSING CONTRACT',
  '2 S 09/01/26 09/02/26 300 NU 14.7400000 4,422.00 0.00 0.16 0.24 C2178 4,421.60 TUA0902 6 1',
  'Desc: ***NU HOLDINGS LTD CLASS A ORDINARY SHARES UNSOLICITED Currency: USD',
].join(' ');

const ops = sandbox.extrairOperacoesNota(apexClosingSample, 'auto');
assert.strictEqual(ops.length, 1, 'Apex closing note should produce one option closing operation');
assert.strictEqual(ops[0].modo, 'fechamentoOpcao');
assert.strictEqual(ops[0].tipo, 'call');
assert.strictEqual(ops[0].ticker, 'NU');
assert.strictEqual(ops[0].qtd, 300);
assert.strictEqual(ops[0].contratos, 3);
assert.strictEqual(ops[0].strike, 13);
assert.strictEqual(ops[0].fechamento, 1.82);
assert.strictEqual(ops[0].vencimento, '2026-09-18');
assert.strictEqual(ops[0].tradeDate, '2026-09-01');
assert.ok(ops[0].vendaAcao, 'Closing note should attach the stock sale');
assert.strictEqual(ops[0].vendaAcao.qtd, 300);
assert.strictEqual(ops[0].vendaAcao.preco, 14.74);
assert.strictEqual(sandbox.modoOperacaoImportada(ops[0]), 'Fechamento posição CALL');
assert.match(sandbox.detalhesOperacaoImportada(ops[0]), /venda ação 300 a 14,74/);

sandbox.historicoCompleto = [{
  id: 77,
  mercado: 'US',
  ticker: 'NU',
  putExercida: 'Carteira',
  callExercida: 'Pendente',
  callPremio: 2.68,
  callTaxas: 0.36,
  callStrike: 13,
  callVencimento: '2026-09-18',
  acaoCompra: 14.7471667,
  acaoQtd: 300,
  acaoQtdAtual: 300,
  acaoTaxaCompra: 0.4,
  data: '01/09/2026',
}];
sandbox.historicoOperacoes = [];

const match = sandbox.encontrarOperacaoAbertaParaFechamento(ops[0]);
assert.ok(match, 'Closing import should match the open NU covered call');
assert.strictEqual(match.id, 77);

const recalculated = sandbox.recalcularOperacaoPersistida({
  ...sandbox.historicoCompleto[0],
  callExercida: 'Fechada',
  callFechamento: ops[0].fechamento,
  callTaxasFechamento: ops[0].taxas,
  callDataFechamento: ops[0].tradeDate,
  acaoVenda: ops[0].vendaAcao.preco,
  acaoTaxas: ops[0].vendaAcao.taxas,
  acaoQtd: ops[0].vendaAcao.qtd,
}, ops[0].tradeDate);

assert.strictEqual(recalculated.statusOperacao, 'Encerrada');
assert.strictEqual(recalculated.acaoQtdAtual, 0);
assert.match(recalculated.fase, /recomprada \+ acoes vendidas/);
assert.ok(Number.isFinite(recalculated.lucro), 'Recalculated profit must be finite');

const apexThirteenContractCloseSample = [
  'Apex Clearing Corporation TASTYWORKS, INC Acct Type B/S Trade Date Settle Date QTY SYM PRICE Principal COMM Tran Fee Fees Number Net Amount',
  '2 B 10/05/26 10/06/26 13 2.3500000 3,055.00 0.00 0.26 1.30 Z5551 3,056.56 TUA1006 5 1',
  'Desc: CALL NU 10/16/26 13 NU HOLDINGS LTD UNSOLICITED CLOSING CONTRACT',
  'Currency: USD Trailer: UNSOLICITED, CLOSING CONTRACT',
  '2 S 10/05/26 10/06/26 1,300 NU 15.2800000 19,864.00 0.00 0.41 1.04 Z5865 19,862.55 TUA1006 6 1',
  'Desc: ***NU HOLDINGS LTD CLASS A ORDINARY SHARES UNSOLICITED Currency: USD',
].join(' ');
const thirteenContractClose = sandbox.extrairOperacoesNota(apexThirteenContractCloseSample, 'auto');
assert.strictEqual(thirteenContractClose.length, 1, 'The supplied 13-contract note should stay as one linked position close');
assert.strictEqual(thirteenContractClose[0].qtd, 1300);
assert.strictEqual(thirteenContractClose[0].fechamento, 2.35);
assert.ok(thirteenContractClose[0].vendaAcao, 'The supplied note should link the 1,300-share sale to the call close');

sandbox.historicoCompleto = [
  { id: 710, mercado: 'US', ticker: 'NU', putExercida: 'Carteira', callExercida: 'Pendente', callPremio: 2.70, callTaxas: 0.20, callStrike: 13, callVencimento: '2026-10-16', acaoCompra: 14.10, acaoQtd: 300, acaoQtdAtual: 300, data: '20/09/2026' },
  { id: 711, mercado: 'US', ticker: 'NU', putExercida: 'Carteira', callExercida: 'Pendente', callPremio: 2.50, callTaxas: 0.30, callStrike: 13, callVencimento: '2026-10-16', acaoCompra: 14.20, acaoQtd: 400, acaoQtdAtual: 400, data: '25/09/2026' },
  { id: 712, mercado: 'US', ticker: 'NU', putExercida: 'Carteira', callExercida: 'Pendente', callPremio: 2.40, callTaxas: 0.40, callStrike: 13, callVencimento: '2026-10-16', acaoCompra: 14.30, acaoQtd: 600, acaoQtdAtual: 600, data: '30/09/2026' },
];
const groupedClosePlan = sandbox.planoFechamentoAgrupado(thirteenContractClose[0]);
assert.ok(groupedClosePlan, 'A close must recognize separate matching lots as one available position');
assert.strictEqual(groupedClosePlan.itens.length, 3, 'The 1,300-share close should consume all three matching NU lots');
assert.strictEqual(groupedClosePlan.itens.reduce((sum, item) => sum + item.quantidade, 0), 1300);

const partialClosePlan = sandbox.planoFechamentoAgrupado({ ...thirteenContractClose[0], qtd: 500, contratos: 5, vendaAcao: null });
assert.ok(partialClosePlan, 'A partial buyback must be recognized across matching lots');
assert.deepStrictEqual(Array.from(partialClosePlan.itens.map((item) => item.quantidade)), [300, 200], 'Partial close should allocate by opening date and preserve the remaining lot');

const novaCallNu = {
  mercado: 'US', moeda: 'USD', ticker: 'NU', data: '04/10/2026', tipoAtivo: 'OPCAO',
  putExercida: 'Carteira', callExercida: 'Pendente', callPremio: 2.20, callTaxas: 0.50,
  callStrike: 13, callVencimento: '2026-10-16', acaoCompra: 14.40, acaoQtd: 200, acaoQtdAtual: 200,
};
const nuAgrupada = sandbox.montarOpcaoAgrupada(sandbox.historicoCompleto, novaCallNu);
assert.strictEqual(nuAgrupada.acaoQtd, 1500, 'Grouping should add the quantities of all compatible calls');
assert.ok(Math.abs(nuAgrupada.callPremio - (3690 / 1500)) < 0.000001, 'Grouping should calculate the weighted average option premium');
assert.ok(Math.abs(nuAgrupada.callTaxas - 1.40) < 0.000001, 'Grouping should preserve the sum of all opening fees');
assert.strictEqual(sandbox.operacoesCompativeisParaAgrupamento({ ...novaCallNu, callStrike: 14 }).length, 0, 'Different strikes must not be grouped');

const apexIntcFullCloseSample = [
  'Apex Clearing Corporation TASTYWORKS, INC Acct Type B/S Trade Date Settle Date QTY SYM PRICE Principal COMM Tran Fee Fees Number Net Amount',
  '2 B 09/09/26 09/10/26 1 15.8000000 1,580.00 0.00 0.02 0.10 L8770 1,580.12 TUA0910 5 1',
  'Desc: CALL INTC 09/18/26 90 INTEL CORP UNSOLICITED CLOSING CONTRACT',
  'Currency: USD Trailer: UNSOLICITED, CLOSING CONTRACT',
  '2 S 09/09/26 09/10/26 100 INTC 105.3200000 10,532.00 0.00 0.24 0.08 L9566 10,531.68 TUA0910 6 1',
  'Desc: INTEL CORP UNSOLICITED Currency: USD',
].join(' ');
const intcFullClose = sandbox.extrairOperacoesNota(apexIntcFullCloseSample, 'auto');
assert.strictEqual(intcFullClose.length, 1, 'Exact INTC call buyback + stock sale should be one full-position close');
assert.strictEqual(intcFullClose[0].modo, 'fechamentoOpcao');
assert.strictEqual(intcFullClose[0].ticker, 'INTC');
assert.strictEqual(intcFullClose[0].qtd, 100);
assert.strictEqual(intcFullClose[0].fechamento, 15.8);
assert.ok(intcFullClose[0].vendaAcao, 'INTC full close should attach the stock sale');
assert.strictEqual(intcFullClose[0].vendaAcao.qtd, 100);
assert.strictEqual(intcFullClose[0].vendaAcao.preco, 105.32);

sandbox.historicoCompleto = [{
  id: 631,
  mercado: 'US',
  ticker: 'INTC',
  putExercida: 'Carteira',
  callExercida: 'Pendente',
  callPremio: 17.3,
  callTaxas: 1.17,
  callStrike: 90,
  callVencimento: '2026-09-18',
  acaoCompra: 104.72,
  acaoQtd: 100,
  acaoQtdAtual: 100,
  acaoTaxaCompra: 0.08,
  data: '17/08/2026',
}];
const intcMatch = sandbox.encontrarOperacaoAbertaParaFechamento(intcFullClose[0]);
assert.ok(intcMatch, 'INTC full-position close should match the open covered call');
assert.ok(sandbox.fechamentoImportadoPodeAplicarDireto(intcFullClose[0], intcMatch, 'CALL'), 'Exact ticker and quantity should apply without another confirmation');

const apexMixedUsSample = [
  apexIntcFullCloseSample,
  '2 B 09/09/26 09/10/26 10 MSFT 500.0000000 5,000.00 0.00 0.01 0.02 L1234 5,000.03 TUA0910 7 1',
  'Desc: MICROSOFT CORP UNSOLICITED Currency: USD',
].join(' ');
const mixedUsOps = sandbox.extrairOperacoesNota(apexMixedUsSample, 'auto');
assert.strictEqual(mixedUsOps.length, 2, 'Mixed US note should list the full close and the extra stock trade');
assert.ok(mixedUsOps.some((op) => op.modo === 'fechamentoOpcao' && op.ticker === 'INTC' && op.vendaAcao), 'Mixed US note should keep the INTC full close');
assert.ok(mixedUsOps.some((op) => op.modo === 'compraAcao' && op.ticker === 'MSFT'), 'Mixed US note should expose the extra stock buy');

const btgPutClosingSample = [
  'NOTA DE CORRETAGEM Nr. nota Folha Data pregao 34184397 1 02/09/2026 BTG Pactual CTVM S.A.',
  'Negocios realizados Q Negociacao C/V Tipo Mercado Prazo Especificacao do titulo Obs. (*) Quantidade Preco / Ajuste Valor Operacao / Ajuste D/C',
  '1-BOVESPA C OPCAO DE VENDA 09/26 B3SAU157 ON 100 0,08 8,00 D',
  'Resumo Financeiro Valor liquido das operacoes 8,00 D Total corretagem / Despesas 4,81 D Liquido para 03/09/2026 12,81 D',
].join(' ');

const brClosings = sandbox.extrairOperacoesNota(btgPutClosingSample, 'auto');
assert.strictEqual(brClosings.length, 1, 'BTG put buyback note should produce one option closing operation');
assert.strictEqual(brClosings[0].modo, 'fechamentoOpcao');
assert.strictEqual(brClosings[0].tipo, 'put');
assert.strictEqual(brClosings[0].ticker, 'B3SA3');
assert.strictEqual(brClosings[0].codigo, 'B3SAU157 ON');
assert.strictEqual(brClosings[0].qtd, 100);
assert.strictEqual(brClosings[0].strike, 15.7);
assert.strictEqual(brClosings[0].fechamento, 0.08);
assert.strictEqual(brClosings[0].debitoRecompra, 8);
assert.strictEqual(brClosings[0].vencimento, '2026-09-30');
assert.strictEqual(brClosings[0].tradeDate, '2026-09-02');

sandbox.historicoCompleto = [{
  id: 88,
  mercado: 'BR',
  ticker: 'B3SA3',
  putExercida: 'Pendente',
  putPremio: 0.2,
  putTaxas: 1.25,
  putStrike: 15.7,
  putQtd: 100,
  putVencimento: '2026-09-18',
  data: '01/09/2026',
}];

const brMatch = sandbox.encontrarOperacaoAbertaParaFechamento(brClosings[0]);
assert.ok(brMatch, 'Imported BTG put closing should match the open B3SA3 put');
assert.strictEqual(brMatch.id, 88);

sandbox.fechamentoContext = { id: 88, tipo: 'PUT', op: sandbox.historicoCompleto[0] };
const brModalCompatibility = sandbox.compatibilidadeFechamentoSelecionado(brClosings[0]);
assert.ok(brModalCompatibility.ok, 'Selected close modal should accept the matching BTG put closing');

const mixedBrNote = [
  'NOTA DE CORRETAGEM Nr. nota Folha Data pregao 34184398 1 02/09/2026',
  '1-BOVESPA C OPCAO DE VENDA 09/26 B3SAU157 ON 100 0,08 8,00 D',
  '1-BOVESPA V OPCAO DE COMPRA 10/26 PETRJ320 PN 100 0,20 20,00 C',
].join(' ');
const mixedOps = sandbox.extrairOperacoesNota(mixedBrNote, 'auto');
assert.ok(mixedOps.some((op) => op.modo === 'fechamentoOpcao' && op.tipo === 'put'), 'Mixed note should still expose the put closing');
assert.ok(mixedOps.some((op) => op.modo === 'vendaOpcao' && op.tipo === 'call'), 'Mixed note should keep the new call sale separate');

const btgTwoEtfsSample = [
  'NOTA DE CORRETAGEM Nr. nota Folha Data pregao 33961229 1 25/08/2026',
  'Negocios realizados Q Negociacao C/V Tipo Mercado Prazo Especificacao do titulo Obs. (*) Quantidade Preco / Ajuste Valor Operacao / Ajuste D/C',
  '1-BOVESPA C VISTA LFTB11 32 125,65 4.020,80 D',
  '1-BOVESPA C VISTA LFTS11 25 158,68 3.967,00 D',
  'Resumo Financeiro Valor liquido das operacoes 7.987,80 D Taxa de liquidacao/CCP 1,78 D Emolumentos 0,39 D Taxa de Transferencia de Ativos 0,20 D Total corretagem / Despesas 9,63 D Liquido para 26/08/2026 7.999,80 D',
].join(' ');

const vistaOps = sandbox.extrairOperacoesNota(btgTwoEtfsSample, 'auto');
assert.strictEqual(vistaOps.length, 2, 'BTG cash note should expose both asset purchases');
assert.deepStrictEqual(Array.from(vistaOps.map((op) => op.ticker).sort()), ['LFTB11', 'LFTS11']);
assert.ok(vistaOps.every((op) => op.tipoAtivo === 'ETF_RF' && op.modo === 'compraEtf'), 'LFTB11/LFTS11 should remain ETF_RF purchases');
const lftb = vistaOps.find((op) => op.ticker === 'LFTB11');
const lfts = vistaOps.find((op) => op.ticker === 'LFTS11');
assert.strictEqual(lftb.tradeDate, '2026-08-25');
assert.strictEqual(lftb.qtd, 32);
assert.strictEqual(lftb.preco, 125.65);
assert.ok(Math.abs(lftb.taxas - 6.04) < 0.02, 'LFTB11 fees should be prorated by trade value');
assert.ok(Math.abs(lfts.taxas - 5.96) < 0.02, 'LFTS11 fees should be prorated by trade value');
const lftbPayload = sandbox.payloadOperacaoSimplesImportada(lftb);
assert.strictEqual(lftbPayload.tipoAtivo, 'ETF_RF');
assert.strictEqual(lftbPayload.etfQuantidade, 32);
assert.strictEqual(lftbPayload.etfPrecoCompra, 125.65);
assert.ok(Math.abs(lftbPayload.etfTaxasCompra - lftb.taxas) < 0.001);

const mixedCashAndOption = [
  'NOTA DE CORRETAGEM Nr. nota Folha Data pregao 33961230 1 25/08/2026',
  '1-BOVESPA C VISTA PETR4 PN 100 32,10 3.210,00 D',
  '1-BOVESPA V OPCAO DE VENDA 09/26 B3SAU157 ON 100 0,20 20,00 C',
  'Resumo Financeiro Valor liquido das operacoes 3.190,00 D Taxa de liquidacao/CCP 1,00 D Total corretagem / Despesas 4,00 D Liquido para 26/08/2026 3.195,00 D',
].join(' ');
const mixedCashOps = sandbox.extrairOperacoesNota(mixedCashAndOption, 'auto');
assert.ok(mixedCashOps.some((op) => op.modo === 'compraAcao' && op.ticker === 'PETR4'), 'Mixed note should expose the stock purchase');
assert.ok(mixedCashOps.some((op) => op.modo === 'vendaOpcao' && op.tipo === 'put'), 'Mixed note should keep the put sale separate');
const petr = mixedCashOps.find((op) => op.modo === 'compraAcao');
const petrPayload = sandbox.payloadOperacaoSimplesImportada(petr);
assert.strictEqual(petrPayload.tipoAtivo, 'ACAO');
assert.strictEqual(petrPayload.acaoQtdAtual, 100);
assert.strictEqual(petrPayload.acaoCompra, 32.10);

sandbox.historicoCompleto = [
  Object.assign({ id: 201 }, sandbox.montarOperacaoAcaoCarteira({
    modo: 'compra',
    ticker: 'PETR4',
    data: '2026-08-01',
    quantidade: 100,
    preco: 30,
    taxas: 10,
  })),
];
const vendaParcial = {
  mercado: 'BR',
  moeda: 'BRL',
  tipoAtivo: 'ACAO',
  modo: 'vendaAcao',
  ticker: 'PETR4',
  qtd: 40,
  preco: 35,
  valorOperacao: 1400,
  taxas: 2,
  tradeDate: '2026-08-25',
};
const vendaPayload = sandbox.payloadOperacaoSimplesImportada(vendaParcial);
assert.strictEqual(vendaPayload.tipoAtivo, 'ACAO');
assert.strictEqual(vendaPayload.acaoQtd, 40);
assert.ok(Math.abs(vendaPayload.acaoCompra - 30.10) < 0.001, 'Stock sale should use current average cost including buy fees');
assert.ok(Math.abs(vendaPayload.ir - 29.10) < 0.001, 'Stock sale should estimate 15% IR over positive realized result');
assert.ok(Math.abs(vendaPayload.lucro - 164.90) < 0.001, 'Stock sale should store net realized result');
const vendaDetalhes = sandbox.detalhesFiscaisOperacao(vendaPayload);
assert.strictEqual(vendaDetalhes.length, 1);
assert.strictEqual(vendaDetalhes[0].tipo, 'ACAO');
assert.ok(Math.abs(vendaDetalhes[0].resultadoTributavel - 194) < 0.001);

// A covered call that expires and a later assigned call must preserve the
// original stock basis. This guards the manual reconciliation path used for
// broker statements where the stock lot and the later call are separate rows.
const reconciledCoveredCalls = [
  {
    id: 376,
    mercado: 'US',
    ticker: 'NU',
    putExercida: 'Carteira',
    callExercida: 'nao',
    callPremio: 2.19,
    callTaxas: 4.54,
    callVencimento: '2026-05-22',
    acaoCompra: 15.37,
    acaoTaxaCompra: 0.32,
    acaoQtd: 400,
    acaoQtdAtual: 400,
    data: '16/04/2026',
  },
  {
    id: 408,
    mercado: 'US',
    ticker: 'NU',
    putExercida: 'Carteira',
    callExercida: 'Sim',
    callPremio: 0.46,
    callTaxas: 4.51,
    callStrike: 13,
    callVencimento: '2026-07-17',
    acaoCompra: 15.37,
    acaoTaxaCompra: 0.32,
    acaoVenda: 13,
    acaoTaxas: 5.19,
    acaoQtd: 400,
    acaoQtdAtual: 0,
    data: '16/06/2026',
  },
];
const reconciledMonthly = sandbox.apuracaoMensalUS(reconciledCoveredCalls);
assert.ok(Math.abs(reconciledMonthly['2026-05'].resultado - 871.46) < 0.001, 'Expired call should realize its net premium');
assert.ok(Math.abs(reconciledMonthly['2026-07'].resultado + 774.02) < 0.001, 'Assigned call should retain the original stock cost and sale fee');
assert.ok(Math.abs(sandbox.resultadoRealizadoTotalUS(reconciledCoveredCalls) - 97.44) < 0.001, 'The two linked covered-call cycles should reconcile to their combined realized result');

// Expiry must never resolve an option without an explicit outcome, even months later.
const originalToday = sandbox.hojeISO;
sandbox.hojeISO = () => '2027-02-10';
for (const tipo of ['PUT', 'CALL']) {
  const prefix = tipo.toLowerCase();
  const pending = {
    mercado: 'BR', ticker: 'B3SA3', data: '01/10/2026',
    putExercida: tipo === 'CALL' ? 'Carteira' : 'Pendente',
    callExercida: 'Pendente', acaoQtd: 600, acaoQtdAtual: 600, acaoCompra: 16,
    [prefix + 'Premio']: 1, [prefix + 'Qtd']: 600,
    [prefix + 'Strike']: 17.57, [prefix + 'Vencimento']: '2026-10-16',
    fase: tipo + ' vencida sem exercicio', statusOperacao: 'Encerrada',
  };
  assert.strictEqual(sandbox.opcaoAguardandoConfirmacao('Pendente', '2026-10-16'), true);
  assert.strictEqual(sandbox.opcaoAbertaComVencimento('Pendente', '2026-10-16'), true);
  assert.strictEqual(sandbox.opcaoExpiradaSemExercicio('Pendente', '2026-10-16'), false);
  assert.strictEqual(sandbox.statusOperacao(pending), 'Vencida — aguardando confirmação');
  assert.strictEqual(sandbox.darfStatus(pending), 'Aguardando apuração');
  assert.strictEqual(sandbox.resultadoOpcaoFechadaMensal(pending, tipo).valor, 0);
  assert.match(sandbox.vencOpcaoBadge(pending, tipo), /Aguardando nota/);
  const normalized = sandbox.normalizarResultadosHistorico([pending])[0];
  assert.strictEqual(normalized.statusOperacao, 'Vencida — aguardando confirmação');
  assert.match(normalized.fase, /Aguardando nota/);
  assert.ok(sandbox.pontuarOperacaoAbertaParaExercicio(normalized, {
    mercado: 'BR', ticker: 'B3SA3', qtd: 600, precoExercicio: 17.57,
  }, tipo, false) >= 0, 'Overdue option must still accept an exercise note');
  const confirmed = { ...pending, [prefix + 'Exercida']: 'nao' };
  assert.strictEqual(sandbox.operacaoAguardandoApuracao(confirmed), false);
  assert.strictEqual(sandbox.statusOperacao(confirmed), 'Encerrada');
  assert.strictEqual(sandbox.resultadoOpcaoFechadaMensal(confirmed, tipo).valor, 600);
}
sandbox.hojeISO = originalToday;

const exerciseNote = sandbox.extrairOperacoesNota([
  'NOTA DE CORRETAGEM 16/10/2026',
  '1-BOVESPA V EXERC OPC COMPRA B3SAJ177E 600 17,57 10.542,00 C',
  '1-BOVESPA V EXERC OPC COMPRA BBASJ215E 1200 21,29 25.548,00 C',
].join(' '), 'auto');
assert.strictEqual(exerciseNote.length, 2);
assert.ok(exerciseNote.every(op => op.exercicioVenda), 'Both attached note trades must be call exercises');

const settlementToday = sandbox.hojeISO;
sandbox.mercadoAtivo = 'BR';
sandbox.carteiraMovimentos = [
  { mercado: 'BR', tipo: 'APORTE', valor: 33893.22, data: '2026-01-01' },
  { mercado: 'BR', tipo: 'SALDO_EM_CONTA', valor: 2.44, data: '2026-10-17' },
];
const settledExercises = [
  { ticker:'B3SA3', acaoQtd:600, acaoVenda:17.57, acaoTaxas:59.61823773898586 },
  { ticker:'BBAS3', acaoQtd:1200, acaoVenda:21.29, acaoTaxas:144.48176226101413 },
].map((op, i) => ({ ...op, id:9000+i, mercado:'BR', tipoAtivo:'OPCAO',
  data:'25/08/2026', putExercida:'Carteira', callExercida:'Sim',
  callPremio:0.42, acaoCompra:17, acaoQtdAtual:0,
  acaoDataVenda:'2026-10-16', acaoDataLiquidacao:'2026-10-20' }));
sandbox.hojeISO = () => '2026-10-17';
let settlementSnap = sandbox.snapshotCarteiraPatrimonial(settledExercises);
assert.ok(Math.abs(settlementSnap.aReceber - 35885.90) < 0.00001);
assert.ok(Math.abs(settlementSnap.composicao.caixa - 35888.34) < 0.00001, 'Account balance includes pending settlement');
assert.strictEqual(settlementSnap.saldoCaixaDisponivel, 2.44);
assert.ok(Math.abs(settlementSnap.disponibilidadeRecebimentos['2026-10-20'] - 35885.90) < 0.00001);
assert.ok(Math.abs(settlementSnap.patrimonio - 35888.34) < 0.00001);
assert.strictEqual(settlementSnap.aporteLiquido, 33893.22);
const settlementGetElement = sandbox.document.getElementById;
const settlementLegend = {innerHTML:''};
sandbox.document.getElementById = id => id === 'wallet-pie' ? {style:{}} : settlementLegend;
sandbox.renderPizzaCarteiraPatrimonial(settlementSnap);
assert.match(settlementLegend.innerHTML, /Saldo em conta/);
assert.match(settlementLegend.innerHTML, /35\.888,34/);
assert.match(settlementLegend.innerHTML, /Inclui.*35\.885,90.*disponíveis em 20\/10\/2026/);
assert.ok(!settlementLegend.innerHTML.includes('>A receber<'), 'Settlement must not be a separate slice');
sandbox.document.getElementById = settlementGetElement;
sandbox.hojeISO = () => '2026-10-20';
settlementSnap = sandbox.snapshotCarteiraPatrimonial(settledExercises);
assert.strictEqual(settlementSnap.aReceber, 0);
assert.strictEqual(Object.keys(settlementSnap.disponibilidadeRecebimentos).length, 0, 'Availability warning disappears upon settlement');
assert.ok(Math.abs(settlementSnap.composicao.caixa - 35888.34) < 0.00001);
assert.ok(Math.abs(settlementSnap.patrimonio - 35888.34) < 0.00001);
sandbox.carteiraMovimentos.push({mercado:'BR', tipo:'SALDO_EM_CONTA', valor:35888.34, data:'2026-10-20', id:99});
settlementSnap = sandbox.snapshotCarteiraPatrimonial(settledExercises);
assert.ok(Math.abs(settlementSnap.composicao.caixa - 35888.34) < 0.00001, 'Reported settled balance must not duplicate proceeds');
sandbox.hojeISO = () => '2026-10-17';
sandbox.carteiraMovimentos[1].data = '2026-10-15';
sandbox.carteiraMovimentos.pop();
const sameDayOpening = settledExercises.map(op => ({...op, data:'16/10/2026', acaoCompra:0}));
assert.ok(Math.abs(sandbox.snapshotCarteiraPatrimonial(sameDayOpening).composicao.caixa - (758.44 + 35885.90)) < 0.00001, 'Pending proceeds enter the account balance exactly once');
sandbox.hojeISO = settlementToday;
const noteWithSettlement = sandbox.extrairOperacoesNota('NOTA DE CORRETAGEM 16/10/2026 1-BOVESPA V EXERC OPC COMPRA BBASJ215E 1200 21,29 25.548,00 C Líquido para 20/10/2026 C35.885,90', 'auto');
assert.strictEqual(noteWithSettlement[0].settleDate, '2026-10-20');

// A call sale in August exercises in October, after another stock purchase.
const assignedCallTimeline = [
  {id:9100, mercado:'BR', ticker:'B3AS3', data:'07/05/2026', putExercida:'Sim', putQtd:500, acaoCompra:17.44},
  {id:9101, mercado:'BR', ticker:'B3SA3', data:'24/08/2026', putExercida:'Carteira', callExercida:'Sim',
    callPremio:0.36, acaoQtd:600, acaoQtdAtual:0, acaoVenda:17.57, callVencimento:'2026-10-16', acaoDataVenda:'2026-10-16'},
  {id:9102, mercado:'BR', ticker:'B3SA3', data:'13/10/2026', tipoAtivo:'ACAO',
    putExercida:'Carteira', acaoQtd:100, acaoQtdAtual:100, acaoCompra:22.83},
];
const fullySoldTimeline = sandbox.calcularCarteiraEstados(assignedCallTimeline);
assert.strictEqual(fullySoldTimeline.itens.length, 0, 'Exercise must sell all 600 shares, including 100 bought after call opening');
assert.strictEqual(fullySoldTimeline.divergencias.length, 0);
assert.strictEqual(fullySoldTimeline.estados[9101].qtd, 0);
assert.strictEqual(sandbox.calcularCarteiraEstados(assignedCallTimeline.slice().reverse()).itens.length, 0, 'API row order must not change holdings');
const stockBoughtAfterExercise = {...assignedCallTimeline[2], id:9103, data:'19/10/2026', acaoQtd:50, acaoQtdAtual:50};
assert.strictEqual(sandbox.calcularCarteiraEstados(assignedCallTimeline.concat(stockBoughtAfterExercise)).itens[0].qtd, 50, 'A later purchase must remain in the portfolio');
const missingPurchaseTimeline = assignedCallTimeline.slice(0, 2);
assert.strictEqual(sandbox.calcularCarteiraEstados(missingPurchaseTimeline).divergencias[0].quantidadeDisponivel, 500, 'Missing shares must be reported instead of silently truncating exercise');
const sameDatePurchaseTimeline = assignedCallTimeline.map(op => op.id === 9102 ? {...op, data:'16/10/2026'} : op);
assert.strictEqual(sandbox.calcularCarteiraEstados(sameDatePurchaseTimeline).divergencias.length, 0, 'Same-day purchases must be considered before exercise settlement');
const partiallySoldTimeline = assignedCallTimeline.map(op => op.id === 9101 ? {...op, acaoQtd:400} : op);
assert.strictEqual(sandbox.calcularCarteiraEstados(partiallySoldTimeline).itens[0].qtd, 200, 'Partial exercise must preserve the remaining shares');

const originalPurchaseSnapshot = JSON.stringify(assignedCallTimeline[2]);
const soldPurchaseHistory = sandbox.dadosCompraAcaoHistorico(assignedCallTimeline[2], fullySoldTimeline);
assert.strictEqual(soldPurchaseHistory.status, 'Vendida');
assert.strictEqual(soldPurchaseHistory.quantidade, 100);
assert.strictEqual(soldPurchaseHistory.saldo, 0);
const reacquiredTimeline = sandbox.calcularCarteiraEstados(assignedCallTimeline.concat(stockBoughtAfterExercise));
assert.strictEqual(sandbox.dadosCompraAcaoHistorico(assignedCallTimeline[2], reacquiredTimeline).status, 'Vendida', 'Later repurchase must not revive a sold purchase card');
assert.strictEqual(sandbox.dadosCompraAcaoHistorico(stockBoughtAfterExercise, reacquiredTimeline).status, 'Em carteira');
const partialPurchaseTimeline = assignedCallTimeline.map(op => op.id === 9101 ? {...op, acaoQtd:550} : op);
const partialPurchaseHistory = sandbox.dadosCompraAcaoHistorico(assignedCallTimeline[2], sandbox.calcularCarteiraEstados(partialPurchaseTimeline));
assert.strictEqual(partialPurchaseHistory.status, 'Parcialmente vendida');
assert.strictEqual(partialPurchaseHistory.saldo, 50);
assert.strictEqual(JSON.stringify(assignedCallTimeline[2]), originalPurchaseSnapshot, 'History status must not rewrite the original stock purchase');
const historyOriginalGetElement = sandbox.document.getElementById;
const historyOutput = {innerHTML:''};
sandbox.document.getElementById = id => id === 'hist-list' ? historyOutput : historyOriginalGetElement(id);
sandbox.historicoExpandido = false;
sandbox.renderHistoryOrganizado(assignedCallTimeline);
const purchaseCard = historyOutput.innerHTML.split('<div class="hist-item">')[1].split('<div class="hist-item">')[0];
assert.match(purchaseCard, /Vendida/);
assert.match(purchaseCard, /Compra: 100 a&ccedil;&otilde;es/);
assert.match(purchaseCard, /22,83/);
assert.match(purchaseCard, /Saldo atual: 0 a&ccedil;&otilde;es/);
assert.ok(!purchaseCard.includes('Em carteira'), 'Sold purchase card must not claim current ownership');
assert.ok(!purchaseCard.includes('Carteira: 600'), 'Historical aggregate balance must not be presented as current shares');
sandbox.document.getElementById = historyOriginalGetElement;

// Fiscal regression: acquisitions must be replayed at their actual dates,
// independently of the portfolio average adjusted by previous call premiums.
const fiscalAccount = 901;
const fiscalOps = [
  {id:20001,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'23/04/2026',putExercida:'Sim',putQtd:500,putPremio:.27,putTaxas:4.98,putVencimento:'2026-05-15',acaoCompra:17.72,acaoTaxaCompra:50.10,acaoQtd:500},
  {id:20002,usuarioId:fiscalAccount,mercado:'BR',ticker:'BBAS3',data:'15/04/2026',putExercida:'Sim',putQtd:600,putPremio:.31,putTaxas:5.04,putVencimento:'2026-05-15',acaoCompra:23,acaoTaxaCompra:78.03,acaoQtd:600},
  {id:20003,usuarioId:fiscalAccount,mercado:'BR',ticker:'BBAS3',data:'24/07/2026',putExercida:'Sim',putQtd:600,putPremio:.22,putTaxas:4.98,putVencimento:'2026-08-21',acaoCompra:19.4,acaoTaxaCompra:65.81,acaoQtd:600},
  {id:20004,usuarioId:fiscalAccount,mercado:'BR',tipoAtivo:'ACAO',ticker:'B3SA3',data:'13/10/2026',putExercida:'Carteira',acaoQtd:100,acaoQtdAtual:0,acaoCompra:22.83,acaoTaxaCompra:5.48},
  {id:20005,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'24/08/2026',putExercida:'Carteira',callExercida:'Sim',callPremio:.36,callTaxas:5.08,callStrike:17.76,callVencimento:'2026-10-16',acaoDataVenda:'2026-10-16',acaoCompra:17.32,acaoVenda:17.57,acaoTaxas:59.62,acaoQtd:600,acaoQtdAtual:0},
  {id:20006,usuarioId:fiscalAccount,mercado:'BR',ticker:'BBAS3',data:'25/08/2026',putExercida:'Carteira',callExercida:'Sim',callPremio:.42,callTaxas:5.47,callVencimento:'2026-10-16',acaoDataVenda:'2026-10-16',acaoCompra:20.98,acaoVenda:21.29,acaoTaxas:144.48,acaoQtd:1200,acaoQtdAtual:0},
  {id:20007,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'01/09/2026',putExercida:'Fechada',putQtd:100,putPremio:.21,putTaxas:4.82,putFechamento:.08,putTaxasFechamento:4.81,putDataFechamento:'2026-09-02',putVencimento:'2026-09-18'},
  {id:20008,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'03/09/2026',putExercida:'Não',putQtd:100,putPremio:.19,putTaxas:4.82,putVencimento:'2026-09-18'},
  {id:20009,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'21/09/2026',putExercida:'Não',putQtd:100,putPremio:.21,putTaxas:4.82,putVencimento:'2026-09-25'},
  {id:20010,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'07/10/2026',putExercida:'Não',putQtd:100,putPremio:.31,putTaxas:4.84,putVencimento:'2026-10-09'},
];
const fiscalSnapshot = JSON.stringify(fiscalOps);
const fiscalApuracao = sandbox.apurarDARFMensal(fiscalOps, []);
assert.strictEqual(fiscalApuracao.custosFiscais[20005].custo,11068.56);
assert.strictEqual(fiscalApuracao.custosFiscais[20006].custo,25275.86);
assert.strictEqual(fiscalApuracao.resultadosPorOperacao[20005],-375.26);
assert.strictEqual(fiscalApuracao.resultadosPorOperacao[20006],626.19);
const fiscalSeptember = fiscalApuracao.linhas.find(l => l.mes === '2026-09');
const fiscalOctober = fiscalApuracao.linhas.find(l => l.mes === '2026-10');
assert.strictEqual(fiscalSeptember.resultado,33.73);
assert.strictEqual(fiscalSeptember.imposto,5.05,'Monthly tax must match the truncation in the official IRPF program');
assert.strictEqual(fiscalOctober.resultado,277.09);
assert.strictEqual(fiscalOctober.imposto,41.56);
assert.strictEqual(fiscalOctober.saldoAnterior,5.05);
assert.strictEqual(fiscalOctober.darfAPagar,46.61);
assert.strictEqual(fiscalOctober.vencimento,'2026-11-30');
assert.strictEqual(fiscalApuracao.totalPendente,46.61);
assert.strictEqual(sandbox.apurarDARFMensal(fiscalOps.slice().reverse(), []).totalPendente,46.61);
assert.strictEqual(JSON.stringify(fiscalOps),fiscalSnapshot,'Fiscal calculation must not rewrite purchases or payment flags');
const previousCall = {id:20011,usuarioId:fiscalAccount,mercado:'BR',ticker:'B3SA3',data:'20/05/2026',putExercida:'Carteira',acaoQtd:500,acaoCompra:17.72,callExercida:'Não',callPremio:.50,callVencimento:'2026-06-19'};
assert.strictEqual(sandbox.apurarDARFMensal(fiscalOps.concat(previousCall), []).custosFiscais[20005].custo,11068.56,'Earlier call premiums must not reduce fiscal stock cost again');
const missingFiscalBuy = fiscalOps.filter(op => op.id !== 20004);
assert.strictEqual(sandbox.apurarDARFMensal(missingFiscalBuy, []).linhas.find(l => l.mes === '2026-10').status,'Conferir custo');
const fiscalRendered = JSON.parse(JSON.stringify(fiscalOps));
sandbox.pagamentosDarf = [];
sandbox.aplicarApuracaoMensalAoHistorico(fiscalRendered);
assert.strictEqual(fiscalRendered.find(op => op.id === 20005).ir,0);
assert.strictEqual(fiscalRendered.find(op => op.id === 20005).lucro,-375.26);
assert.strictEqual(sandbox.centavosFiscais(fiscalRendered.reduce((total,op) => total + op.ir,0)),4661,'Per-operation allocation must reconcile with monthly tax');

const fiscalOption = (id,month,profit,extra={}) => ({id,usuarioId:901,mercado:'BR',tipoAtivo:'OPCAO',ticker:'TEST3',data:month+'-01',putExercida:profit < 0 ? 'Fechada' : 'Não',putQtd:100,putPremio:profit < 0 ? 1 : profit/100,putFechamento:profit < 0 ? 1-profit/100 : 0,putDataFechamento:month+'-15',putVencimento:month+'-15',...extra});
const fiscalSale = (id,salePrice,cost,extra={}) => ({id,usuarioId:901,mercado:'BR',tipoAtivo:'ACAO',ticker:'TEST3',data:'2026-01-15',putExercida:'Carteira',acaoQtd:100,acaoVenda:salePrice,acaoCompra:cost,...extra});
let fiscalCase = sandbox.apurarDARFMensal([fiscalSale(21001,11,10)],[]);
assert.strictEqual(fiscalCase.linhas[0].resultadoIsento,100);
assert.strictEqual(fiscalCase.linhas[0].imposto,0,'Eligible cash stock gains below the monthly sales limit are exempt');
assert.strictEqual(sandbox.apurarDARFMensal([fiscalSale(21001,200,199)],[]).linhas[0].imposto,0,'Exactly R$20,000 in cash stock sales remains eligible');
assert.strictEqual(sandbox.apurarDARFMensal([fiscalSale(21001,200.01,199)],[]).linhas[0].imposto,15.15,'Above the limit the entire net gain is taxable');
assert.strictEqual(sandbox.apurarDARFMensal([fiscalSale(21001,11,10,{dayTrade:true})],[]).linhas[0].imposto,20,'Day trades cannot use the cash-stock exemption');
const earlyFiscalExercise = {...fiscalOps[4],id:21002,data:'2026-08-01',ticker:'TEST3',acaoQtd:100,acaoCompra:10,acaoVenda:11,acaoTaxas:0,callPremio:1,callTaxas:0,acaoDataVenda:'2026-09-15'};
assert.strictEqual(sandbox.apurarDARFMensal([earlyFiscalExercise],[]).linhas[0].mes,'2026-09','Actual exercise date must override expiry month');
assert.strictEqual(sandbox.apurarDARFMensal([earlyFiscalExercise],[]).linhas[0].imposto,30,'Exercise gains do not receive the cash-stock exemption');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22001,'2026-01',-100,{irrf:2}),fiscalOption(22002,'2026-02',200)],[]);
assert.strictEqual(fiscalCase.linhas[1].prejuizoAnterior,100);
assert.strictEqual(fiscalCase.linhas[1].irrfAnterior,2);
assert.strictEqual(fiscalCase.linhas[1].darfAPagar,13,'Unused IRRF and losses must carry independently');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22003,'2026-01',100,{irrf:2,acaoQtd:100,callPremio:1,callExercida:'Não',callVencimento:'2026-02-15'})],[]);
assert.strictEqual(fiscalCase.linhas.reduce((sum,l) => sum+l.irrf,0),2,'A single retained amount cannot be deducted again for the other option leg');
assert.strictEqual(fiscalCase.linhas[1].impostoAposIrrf,15);
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22004,'2026-01',40),fiscalOption(22005,'2026-01',30,{dayTrade:true})],[]);
assert.strictEqual(fiscalCase.totalPendente,12,'The R$10 minimum applies to the monthly DARF, after separate common/day-trade calculations');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22006,'2026-01',100),fiscalOption(22007,'2026-01',-100,{usuarioId:902})],[]);
assert.strictEqual(fiscalCase.totalPendente,15,'Losses from a different CPF must never reduce tax');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22008,'2026-01',100,{irrf:10,dayTrade:true}),fiscalOption(22009,'2026-02',100)],[]);
assert.strictEqual(fiscalCase.linhas[1].impostoAposIrrf,15,'Day-trade IRRF cannot reduce common-operation tax');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(22010,'2026-12',0,{irrf:10}),fiscalOption(22011,'2027-01',100)],[]);
assert.strictEqual(fiscalCase.linhas[1].irrfAnterior,0,'Unused IRRF from a previous tax year must not automatically cross into the next year');
const fiscalPayment = {id:1,usuarioId:901,competencia:'2026-01',valor:10,dataPagamento:'2026-02-10',referencia:'pago'};
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(23001,'2026-01',200)],[fiscalPayment]);
assert.strictEqual(fiscalCase.totalPendente,20);
assert.strictEqual(fiscalCase.linhas[0].status,'Parcial');
assert.strictEqual(fiscalCase.linhas[0].valorPago,10);
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(23001,'2026-01',200),fiscalOption(23002,'2026-01',100)],[fiscalPayment]);
assert.strictEqual(fiscalCase.totalPendente,35,'A new taxable operation must expose the difference after an earlier payment');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(23001,'2026-01',200)],[{...fiscalPayment,valor:30}]);
assert.strictEqual(fiscalCase.totalPendente,0);
assert.strictEqual(fiscalCase.linhas[0].status,'Pago');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(23001,'2026-01',200,{darfStatus:'Pago'}),fiscalOption(23002,'2026-01',100)],[]);
assert.strictEqual(fiscalCase.linhas[0].status,'Conferir pagamento','A legacy paid flag without an amount must not silently mark new results as paid');
fiscalCase = sandbox.apurarDARFMensal([fiscalOption(23001,'2026-01',100),fiscalOption(23002,'2026-02',100)],[fiscalPayment]);
assert.strictEqual(fiscalCase.linhas[0].darfMenor10,5);
assert.strictEqual(fiscalCase.linhas[1].darfAPagar,20,'A remaining difference below the minimum must carry after a partial payment');
assert.strictEqual(sandbox.apurarDARFMensal([{...fiscalOps[4],mercado:'US'}],[]).linhas.length,0,'US options stay outside the monthly Brazilian DARF');
const unknownExpiredOutcome = {...earlyFiscalExercise,callExercida:'Pendente',acaoVenda:0,callVencimento:'2026-01-16'};
fiscalCase = sandbox.apurarDARFMensal([unknownExpiredOutcome],[{...fiscalPayment,valor:30}]);
assert.strictEqual(fiscalCase.linhas[0].status,'Aguardando apuração','An attached payment cannot silently resolve a pending exercise outcome');
assert.strictEqual(fiscalCase.linhas[0].creditoPagamento,0,'An unresolved month cannot be labelled an overpayment');
assert.strictEqual(fiscalCase.totalPendente,0,'Unresolved outcomes must not produce a purported final tax bill');

// Expired options must remain unresolved without consuming coverage or looking active.
const reviewToday=sandbox.hojeISO;
sandbox.hojeISO=()=> '2026-10-17';
sandbox.usuarioAtual={id:901};
sandbox.mercadoAtivo='BR';
sandbox.pagamentosDarf=[];
const reviewShares={id:30000,usuarioId:901,mercado:'BR',tipoAtivo:'ACAO',ticker:'TEST3',data:'2026-01-01',putExercida:'Carteira',acaoCompra:10,acaoQtd:100,acaoQtdAtual:100};
const reviewExpired={id:30001,usuarioId:901,mercado:'BR',ticker:'TEST3',data:'2026-02-01',putExercida:'Carteira',acaoQtd:100,acaoCompra:10,callPremio:.5,callExercida:'Pendente',callVencimento:'2026-06-19'};
const reviewFuture={...reviewExpired,id:30002,data:'2026-09-01',callVencimento:'2026-11-20'};
let reviewOptions=sandbox.opcoesAbertasHistorico([reviewShares,reviewExpired,reviewFuture]);
assert.strictEqual(reviewOptions.find(o=>o.id===30001).aguardandoConfirmacao,true);
assert.strictEqual(reviewOptions.find(o=>o.id===30001).qtdCoberta,undefined);
assert.strictEqual(reviewOptions.find(o=>o.id===30002).qtdCoberta,100,'An unresolved old call must not consume coverage shown for the current call');
assert.strictEqual(sandbox.calcularCarteiraEstados([reviewShares,reviewExpired,reviewFuture]).itens[0].callsAbertas,1);
const reviewGetElement=sandbox.document.getElementById;
const reviewElements={};
sandbox.document.getElementById=id=>reviewElements[id] || (reviewElements[id]={innerHTML:'',textContent:'',style:{}});
sandbox.renderOpenOptions([reviewShares,reviewExpired]);
assert.match(reviewElements['open-options-list'].innerHTML,/Vencida — aguardando confirmação/);
assert.ok(!reviewElements['open-options-list'].innerHTML.includes('CALL aberta'));
assert.ok(!reviewElements['open-options-list'].innerHTML.includes('descobertas'));
sandbox.renderOpenOptions([reviewShares,{...reviewExpired,callExercida:'Não'}]);
assert.ok(!reviewElements['open-options-list'].innerHTML.includes('TEST3'),'An explicitly closed option must stay out of both active and pending lists');
sandbox.document.getElementById=reviewGetElement;

const reviewMixedNote='NOTA DE CORRETAGEM 16/10/2026\n1-BOVESPA V EXERC OPC COMPRA B3SAJ177E 200 17,57 3.514,00 C\n1-BOVESPA V EXERC OPC COMPRA BBASJ215E 600 21,29 12.774,00 C\n1-BOVESPA C EXERC OPC VENDA VALEV754E 200 73,64 14.728,00 D\nVendas à vista 16.288,00 Valor líquido das operações 1.560,00 C\nCompras à vista 14.728,00 Taxa de liquidação/CCP 6,94 D\nTaxa de Transferencia de Ativos 0,80 D\nLíquido para 20/10/2026 1.384,60 C';
const reviewParsed=sandbox.extrairOperacoesNota(reviewMixedNote,'auto');
assert.strictEqual(reviewParsed.length,3);
assert.deepStrictEqual(JSON.parse(JSON.stringify(reviewParsed.map(o=>o.taxas))),[19.87,72.24,83.29]);
assert.strictEqual(sandbox.somarTaxasNota('Valor líquido das operações 1,00 C\nLíquido para 20/10/2026 2,00 D'),3,'Debit/credit signs must be preserved when fees cross a zero net');
assert.strictEqual(sandbox.somarTaxasNota('Valor líquido das operações 100,00 D\nLíquido para 20/10/2026 103,00 D'),3);
const reviewTinyFees=Array.from({length:20},()=>({valorOperacao:1}));
sandbox.ratearTaxasNota(reviewTinyFees,.10);
assert.strictEqual(sandbox.centavosFiscais(reviewTinyFees.reduce((s,o)=>s+o.taxas,0)),10);
assert.ok(reviewTinyFees.every(o=>o.taxas>=0));

const reviewExercises=[
  {id:31001,usuarioId:901,mercado:'BR',ticker:'B3SA3',data:'2026-08-24',putExercida:'Carteira',callExercida:'Sim',callPremio:.36,callTaxas:2.16,callVencimento:'2026-10-16',acaoVenda:17.57,acaoQtd:200,acaoQtdAtual:0,acaoTaxas:19.87,acaoDataVenda:'2026-10-16',acaoDataLiquidacao:'2026-10-20'},
  {id:31002,usuarioId:901,mercado:'BR',ticker:'BBAS3',data:'2026-08-24',putExercida:'Carteira',callExercida:'Sim',callPremio:.44,callTaxas:7.91,callVencimento:'2026-10-16',acaoVenda:21.29,acaoQtd:600,acaoQtdAtual:0,acaoTaxas:72.24,acaoDataVenda:'2026-10-16',acaoDataLiquidacao:'2026-10-20'},
  {id:31003,usuarioId:901,mercado:'BR',ticker:'VALE3',data:'2026-09-10',putExercida:'Sim',putPremio:1.05,putTaxas:5.07,putQtd:200,putVencimento:'2026-10-16',acaoCompra:73.64,acaoQtd:200,acaoQtdAtual:200,acaoTaxaCompra:83.29,acaoDataCompra:'2026-10-16',acaoDataLiquidacao:'2026-10-20'},
];
assert.strictEqual(sandbox.eventosCaixaOperacaoBR(reviewExercises[2]).find(e=>e.valor<0).data,'2026-10-16','A put purchase must use its actual exercise date, not its opening date');
sandbox.carteiraMovimentos=[{usuarioId:901,mercado:'BR',tipo:'SALDO_EM_CONTA',data:'2026-10-15',valor:2.44}];
let reviewCash=sandbox.snapshotCarteiraPatrimonial(reviewExercises);
assert.strictEqual(sandbox.centavosFiscais(reviewCash.composicao.caixa),138704,'Mixed exercise cash must include both sales and the put purchase');
assert.strictEqual(sandbox.centavosFiscais(reviewCash.aReceber),138460);
assert.strictEqual(reviewCash.saldoCaixaDisponivel,2.44);
sandbox.hojeISO=()=> '2026-10-20';
reviewCash=sandbox.snapshotCarteiraPatrimonial(reviewExercises);
assert.strictEqual(sandbox.centavosFiscais(reviewCash.composicao.caixa),138704,'Settlement must not duplicate the mixed note');
assert.strictEqual(reviewCash.aReceber,0);
sandbox.carteiraMovimentos.push({usuarioId:901,mercado:'BR',tipo:'SALDO_EM_CONTA',data:'2026-10-20',valor:1387.04,id:2});
assert.strictEqual(sandbox.centavosFiscais(sandbox.snapshotCarteiraPatrimonial(reviewExercises).composicao.caixa),138704);
sandbox.hojeISO=()=> '2026-10-17';
sandbox.carteiraMovimentos=[{usuarioId:901,mercado:'BR',tipo:'SALDO_EM_CONTA',data:'2026-10-01',valor:100}];
sandbox.pagamentosDarf=[{usuarioId:901,dataPagamento:'2026-10-16',valor:15},{usuarioId:902,dataPagamento:'2026-10-16',valor:1000}];
assert.strictEqual(sandbox.snapshotCarteiraPatrimonial([]).composicao.caixa,85,'Only an actual payment from the current CPF reduces cash');
sandbox.pagamentosDarf=[];
assert.strictEqual(sandbox.snapshotCarteiraPatrimonial([]).composicao.caixa,100,'A provision for tax must not be deducted as if it had been paid');
assert.strictEqual(sandbox.pontuarOperacaoAbertaParaExercicio({...reviewExercises[0],usuarioId:902},reviewParsed[0],'CALL',false),-1);
sandbox.hojeISO=reviewToday;
sandbox.usuarioAtual=null;
const reviewDateType=sandbox.Date;
const reviewRealToday=sandbox.hojeISO;
// Exercise date defaults must follow the Brazilian calendar after midnight UTC.
sandbox.hojeISO = (new Function('return ('+script.match(/function hojeISO\(\)\{[\s\S]*?\n\}/)[0]+')'))();
sandbox.Date=class extends Date { constructor(...args){ super(...(args.length ? args : ['2026-10-18T02:59:59Z'])); } };
// Re-evaluate in the app context so the fake clock is used by the function.
vm.runInContext(script.match(/function hojeISO\(\)\{[\s\S]*?\n\}/)[0],sandbox);
assert.strictEqual(sandbox.hojeISO(),'2026-10-17');
sandbox.Date=class extends Date { constructor(...args){ super(...(args.length ? args : ['2026-10-18T03:00:00Z'])); } };
assert.strictEqual(sandbox.hojeISO(),'2026-10-18');
sandbox.Date=reviewDateType;
sandbox.hojeISO=reviewRealToday;
async function verificarSessaoEConcorrencia(){
  let requisicao;
  respostaRede = async (url, opcoes) => { requisicao = {url, opcoes}; return {ok:true}; };
  sandbox.usuarioAtual = {id:1, token:'token-de-teste'};
  const opcoes = {headers:{'Content-Type':'application/json'}};
  await sandbox.fetch('http://127.0.0.1:8080/operacoes', opcoes);
  assert.strictEqual(requisicao.opcoes.headers.Authorization, 'Bearer token-de-teste');
  assert.strictEqual(opcoes.headers.Authorization, undefined, 'Request options must not be mutated');
  await sandbox.fetch('https://example.com/cotacao');
  assert.strictEqual(requisicao.opcoes, undefined, 'Session token must never be sent to external sources');
  await sandbox.fetch('http://127.0.0.1:8080.evil.example/operacoes');
  assert.strictEqual(requisicao.opcoes, undefined, 'API prefix lookalikes must not receive session tokens');
  const buscarHistorico = sandbox.buscarHistoricoUsuario;
  const renderMercado = sandbox.renderMercadoAtual;
  let concluir;
  let renderizou = false;
  sandbox.buscarHistoricoUsuario = () => new Promise(resolve => { concluir = resolve; });
  sandbox.renderMercadoAtual = () => { renderizou = true; };
  sandbox.dadosUsuarioCarregando = false;
  const pendente = sandbox.carregarHistorico();
  sandbox.cargaDadosUsuarioSeq++;
  sandbox.usuarioAtual = {id:2, token:'outra-sessao'};
  concluir([{usuarioId:1}]);
  await pendente;
  assert.strictEqual(renderizou, false, 'Old account history must be discarded after account switching');
  sandbox.buscarHistoricoUsuario = buscarHistorico;
  sandbox.renderMercadoAtual = renderMercado;
  const buscarMovimentos = sandbox.buscarMovimentosCarteiraUsuario;
  sandbox.buscarMovimentosCarteiraUsuario = () => new Promise(resolve => { concluir = resolve; });
  const carteiraPendente = sandbox.carregarMovimentosCarteira({silencioso:true});
  sandbox.cargaDadosUsuarioSeq++;
  const carteiraNova = [{usuarioId:3, valor:123}];
  sandbox.usuarioAtual = {id:3};
  sandbox.carteiraMovimentos = carteiraNova;
  concluir([{usuarioId:2, valor:999}]);
  await carteiraPendente;
  assert.strictEqual(sandbox.carteiraMovimentos, carteiraNova, 'Late wallet response must not overwrite another account');
  sandbox.buscarMovimentosCarteiraUsuario = buscarMovimentos;
  sandbox.usuarioAtual = null;
  console.log('frontend smoke OK — fiscal, expired options, coverage, imports, settlement, payments, authentication and account switching');
}
verificarSessaoEConcorrencia().catch(error => { console.error(error); process.exitCode=1; });
