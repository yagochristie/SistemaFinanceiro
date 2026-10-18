package com.yago.wheel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CarteiraAtualizadaApiTests extends ApiAutenticadaTestSupport {


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void atualizaPosicaoCriptoSemDuplicarEProtegeOutroUsuario() throws Exception {
        long usuario = criarUsuario("cripto-api-user");
        long outroUsuario = criarUsuario("cripto-outro-user");

        MvcResult criada = mvc.perform(post("/criptos/posicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuario + ",\"simbolo\":\"btc\",\"quantidade\":\"0.12500000\",\"precoMedioUsd\":\"62500\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simbolo").value("BTC"))
                .andExpect(jsonPath("$.coinGeckoId").value(nullValue()))
                .andExpect(jsonPath("$.quantidade").value(0.125))
                .andExpect(jsonPath("$.precoMedioUsd").value(62500))
                .andReturn();
        long id = objectMapper.readTree(criada.getResponse().getContentAsString()).path("id").asLong();

        mvc.perform(post("/criptos/posicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuario + ",\"simbolo\":\"BTC\",\"coinGeckoId\":\"bitcoin\",\"quantidade\":\"0.25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.quantidade").value(0.25));

        mvc.perform(get("/criptos/posicoes").param("usuarioId", String.valueOf(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coinGeckoId").value("bitcoin"));

        mvc.perform(put("/criptos/posicoes/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + outroUsuario + ",\"simbolo\":\"BTC\",\"quantidade\":1}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/criptos/posicoes/" + id).param("usuarioId", String.valueOf(outroUsuario)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/criptos/posicoes/" + id).param("usuarioId", String.valueOf(usuario)))
                .andExpect(status().isOk());
    }

    @Test
    void registraProventoSemContarComoAporteEDeduplicaReferencia() throws Exception {
        long usuario = criarUsuario("provento-api-user");
        String movimento = "{\"usuarioId\":" + usuario
                + ",\"mercado\":\"BR\",\"data\":\"2026-10-13\",\"tipo\":\"PROVENTO\",\"valor\":12.34"
                + ",\"descricao\":\"Dividendo PETR4\",\"referencia\":\"provento:PETR4:2026-10-13:0.1234\"}";

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PROVENTO"))
                .andExpect(jsonPath("$.referencia").value("provento:PETR4:2026-10-13:0.1234"));

        mvc.perform(post("/carteira/movimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimento))
                .andExpect(status().isConflict());
    }

    @Test
    void fontesDeMercadoComConsultaVaziaNaoFazemChamadaExterna() throws Exception {
        mvc.perform(get("/mercado/cripto").param("ativos", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/proventos").param("tickers", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(0));
    }

    @Test
    void importaHistoricoBtcSemDuplicarEAtualizaSaldo() throws Exception {
        long usuario = criarUsuario("btc-csv-user");
        String csv = "{\"usuarioId\":" + usuario + ",\"simbolo\":\"BTC\",\"produtoCoinbase\":\"BTC-USD\",\"registros\":["
                + "{\"referencia\":\"btc-csv-1\",\"data\":\"2026-05-15T14:30:41\",\"tipo\":\"COMPRA\",\"quantidade\":\"0.001\",\"precoUsd\":\"100000\",\"totalUsd\":\"100\"},"
                + "{\"referencia\":\"btc-csv-2\",\"data\":\"2026-05-16T14:30:41\",\"tipo\":\"COMPRA\",\"quantidade\":\"0.0005\",\"precoUsd\":\"101000\",\"totalUsd\":\"50.5\"}]}";

        mvc.perform(post("/criptos/movimentos/importar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(2))
                .andExpect(jsonPath("$.ignorados").value(0))
                .andExpect(jsonPath("$.saldo").value(0.0015))
                .andExpect(jsonPath("$.posicao.simbolo").value("BTC"));

        mvc.perform(post("/criptos/movimentos/importar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(0))
                .andExpect(jsonPath("$.ignorados").value(2));

        mvc.perform(get("/criptos/movimentos").param("usuarioId", String.valueOf(usuario)).param("simbolo", "BTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private long criarUsuario(String login) throws Exception {
        MvcResult result = mvc.perform(post("/usuarios/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"login\":\"" + login + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode usuario = objectMapper.readTree(result.getResponse().getContentAsString());
        registrarSessao(usuario);
        return usuario.path("id").asLong();
    }
}
