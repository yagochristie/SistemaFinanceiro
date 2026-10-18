package com.yago.wheel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiBehaviorTests extends ApiAutenticadaTestSupport {


    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void operacoesExigemUsuarioELoginNaoRetornaHash() throws Exception {
        MvcResult usuarioResult = mvc.perform(post("/usuarios/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"login\":\"api-user\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andReturn();

        JsonNode usuarioJson = objectMapper.readTree(usuarioResult.getResponse().getContentAsString());
        registrarSessao(usuarioJson);
        long usuarioId = usuarioJson.get("id").asLong();

        mvc.perform(get("/operacoes"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "18/06/2026", "2026-07-31", 28.50, 1.25, 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        mvc.perform(get("/operacoes").param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("PETR4"));
    }

    @Test
    void loginLegadoContinuaFuncionandoEAtualizaHash() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Legado");
        usuario.setLogin("legacy-user");
        usuario.setSenhaHash(hashLegado("legacy-user", "senha-antiga"));
        usuarios.save(usuario);

        mvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"legacy-user\",\"senha\":\"senha-antiga\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        Usuario atualizado = usuarios.findByLogin("legacy-user").orElseThrow();
        assertTrue(atualizado.getSenhaHash().startsWith("pbkdf2$"));
    }

    @Test
    void cotacoesSemTickersRetornaListaVazia() throws Exception {
        mvc.perform(get("/cotacoes").param("mercado", "BR").param("tickers", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/cotacoes").param("mercado", "INVALIDO").param("tickers", "PETR4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operacoesRejeitamDadosInvalidosEDuplicados() throws Exception {
        long usuarioId = criarUsuario("validacao-user");
        String operacao = operacaoJson(usuarioId, "PETR4", "18/06/2026", "2026-07-31", 28.50, 1.25, 100);

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacao))
                .andExpect(status().isOk());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacao))
                .andExpect(status().isConflict());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "31/02/2026", "2026-07-31", 28.50, 1.25, 100)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "18/06/2026", "2026-02-31", 28.50, 1.25, 100)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "18/06/2026", "", 28.50, 1.25, 100)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "18/06/2026", "2026-07-31", 28.50, -1.25, 100)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "—", "18/06/2026", "2026-07-31", 28.50, 1.25, 100)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void normalizaTickerB3ParaCodigoCorreto() throws Exception {
        long usuarioId = criarUsuario("b3sa-user");

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "B3AS3", "18/06/2026", "2026-07-31", 12.00, 0.80, 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("B3SA3"));
    }

    @Test
    void salvaMovimentosDaCarteiraPorUsuario() throws Exception {
        long usuarioId = criarUsuario("carteira-user");

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"data\":\"26/08/2026\",\"tipo\":\"aporte\",\"valor\":1500.50,\"descricao\":\"PIX corretora\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId))
                .andExpect(jsonPath("$.mercado").value("BR"))
                .andExpect(jsonPath("$.data").value("2026-08-26"))
                .andExpect(jsonPath("$.tipo").value("APORTE"))
                .andExpect(jsonPath("$.valor").value(1500.50));

        mvc.perform(get("/carteira/movimentos").param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("PIX corretora"));

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"data\":\"2026-08-26\",\"tipo\":\"APORTE\",\"valor\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"INVALIDO\",\"data\":\"2026-08-26\",\"tipo\":\"APORTE\",\"valor\":100}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"data\":\"2026-08-26\",\"tipo\":\"SALDO_REAL\",\"valor\":100}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"US\",\"data\":\"2026-08-26\",\"tipo\":\"AJUSTE\",\"valor\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salvaSaldoDisponivelEmContaSemAlterarOMovimentoDeAporte() throws Exception {
        long usuarioId = criarUsuario("saldo-em-conta-user");

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"BR\",\"data\":\"2026-10-02\",\"tipo\":\"saldo_em_conta\",\"valor\":1170.79,\"descricao\":\"Saldo disponível\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mercado").value("BR"))
                .andExpect(jsonPath("$.tipo").value("SALDO_EM_CONTA"))
                .andExpect(jsonPath("$.valor").value(1170.79));
    }

    @Test
    void movimentosDaCarteiraSaoSeparadosPorMercado() throws Exception {
        long usuarioId = criarUsuario("carteira-mercado-user");

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"BR\",\"data\":\"2026-08-26\",\"tipo\":\"APORTE\",\"valor\":1000,\"descricao\":\"PIX B3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mercado").value("BR"));

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"US\",\"data\":\"2026-08-26\",\"tipo\":\"APORTE\",\"valor\":500,\"descricao\":\"Remessa exterior\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mercado").value("US"));

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + ",\"mercado\":\"US\",\"data\":\"2026-08-27\",\"tipo\":\"RETIRADA\",\"valor\":100,\"descricao\":\"Retirada exterior\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mercado").value("US"))
                .andExpect(jsonPath("$.tipo").value("RETIRADA"));

        mvc.perform(get("/carteira/movimentos")
                        .param("usuarioId", String.valueOf(usuarioId))
                        .param("mercado", "BR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("PIX B3"));

        mvc.perform(get("/carteira/movimentos")
                        .param("usuarioId", String.valueOf(usuarioId))
                        .param("mercado", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].descricao").value("Retirada exterior"))
                .andExpect(jsonPath("$[1].descricao").value("Remessa exterior"));
    }

    @Test
    void edicaoNaoPodeVirarDuplicada() throws Exception {
        long usuarioId = criarUsuario("edicao-duplicada-user");

        MvcResult primeira = mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "VALE3", "18/06/2026", "2026-07-31", 60.00, 1.10, 100)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult segunda = mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "VALE3", "19/06/2026", "2026-08-31", 62.00, 1.20, 100)))
                .andExpect(status().isOk())
                .andReturn();

        long primeiroId = objectMapper.readTree(primeira.getResponse().getContentAsString()).get("id").asLong();
        long segundoId = objectMapper.readTree(segunda.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(put("/operacoes/" + segundoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "VALE3", "18/06/2026", "2026-07-31", 60.00, 1.10, 100)))
                .andExpect(status().isConflict());

        mvc.perform(put("/operacoes/" + primeiroId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "VALE3", "18/06/2026", "2026-07-31", 60.00, 1.10, 100)))
                .andExpect(status().isOk());
    }

    @Test
    void edicaoDeOperacaoInexistenteRetornaNotFoundMesmoComCorpoDuplicado() throws Exception {
        long usuarioId = criarUsuario("edicao-inexistente-user");
        String operacao = operacaoJson(usuarioId, "ITUB4", "18/06/2026", "2026-07-31", 32.00, 1.10, 100);

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacao))
                .andExpect(status().isOk());

        mvc.perform(put("/operacoes/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void comprovanteDarfNaoPesaHistoricoEPreservaAoEditar() throws Exception {
        long usuarioId = criarUsuario("comprovante-user");
        String comprovante = "data:application/pdf;base64,JVBERi0xLjQ=";

        MvcResult criada = mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJsonComComprovante(
                                usuarioId, "B3SA3", "18/06/2026", "2026-07-31", 12.00, 0.80, 100, comprovante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfComprovanteDados").doesNotExist())
                .andReturn();

        long operacaoId = objectMapper.readTree(criada.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/operacoes").param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].darfComprovanteNome").value("darf.pdf"))
                .andExpect(jsonPath("$[0].darfComprovanteDados").doesNotExist());

        mvc.perform(get("/operacoes/" + operacaoId + "/comprovante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("darf.pdf"))
                .andExpect(jsonPath("$.tipo").value("application/pdf"))
                .andExpect(jsonPath("$.dados").value(comprovante));

        mvc.perform(put("/operacoes/" + operacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "B3SA3", "18/06/2026", "2026-07-31", 12.00, 0.80, 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfComprovanteDados").doesNotExist());

        mvc.perform(get("/operacoes/" + operacaoId + "/comprovante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados").value(comprovante));

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJsonComComprovante(
                                usuarioId, "B3SA3", "19/06/2026", "2026-08-31", 12.00, 0.80, 100,
                                "data:text/plain;base64,SGVsbG8=")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salvaEtfRendaFixaSemDarfMensal() throws Exception {
        long usuarioId = criarUsuario("etf-rf-user");

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(etfCompraRendaFixaJson(usuarioId, "lftb11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("LFTB11"))
                .andExpect(jsonPath("$.tipoAtivo").value("ETF_RF"))
                .andExpect(jsonPath("$.fase").value("Compra ETF renda fixa"))
                .andExpect(jsonPath("$.statusOperacao").value("Em carteira"))
                .andExpect(jsonPath("$.darfStatus").value("Sem DARF"))
                .andExpect(jsonPath("$.etfQuantidade").value(6))
                .andExpect(jsonPath("$.etfPrecoVenda").value(0.0))
                .andExpect(jsonPath("$.etfAliquota").value(0.15))
                .andExpect(jsonPath("$.etfIrRetido").value(0.0))
                .andExpect(jsonPath("$.ir").value(0.0))
                .andExpect(jsonPath("$.irrf").value(0.0));

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(etfRendaFixaJson(usuarioId, "lfts11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("LFTS11"))
                .andExpect(jsonPath("$.tipoAtivo").value("ETF_RF"))
                .andExpect(jsonPath("$.mercado").value("BR"))
                .andExpect(jsonPath("$.darfStatus").value("Sem DARF"))
                .andExpect(jsonPath("$.darfVencimento").doesNotExist())
                .andExpect(jsonPath("$.etfQuantidade").value(10))
                .andExpect(jsonPath("$.fase").value("Venda ETF renda fixa"))
                .andExpect(jsonPath("$.statusOperacao").value("Encerrada"))
                .andExpect(jsonPath("$.etfAliquota").value(0.25))
                .andExpect(jsonPath("$.etfIrRetido").value(5.0))
                .andExpect(jsonPath("$.ir").value(5.0))
                .andExpect(jsonPath("$.irrf").value(5.0))
                .andExpect(jsonPath("$.lucro").value(15.0));

        mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(etfRendaFixaJson(usuarioId, "BOVA11")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agrupaOperacoesDoMesmoUsuarioEmUmaUnicaPosicao() throws Exception {
        long usuarioId = criarUsuario("agrupar-opcoes-user");
        long primeira = objectMapper.readTree(mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "01/09/2026", "2026-10-31", 30.00, 1.20, 100)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long segunda = objectMapper.readTree(mvc.perform(post("/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operacaoJson(usuarioId, "PETR4", "10/09/2026", "2026-10-31", 30.00, 1.50, 200)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String agrupada = operacaoJson(usuarioId, "PETR4", "01/09/2026", "2026-10-31", 30.00, 1.40, 300);
        String pedido = "{\"usuarioId\":" + usuarioId
                + ",\"operacaoIds\":[" + primeira + "," + segunda + "]"
                + ",\"operacaoAgrupada\":" + agrupada + "}";

        mvc.perform(post("/operacoes/agrupar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.putQtd").value(300))
                .andExpect(jsonPath("$.putPremio").value(1.40));

        mvc.perform(get("/operacoes").param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].putQtd").value(300));
    }

    @Test
    void liquidacaoAceitaCompraPorExercicioSemPermitirDataAnterior() throws Exception {
        long usuarioId = criarUsuario("liquidacao-put-user");
        var compra = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                operacaoJson(usuarioId, "VALE3", "10/09/2026", "2026-10-16", 73.64, 1.05, 200));
        compra.put("putExercida", "Sim");
        compra.put("acaoCompra", 73.64);
        compra.put("acaoQtd", 200);
        compra.put("acaoQtdAtual", 200);
        compra.put("acaoTaxaCompra", 83.29);
        compra.put("acaoDataCompra", "2026-10-16");
        compra.put("acaoDataLiquidacao", "2026-10-20");
        var salvo = mvc.perform(post("/operacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acaoDataCompra").value("2026-10-16"))
                .andExpect(jsonPath("$.acaoDataLiquidacao").value("2026-10-20"))
                .andReturn();
        long id = objectMapper.readTree(salvo.getResponse().getContentAsString()).get("id").asLong();
        compra.put("acaoDataLiquidacao", "2026-10-15");
        mvc.perform(put("/operacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compra)))
                .andExpect(status().isBadRequest());
        compra.remove("acaoDataCompra");
        compra.put("acaoDataLiquidacao", "2026-10-20");
        mvc.perform(put("/operacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compra)))
                .andExpect(status().isBadRequest());
        compra.put("acaoDataCompra", "2026-10-16");
        compra.put("acaoDataVenda", "2026-10-21");
        mvc.perform(put("/operacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compra)))
                .andExpect(status().isBadRequest());
    }

    private long criarUsuario(String login) throws Exception {
        MvcResult usuarioResult = mvc.perform(post("/usuarios/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"login\":\"" + login + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode usuario = objectMapper.readTree(usuarioResult.getResponse().getContentAsString());
        registrarSessao(usuario);
        return usuario.get("id").asLong();
    }

    private String operacaoJson(long usuarioId, String ticker, String data, String vencimento, double strike, double premio, int qtd) {
        String vencimentoJson = vencimento == null || vencimento.isBlank() ? "null" : "\"" + vencimento + "\"";
        return "{"
                + "\"usuarioId\":" + usuarioId + ","
                + "\"mercado\":\"BR\","
                + "\"ticker\":\"" + ticker + "\","
                + "\"data\":\"" + data + "\","
                + "\"putExercida\":\"Pendente\","
                + "\"putStrike\":" + strike + ","
                + "\"putPremio\":" + premio + ","
                + "\"putQtd\":" + qtd + ","
                + "\"putTaxas\":1.00,"
                + "\"putVencimento\":" + vencimentoJson + ","
                + "\"premios\":" + (premio * qtd) + ","
                + "\"ir\":0,"
                + "\"lucro\":0"
                + "}";
    }

    private String etfRendaFixaJson(long usuarioId, String ticker) {
        return "{"
                + "\"usuarioId\":" + usuarioId + ","
                + "\"mercado\":\"US\","
                + "\"moeda\":\"USD\","
                + "\"tipoAtivo\":\"ETF_RF\","
                + "\"ticker\":\"" + ticker + "\","
                + "\"data\":\"20/08/2026\","
                + "\"etfDataCompra\":\"2026-08-01\","
                + "\"etfDataVenda\":\"2026-08-20\","
                + "\"etfQuantidade\":10,"
                + "\"etfPrecoCompra\":100.0,"
                + "\"etfPrecoVenda\":102.0,"
                + "\"etfTaxasCompra\":0.0,"
                + "\"etfTaxasVenda\":0.0,"
                + "\"etfAliquota\":0.15,"
                + "\"etfIrRetido\":null,"
                + "\"ir\":0.0,"
                + "\"irrf\":0.0,"
                + "\"lucro\":0.0,"
                + "\"darfStatus\":\"Pendente\""
                + "}";
    }

    private String etfCompraRendaFixaJson(long usuarioId, String ticker) {
        return "{"
                + "\"usuarioId\":" + usuarioId + ","
                + "\"mercado\":\"US\","
                + "\"moeda\":\"USD\","
                + "\"tipoAtivo\":\"ETF_RF\","
                + "\"ticker\":\"" + ticker + "\","
                + "\"data\":\"26/08/2026\","
                + "\"etfDataCompra\":\"2026-08-26\","
                + "\"etfQuantidade\":6,"
                + "\"etfPrecoCompra\":125.73,"
                + "\"etfTaxasCompra\":5.01,"
                + "\"darfStatus\":\"Pendente\""
                + "}";
    }

    private String operacaoJsonComComprovante(
            long usuarioId, String ticker, String data, String vencimento, double strike, double premio, int qtd, String comprovante) {
        String base = operacaoJson(usuarioId, ticker, data, vencimento, strike, premio, qtd);
        return base.substring(0, base.length() - 1)
                + ",\"darfStatus\":\"Pago\""
                + ",\"darfPagoEm\":\"2026-08-31\""
                + ",\"darfComprovanteNome\":\"darf.pdf\""
                + ",\"darfComprovanteTipo\":\"application/pdf\""
                + ",\"darfComprovanteDados\":\"" + comprovante + "\""
                + "}";
    }

    private String hashLegado(String login, String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((login + ":" + senha).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
