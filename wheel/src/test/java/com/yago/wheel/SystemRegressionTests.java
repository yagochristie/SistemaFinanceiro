package com.yago.wheel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class SystemRegressionTests extends ApiAutenticadaTestSupport {
    @Autowired ObjectMapper mapper;

    @Test
    void sessaoObrigatoriaRevogavelESemHashNaResposta() throws Exception {
        long usuario = usuario();
        mvc.perform(get("/operacoes").param("usuarioId", "" + usuario).with(semSessao())).andExpect(status().isUnauthorized());
        mvc.perform(get("/operacoes").param("usuarioId", "" + usuario).header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/operacoes").param("usuarioId", "" + usuario)).andExpect(status().isOk());
        mvc.perform(post("/usuarios/sair")).andExpect(status().isOk());
        mvc.perform(get("/operacoes").param("usuarioId", "" + usuario)).andExpect(status().isUnauthorized());
    }

    @Test
    void operacoesNaoPodemSerLidasAlteradasExcluidasOuTransferidasPorOutraConta() throws Exception {
        long dono = usuario(), outro = usuario();
        ObjectNode op = opcao(dono, "PETR4");
        long id = salvar("/operacoes", op).get("id").asLong();
        mvc.perform(get("/operacoes/" + id).with(como(outro))).andExpect(status().isForbidden());
        mvc.perform(get("/operacoes/" + id + "/comprovante").with(como(outro))).andExpect(status().isForbidden());
        mvc.perform(get("/operacoes").param("usuarioId", "" + dono).with(como(outro))).andExpect(status().isForbidden());
        mvc.perform(delete("/operacoes/" + id).with(como(outro))).andExpect(status().isForbidden());
        mvc.perform(delete("/operacoes").param("usuarioId", "" + dono).with(como(outro))).andExpect(status().isForbidden());
        mvc.perform(put("/operacoes/" + id).with(como(outro)).contentType(MediaType.APPLICATION_JSON).content(op.toString()))
                .andExpect(status().isForbidden());
        op.put("usuarioId", outro);
        mvc.perform(put("/operacoes/" + id).contentType(MediaType.APPLICATION_JSON).content(op.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/operacoes/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(dono));
    }

    @Test
    void postComIdNaoPodeSobrescreverOperacaoOuMovimentoExistente() throws Exception {
        long dono = usuario();
        ObjectNode op = opcao(dono, "VALE3");
        long id = salvar("/operacoes", op).get("id").asLong();
        op.put("id", id).put("ticker", "BBAS3");
        mvc.perform(post("/operacoes").contentType(MediaType.APPLICATION_JSON).content(op.toString())).andExpect(status().isBadRequest());
        ObjectNode movimento = movimento(dono);
        long movimentoId = salvar("/carteira/movimentos", movimento).get("id").asLong();
        movimento.put("id", movimentoId).put("valor", 999);
        mvc.perform(post("/carteira/movimentos").contentType(MediaType.APPLICATION_JSON).content(movimento.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/carteira/movimentos").param("usuarioId", "" + dono)).andExpect(jsonPath("$[0].valor").value(100));
    }

    @Test
    void todasAsCarteirasExigemSessaoEProtegemRegistrosDeOutroUsuario() throws Exception {
        long dono = usuario(), outro = usuario();
        long id = salvar("/carteira/movimentos", movimento(dono)).get("id").asLong();
        mvc.perform(delete("/carteira/movimentos/" + id).with(como(outro))).andExpect(status().isForbidden());
        for (String rota : new String[]{"/carteira/movimentos", "/criptos/posicoes", "/criptos/movimentos", "/darf-pagamentos"}) {
            mvc.perform(get(rota).param("usuarioId", "" + dono).with(semSessao())).andExpect(status().isUnauthorized());
            mvc.perform(get(rota).param("usuarioId", "" + dono).with(como(outro))).andExpect(status().isForbidden());
            mvc.perform(get(rota).param("usuarioId", "" + outro).with(como(outro))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    void compraEEdicaoDeAcaoAmericanaPreservamMercadoEMoeda() throws Exception {
        long usuario = usuario();
        ObjectNode op = mapper.createObjectNode().put("usuarioId", usuario).put("mercado", "US").put("moeda", "USD")
                .put("ticker", "AAPL").put("tipoAtivo", "ACAO").put("data", "2026-09-01")
                .put("acaoCompra", 100).put("acaoQtd", 10).put("acaoQtdAtual", 10);
        JsonNode salva = salvar("/operacoes", op);
        assertEquals("US", salva.get("mercado").asText());
        assertEquals("USD", salva.get("moeda").asText());
        op.put("acaoCompra", 110);
        mvc.perform(put("/operacoes/" + salva.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content(op.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mercado").value("US")).andExpect(jsonPath("$.moeda").value("USD"));
    }

    @Test
    void criptoMinusculaAtualizaPosicaoExistenteERejeitaNumerosForaDaPrecisao() throws Exception {
        long usuario = usuario();
        ObjectNode posicao = mapper.createObjectNode().put("usuarioId", usuario).put("simbolo", "BTC").put("quantidade", "0.25");
        long id = salvar("/criptos/posicoes", posicao).get("id").asLong();
        posicao.put("simbolo", " btc ").put("quantidade", "0.5000000000000");
        assertEquals(id, salvar("/criptos/posicoes", posicao).get("id").asLong());
        for (String quantidade : new String[]{"1E+20", "10000000000000000", "0.0000000000001", "-1", "0"}) {
            posicao.put("quantidade", quantidade);
            mvc.perform(post("/criptos/posicoes").contentType(MediaType.APPLICATION_JSON).content(posicao.toString())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/criptos/posicoes").param("usuarioId", "" + usuario)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].quantidade").value(0.5));
    }

    @Test
    void importacaoCriptoInvalidaReverteTodoOArquivo() throws Exception {
        long usuario = usuario();
        ObjectNode pedido = mapper.createObjectNode().put("usuarioId", usuario).put("simbolo", "BTC").put("produtoCoinbase", "BTC-USD");
        var registros = pedido.putArray("registros");
        registros.addObject().put("referencia", "compra").put("data", "2026-09-01T12:00:00").put("tipo", "COMPRA")
                .put("quantidade", "1").put("precoUsd", "100").put("totalUsd", "100");
        registros.addObject().put("referencia", "venda").put("data", "2026-09-02T12:00:00").put("tipo", "VENDA")
                .put("quantidade", "2").put("precoUsd", "100").put("totalUsd", "200");
        mvc.perform(post("/criptos/movimentos/importar").contentType(MediaType.APPLICATION_JSON).content(pedido.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/criptos/movimentos").param("usuarioId", "" + usuario)).andExpect(jsonPath("$.length()").value(0));
        ((ObjectNode) registros.get(1)).put("quantidade", "1E+20");
        mvc.perform(post("/criptos/movimentos/importar").contentType(MediaType.APPLICATION_JSON).content(pedido.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/criptos/posicoes").param("usuarioId", "" + usuario)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pagamentosEOperacoesRejeitamAnexosAtivosEVazios() throws Exception {
        long usuario = usuario();
        for (String dados : new String[]{"data:text/html;base64,PHNjcmlwdD4=", "data:image/svg+xml;base64,PHN2Zz4=", "data:application/pdf;base64,"}) {
            ObjectNode pagamento = mapper.createObjectNode().put("usuarioId", usuario).put("competencia", "2026-09").put("valor", 30)
                    .put("dataPagamento", "2026-10-01").put("comprovanteDados", dados);
            mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString())).andExpect(status().isBadRequest());
            ObjectNode op = opcao(usuario, "PETR4").put("darfComprovanteDados", dados);
            mvc.perform(post("/operacoes").contentType(MediaType.APPLICATION_JSON).content(op.toString())).andExpect(status().isBadRequest());
        }
    }

    @Test
    void agrupamentoDeContratosDiferentesNaoApagaPosicoes() throws Exception {
        long usuario = usuario();
        long primeira = salvar("/operacoes", opcao(usuario, "PETR4")).get("id").asLong();
        long segunda = salvar("/operacoes", opcao(usuario, "VALE3")).get("id").asLong();
        ObjectNode pedido = mapper.createObjectNode().put("usuarioId", usuario);
        pedido.putArray("operacaoIds").add(primeira).add(segunda);
        pedido.set("operacaoAgrupada", opcao(usuario, "PETR4").put("putQtd", 200));
        mvc.perform(post("/operacoes/agrupar").contentType(MediaType.APPLICATION_JSON).content(pedido.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/operacoes").param("usuarioId", "" + usuario)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void datasComEspacosSaoNormalizadasAntesDaValidacaoDeLiquidacao() throws Exception {
        long usuario = usuario();
        ObjectNode op = opcao(usuario, "BBAS3").put("acaoDataCompra", " 2026-09-01 ").put("acaoDataLiquidacao", " 2026-09-03 ");
        assertEquals("2026-09-03", salvar("/operacoes", op).get("acaoDataLiquidacao").asText());
    }

    @Test
    void preflightPermiteCabecalhosDeAutenticacaoEFontesDeMercado() throws Exception {
        mvc.perform(options("/operacoes").header("Origin", "null").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,x-brapi-token,x-coingecko-demo-key"))
                .andExpect(status().isOk()).andExpect(header().exists("Access-Control-Allow-Headers"));
        mvc.perform(options("/operacoes").header("Origin", "https://example.com").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroInvalidoRetornaErroDeValidacao() throws Exception {
        ObjectNode cadastro = mapper.createObjectNode().put("login", "x".repeat(256)).put("senha", "senha123");
        mvc.perform(post("/usuarios/cadastrar").contentType(MediaType.APPLICATION_JSON).content(cadastro.toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/usuarios/login").contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"inexistente\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enviosSimultaneosNaoDuplicamOperacoesProventosCriptosOuPagamentos() throws Exception {
        long usuario = usuario();
        ObjectNode provento = movimento(usuario).put("tipo", "PROVENTO").put("referencia", "provento:concorrente");
        ObjectNode cripto = mapper.createObjectNode().put("usuarioId", usuario).put("simbolo", "BTC").put("quantidade", "0.25");
        ObjectNode pagamento = mapper.createObjectNode().put("usuarioId", usuario).put("competencia", "2026-09").put("valor", 30)
                .put("dataPagamento", "2026-10-01").put("referencia", "recibo-concorrente");
        verificarEnviosSimultaneos("/operacoes", opcao(usuario, "PETR4"), java.util.List.of(200, 409));
        verificarEnviosSimultaneos("/carteira/movimentos", provento, java.util.List.of(200, 409));
        verificarEnviosSimultaneos("/criptos/posicoes", cripto, java.util.List.of(200, 200));
        verificarEnviosSimultaneos("/darf-pagamentos", pagamento, java.util.List.of(200, 200));
        for (String rota : new String[]{"/operacoes", "/carteira/movimentos", "/criptos/posicoes", "/darf-pagamentos"}) {
            mvc.perform(get(rota).param("usuarioId", "" + usuario)).andExpect(jsonPath("$.length()").value(1));
        }
    }

    private void verificarEnviosSimultaneos(String rota, ObjectNode dados, java.util.List<Integer> esperados) throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var iniciar = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> enviar = () -> {
                iniciar.await();
                return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(dados.toString()))
                        .andReturn().getResponse().getStatus();
            };
            var primeiro = executor.submit(enviar);
            var segundo = executor.submit(enviar);
            iniciar.countDown();
            var recebidos = new java.util.ArrayList<>(java.util.List.of(
                    primeiro.get(15, java.util.concurrent.TimeUnit.SECONDS), segundo.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            recebidos.sort(Integer::compareTo);
            assertEquals(esperados, recebidos, rota);
        } finally {
            executor.shutdownNow();
        }
    }

    private long usuario() throws Exception {
        ObjectNode cadastro = mapper.createObjectNode().put("nome", "Conta ficticia de testes").put("login", "teste-" + UUID.randomUUID()).put("senha", "senha-teste");
        JsonNode resposta = salvar("/usuarios/cadastrar", cadastro);
        registrarSessao(resposta);
        return resposta.get("id").asLong();
    }

    private JsonNode salvar(String rota, ObjectNode dados) throws Exception {
        return mapper.readTree(mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(dados.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.senhaHash").doesNotExist()).andReturn().getResponse().getContentAsString());
    }

    private ObjectNode opcao(long usuario, String ticker) {
        return mapper.createObjectNode().put("usuarioId", usuario).put("mercado", "BR").put("ticker", ticker).put("data", "2026-09-01")
                .put("putExercida", "Pendente").put("putStrike", 30).put("putPremio", 1).put("putQtd", 100).put("putVencimento", "2026-10-30");
    }

    private ObjectNode movimento(long usuario) {
        return mapper.createObjectNode().put("usuarioId", usuario).put("mercado", "BR").put("data", "2026-09-01").put("tipo", "APORTE").put("valor", 100);
    }
}
