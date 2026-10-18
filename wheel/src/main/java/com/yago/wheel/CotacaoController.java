package com.yago.wheel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cotacoes")
public class CotacaoController {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(8);
    private static final String ORIGEM = "TradingView";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    private final ObjectMapper mapper;
    private final Map<String, CotacaoCache> cache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> simbolosResolvidos = new ConcurrentHashMap<>();

    public CotacaoController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<CotacaoResponse> listar(
            @RequestParam(defaultValue = "BR") String mercado,
            @RequestParam(defaultValue = "") String tickers) {
        String mercadoNormalizado = normalizarMercado(mercado);
        List<TickerInfo> pedidos = normalizarTickers(mercadoNormalizado, tickers);
        if (pedidos.isEmpty()) {
            return List.of();
        }

        List<TickerInfo> vencidos = pedidos.stream()
                .filter(pedido -> {
                    CotacaoCache cached = cache.get(pedido.chave());
                    return cached == null || cached.expirou();
                })
                .toList();
        if (!vencidos.isEmpty()) {
            buscarCotacoesExternas(vencidos);
        }

        return pedidos.stream()
                .map(pedido -> {
                    CotacaoCache cached = cache.get(pedido.chave());
                    if (cached == null) {
                        return CotacaoResponse.indisponivel(pedido.ticker(), mercadoNormalizado);
                    }
                    return cached.response();
                })
                .toList();
    }

    private void buscarCotacoesExternas(List<TickerInfo> pedidos) {
        try {
            Set<String> atendidos = consultarScannerTradingView(pedidos);
            List<TickerInfo> pendentes = pedidos.stream()
                    .filter(pedido -> !atendidos.contains(pedido.chave()))
                    .toList();
            if (!pendentes.isEmpty()) {
                pendentes.forEach(this::resolverSimbolosTradingView);
                atendidos.addAll(consultarScannerTradingView(pendentes));
            }

            pedidos.stream()
                    .filter(pedido -> !atendidos.contains(pedido.chave()))
                    .forEach(pedido -> cache.put(pedido.chave(), new CotacaoCache(CotacaoResponse.indisponivel(pedido.ticker(), pedido.mercado()))));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            marcarIndisponiveis(pedidos, "Nao foi possivel atualizar a cotacao agora");
        }
    }

    private Set<String> consultarScannerTradingView(List<TickerInfo> pedidos) throws IOException, InterruptedException {
        if (pedidos.isEmpty()) {
            return Set.of();
        }
        URI uri = URI.create("https://scanner.tradingview.com/" + scannerMercado(pedidos.get(0).mercado()) + "/scan");
        ObjectNode body = montarPayloadTradingView(pedidos);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.tradingview.com")
                .header("Referer", "https://www.tradingview.com/")
                .header("User-Agent", "SoftDarf/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        Map<String, TickerInfo> porSymbol = new LinkedHashMap<>();
        pedidos.forEach(pedido -> symbols(pedido).forEach(symbol -> porSymbol.put(symbol, pedido)));
        Set<String> atendidos = new HashSet<>();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Fonte de cotacao retornou HTTP " + response.statusCode());
        }
        JsonNode resultados = mapper.readTree(response.body()).path("data");
        for (JsonNode node : resultados) {
            String symbol = node.path("s").asText("").toUpperCase(Locale.ROOT);
            TickerInfo pedido = porSymbol.get(symbol);
            if (pedido == null || atendidos.contains(pedido.chave())) {
                continue;
            }
            JsonNode dados = node.path("d");
            double preco = dados.path(0).asDouble(Double.NaN);
            if (!Double.isFinite(preco) || preco <= 0) {
                continue;
            }
            String moeda = dados.path(2).asText(pedido.mercado().equals("US") ? "USD" : "BRL");
            Double variacao = dados.hasNonNull(1)
                    ? dados.path(1).asDouble()
                    : null;
            cache.put(pedido.chave(), new CotacaoCache(new CotacaoResponse(
                    pedido.ticker(),
                    pedido.mercado(),
                    preco,
                    moeda,
                    variacao,
                    Instant.now().toString(),
                    ORIGEM,
                    null)));
            atendidos.add(pedido.chave());
        }
        return atendidos;
    }

    private ObjectNode montarPayloadTradingView(List<TickerInfo> pedidos) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode columns = body.putArray("columns");
        columns.add("close");
        columns.add("change");
        columns.add("currency");
        columns.add("update_mode");

        ObjectNode symbols = body.putObject("symbols");
        ArrayNode tickers = symbols.putArray("tickers");
        pedidos.forEach(pedido -> symbols(pedido).forEach(tickers::add));

        ArrayNode range = body.putArray("range");
        range.add(0);
        range.add(Math.max(1, tickers.size()));

