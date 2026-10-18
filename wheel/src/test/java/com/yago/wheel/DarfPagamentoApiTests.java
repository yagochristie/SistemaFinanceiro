package com.yago.wheel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DarfPagamentoApiTests extends ApiAutenticadaTestSupport {
    @Autowired ObjectMapper mapper;

    @Test
    void pagamentosSaoIsoladosIdempotentesEComprovantesNaoVazamNaLista() throws Exception {
        long usuario = usuario("pagamento-a"), outro = usuario("pagamento-b");
        ObjectNode pagamento = pagamento(usuario);
        pagamento.put("comprovanteNome", "darf.pdf");
        pagamento.put("comprovanteTipo", "application/pdf");
        pagamento.put("comprovanteDados", "data:application/pdf;base64,JVBERi0xLjQK");
        String resultado = mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(26.79))
            .andExpect(jsonPath("$.comprovanteDados").doesNotExist()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(resultado).get("id").asLong();
        String repetido = mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(id, mapper.readTree(repetido).get("id").asLong());
        mvc.perform(get("/darf-pagamentos").param("usuarioId", "" + usuario))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/darf-pagamentos").param("usuarioId", "" + outro).with(como(outro)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/darf-pagamentos/" + id + "/comprovante").param("usuarioId", "" + outro).with(como(outro))).andExpect(status().isNotFound());
        mvc.perform(get("/darf-pagamentos/" + id + "/comprovante").param("usuarioId", "" + usuario))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dados").value("data:application/pdf;base64,JVBERi0xLjQK"));
        mvc.perform(delete("/darf-pagamentos/" + id).param("usuarioId", "" + outro).with(como(outro))).andExpect(status().isNotFound());
        mvc.perform(delete("/darf-pagamentos/" + id).param("usuarioId", "" + usuario)).andExpect(status().isOk());
    }

    @Test
    void pagamentosRejeitamDatasValoresReferenciasEAnexosInvalidos() throws Exception {
        long usuario = usuario("pagamento-validacao");
        mvc.perform(get("/darf-pagamentos")).andExpect(status().isBadRequest());
        for (String[] invalido : new String[][]{{"competencia","2026-13"},{"dataPagamento","2026-02-31"},{"comprovanteDados","texto"}}) {
            ObjectNode pagamento = pagamento(usuario).put(invalido[0], invalido[1]);
            mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString())).andExpect(status().isBadRequest());
        }
        for (double valor : new double[]{0,-1,26.791}) {
            ObjectNode pagamento = pagamento(usuario).put("valor", valor);
            mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString())).andExpect(status().isBadRequest());
        }
        ObjectNode pagamento = pagamento(usuario);
        mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString())).andExpect(status().isOk());
        pagamento.put("valor",30);
        mvc.perform(post("/darf-pagamentos").contentType(MediaType.APPLICATION_JSON).content(pagamento.toString())).andExpect(status().isConflict());
    }

    private ObjectNode pagamento(long usuario) {
        return mapper.createObjectNode().put("usuarioId",usuario).put("competencia","2026-06")
            .put("valor",26.79).put("dataPagamento","2026-07-30").put("referencia","recibo-unico");
    }

    private long usuario(String login) throws Exception {
        String body = mapper.createObjectNode().put("nome","Teste fiscal").put("login",login).put("senha","senha123").toString();
        JsonNode resposta = mapper.readTree(mvc.perform(post("/usuarios/cadastrar").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        registrarSessao(resposta);
        return resposta.get("id").asLong();
    }
}
