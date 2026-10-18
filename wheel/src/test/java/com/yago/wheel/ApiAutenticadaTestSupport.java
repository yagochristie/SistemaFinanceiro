package com.yago.wheel;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

abstract class ApiAutenticadaTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired private WebApplicationContext context;
    private final Map<Long, String> tokens = new HashMap<>();

    protected void registrarSessao(JsonNode usuario) {
        boolean primeira = tokens.isEmpty();
        tokens.put(usuario.path("id").asLong(), usuario.path("token").asText());
        if (primeira) {
            mvc = MockMvcBuilders.webAppContextSetup(context)
                    .defaultRequest(get("/").header("Authorization", "Bearer " + usuario.path("token").asText()))
                    .build();
        }
    }

    protected RequestPostProcessor como(long usuarioId) {
        return request -> {
            request.removeHeader("Authorization");
            request.addHeader("Authorization", "Bearer " + tokens.get(usuarioId));
            return request;
        };
    }

    protected RequestPostProcessor semSessao() {
        return request -> { request.removeHeader("Authorization"); return request; };
    }
}