        ObjectNode options = body.putObject("options");
        options.put("lang", "pt");
        return body;
    }

    private String scannerMercado(String mercado) {
        return mercado.equals("US") ? "america" : "brazil";
    }

    private List<String> symbols(TickerInfo pedido) {
        List<String> resolvidos = simbolosResolvidos.getOrDefault(pedido.chave(), List.of());
        if (resolvidos.isEmpty()) {
            return pedido.directSymbols();
        }
        List<String> todos = new ArrayList<>(resolvidos);
        pedido.directSymbols().forEach(symbol -> {
            if (!todos.contains(symbol)) {
                todos.add(symbol);
            }
        });
        return todos;
    }

    private void resolverSimbolosTradingView(TickerInfo pedido) {
        if (simbolosResolvidos.containsKey(pedido.chave())) {
            return;
        }
        List<String> resolvidos = buscarSimbolosTradingView(pedido);
        simbolosResolvidos.put(pedido.chave(), resolvidos);
    }

    private List<String> buscarSimbolosTradingView(TickerInfo pedido) {
        String url = "https://symbol-search.tradingview.com/symbol_search/"
                + "?text=" + URLEncoder.encode(pedido.tickerCotacao(), StandardCharsets.UTF_8)
                + "&lang=pt"
                + "&domain=production";
        if (pedido.mercado().equals("BR")) {
            url += "&exchange=BMFBOVESPA";
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Origin", "https://www.tradingview.com")
                .header("Referer", "https://www.tradingview.com/")
                .header("User-Agent", "SoftDarf/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode resultados = mapper.readTree(response.body());
            List<String> encontrados = new ArrayList<>();
            for (JsonNode node : resultados) {
                String symbol = node.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
                String exchange = node.path("exchange").asText("").trim().toUpperCase(Locale.ROOT);
                String fullName = node.path("full_name").asText("").trim().toUpperCase(Locale.ROOT);
                String tickerLimpo = limparTickerBusca(symbol);
                if (!tickerLimpo.equals(pedido.tickerCotacao())) {
                    continue;
                }
                String candidato = fullName.contains(":") ? fullName : (exchange.isBlank() ? "" : exchange + ":" + symbol);
                if (!candidato.isBlank() && !encontrados.contains(candidato)) {
                    encontrados.add(candidato);
                }
            }
            return encontrados;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    private String limparTickerBusca(String symbol) {
        String valor = Objects.toString(symbol, "").trim().toUpperCase(Locale.ROOT);
        int doisPontos = valor.indexOf(':');
        if (doisPontos >= 0) {
            valor = valor.substring(doisPontos + 1);
        }
        int pipe = valor.indexOf('|');
        if (pipe >= 0) {
            valor = valor.substring(0, pipe);
        }
        if (valor.endsWith(".SA")) {
            valor = valor.substring(0, valor.length() - 3);
        }
        return valor;
    }

    private void marcarIndisponiveis(List<TickerInfo> pedidos, String erro) {
        pedidos.forEach(pedido -> {
            CotacaoCache cached = cache.get(pedido.chave());
            if (cached == null) {
                cache.put(pedido.chave(), new CotacaoCache(new CotacaoResponse(
                        pedido.ticker(),
                        pedido.mercado(),
                        null,
                        pedido.mercado().equals("US") ? "USD" : "BRL",
                        null,
                        Instant.now().toString(),
                        ORIGEM,
                        erro)));
            }
        });
    }

    private List<TickerInfo> normalizarTickers(String mercado, String tickers) {
        Map<String, TickerInfo> unicos = new LinkedHashMap<>();
        for (String raw : tickers.split(",")) {
            String ticker = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (ticker.endsWith(".SA")) {
                ticker = ticker.substring(0, ticker.length() - 3);
            }
            if (!ticker.matches("[A-Z0-9.]{2,20}")) {
                continue;
            }
            TickerInfo info = new TickerInfo(ticker, mercado, tickerCotacao(ticker, mercado));
            unicos.put(info.chave(), info);
        }
        return new ArrayList<>(unicos.values());
    }

    private String tickerCotacao(String ticker, String mercado) {
        if (mercado.equals("BR") && (ticker.equals("B3AS3") || ticker.equals("B3SA"))) {
            return "B3SA3";
        }
        return ticker;
    }

    private String normalizarMercado(String mercado) {
        String valor = Objects.toString(mercado, "").trim().toUpperCase(Locale.ROOT);
        if (valor.isBlank() || valor.equals("BR") || valor.equals("B3") || valor.equals("BRASIL")) {
            return "BR";
        }
        if (valor.equals("US") || valor.equals("USA") || valor.equals("EUA")
                || valor.equals("AMERICANO") || valor.equals("AMERICANA")) {
            return "US";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mercado invalido");
    }

    private record TickerInfo(String ticker, String mercado, String tickerCotacao) {
        private List<String> directSymbols() {
            if (mercado.equals("BR")) {
                return List.of("BMFBOVESPA:" + tickerCotacao);
            }
            return List.of(
                    "NASDAQ:" + tickerCotacao,
                    "NYSE:" + tickerCotacao,
                    "AMEX:" + tickerCotacao,
                    "OTC:" + tickerCotacao,
                    "CBOE:" + tickerCotacao);
        }

        String chave() {
            return mercado + "|" + ticker;
        }
    }

    private record CotacaoCache(CotacaoResponse response, Instant recebidaEm) {
        CotacaoCache(CotacaoResponse response) {
            this(response, Instant.now());
        }

        boolean expirou() {
            return recebidaEm.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }

    public record CotacaoResponse(
            String ticker,
            String mercado,
            Double preco,
            String moeda,
            Double variacaoPercentual,
            String horario,
            String origem,
            String erro) {
        static CotacaoResponse indisponivel(String ticker, String mercado) {
            return new CotacaoResponse(
                    ticker,
                    mercado,
                    null,
                    mercado.equals("US") ? "USD" : "BRL",
                    null,
                    Instant.now().toString(),
                    ORIGEM,
                    "Cotacao indisponivel");
        }
    }
}
