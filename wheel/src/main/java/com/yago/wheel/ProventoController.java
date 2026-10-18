package com.yago.wheel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consulta eventos anunciados de dividendos e JCP. A API nunca cria um crédito
 * financeiro por conta própria: o cliente confirma o recebimento na carteira.
 */
@RestController
@RequestMapping("/proventos")
public class ProventoController {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper;

    public ProventoController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ProventosResponse listar(
            @RequestParam(defaultValue = "") String tickers,
            @RequestHeader(value = "X-Brapi-Token", required = false) String token) {
        List<String> ativos = normalizarTickers(tickers);
        if (ativos.isEmpty()) {
            return new ProventosResponse(List.of(), "brapi", false, null);
        }
        if (token == null || token.isBlank()) {
            return new ProventosResponse(List.of(), "brapi", true,
                    "Informe sua chave da brapi para consultar dividendos e JCP automaticamente");
        }
        String symbols = ativos.stream().map(ticker -> URLEncoder.encode(ticker, StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "," + b).orElse("");
        LocalDate inicio = LocalDate.now().minusDays(45);
        LocalDate fim = LocalDate.now().plusDays(370);
        String url = "https://brapi.dev/api/v2/stocks/dividends?symbols=" + symbols
                + "&startDate=" + inicio + "&endDate=" + fim + "&sortOrder=asc";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return new ProventosResponse(List.of(), "brapi", true, "A chave da brapi nao foi aceita");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProventosResponse(List.of(), "brapi", false, "Fonte de proventos indisponivel agora");
            }
            return new ProventosResponse(extrairEventos(mapper.readTree(response.body()), ativos), "brapi", false, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProventosResponse(List.of(), "brapi", false, "Nao foi possivel atualizar os proventos agora");
        }
    }

    private List<ProventoEvento> extrairEventos(JsonNode raiz, List<String> ativos) {
        JsonNode eventos = raiz.path("dividends");
        if (!eventos.isArray()) {
            eventos = raiz.path("results");
        }
        List<ProventoEvento> resultado = new ArrayList<>();
        Set<String> chaves = new LinkedHashSet<>();
        for (JsonNode evento : eventos) {
            String ticker = evento.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
            if (!ativos.contains(ticker)) {
                continue;
            }
            double valor = evento.path("rate").asDouble(Double.NaN);
            if (!Double.isFinite(valor) || valor <= 0) {
                continue;
            }
            String dataCom = dataIso(evento.path("lastDatePrior").asText(null));
            String pagamento = dataIso(evento.path("paymentDate").asText(null));
            String tipo = evento.path("label").asText("Provento");
            String chave = ticker + "|" + tipo + "|" + valor + "|" + dataCom + "|" + pagamento;
            if (chaves.add(chave)) {
                resultado.add(new ProventoEvento(ticker, tipo, valor, dataCom, pagamento,
                        dataIso(evento.path("approvedOn").asText(null))));
            }
        }
        resultado.sort(java.util.Comparator.comparing(ProventoEvento::dataPagamento,
                java.util.Comparator.nullsLast(String::compareTo)));
        return resultado;
    }

    private String dataIso(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String data = valor.trim();
        return data.length() >= 10 && data.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}") ? data.substring(0, 10) : null;
    }

    private List<String> normalizarTickers(String tickers) {
        Set<String> unicos = new LinkedHashSet<>();
        for (String raw : Objects.toString(tickers, "").split(",")) {
            String ticker = raw.trim().toUpperCase(Locale.ROOT);
            if (!ticker.isEmpty() && !ticker.matches("[A-Z0-9]{4,12}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker invalido");
            }
            if (!ticker.isEmpty()) {
                unicos.add(ticker);
            }
        }
        if (unicos.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe no maximo 20 ativos por consulta");
        }
        return new ArrayList<>(unicos);
    }

    public record ProventosResponse(List<ProventoEvento> eventos, String origem, boolean requerChave, String erro) { }
    public record ProventoEvento(String ticker, String tipo, double valorPorAcao, String dataCom,
            String dataPagamento, String dataAprovacao) { }
}
