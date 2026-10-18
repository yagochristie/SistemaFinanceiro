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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Fontes públicas e opcionais de dados para o painel consolidado. */
@RestController
@RequestMapping("/mercado")
public class MercadoController {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CACHE_TTL = Duration.ofSeconds(45);
    private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper;
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    public MercadoController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/cambio/dolar")
    public CambioResponse cotacaoDolar() {
        CacheEntry<?> entry = cache.get("dolar");
        if (entry != null && !entry.expirou()) {
            return (CambioResponse) entry.valor();
        }
        CambioResponse resposta = buscarDolarPtax();
        cache.put("dolar", new CacheEntry<>(resposta));
        return resposta;
    }

    @GetMapping("/cripto")
    public List<CriptoCotacaoResponse> cotacoesCripto(
            @RequestParam(defaultValue = "") String ativos,
            @RequestHeader(value = "X-CoinGecko-Demo-Key", required = false) String apiKey) {
        if (ativos == null || ativos.isBlank()) {
            return List.of();
        }
        List<AtivoCripto> pedidos = normalizarAtivos(ativos);
        if (pedidos.isEmpty()) {
            return List.of();
        }
        String chave = "cripto|" + pedidos.stream().map(AtivoCripto::coinGeckoId).sorted().reduce("", (a, b) -> a + "," + b);
        CacheEntry<?> entry = cache.get(chave);
        if (entry != null && !entry.expirou()) {
            @SuppressWarnings("unchecked")
            List<CriptoCotacaoResponse> cached = (List<CriptoCotacaoResponse>) entry.valor();
            return remapearSimbolos(pedidos, cached);
        }
        List<CriptoCotacaoResponse> cotacoes = buscarCriptos(pedidos, apiKey);
        cache.put(chave, new CacheEntry<>(cotacoes));
        return cotacoes;
    }

    private CambioResponse buscarDolarPtax() {
        LocalDate hoje = LocalDate.now();
        for (int i = 0; i < 8; i++) {
            LocalDate data = hoje.minusDays(i);
            try {
                String dataFormatada = BCB_DATE.format(data);
                String url = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"
                        + "CotacaoDolarDia(dataCotacao=@dataCotacao)?@dataCotacao='" + dataFormatada
                        + "'&$top=100&$format=json";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }
                JsonNode valores = mapper.readTree(response.body()).path("value");
                if (!valores.isArray() || valores.isEmpty()) {
                    continue;
                }
                double venda = valores.get(valores.size() - 1).path("cotacaoVenda").asDouble(Double.NaN);
                if (Double.isFinite(venda) && venda > 0) {
                    return new CambioResponse(venda, data.toString(), "Banco Central do Brasil - PTAX", null);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            } catch (RuntimeException ignored) {
                // Tenta o dia útil anterior antes de declarar a indisponibilidade.
            }
        }
        return new CambioResponse(null, null, "Banco Central do Brasil - PTAX", "Cotacao do dolar indisponivel agora");
    }

    private List<CriptoCotacaoResponse> buscarCriptos(List<AtivoCripto> pedidos, String apiKey) {
        List<CriptoCotacaoResponse> resultado = new ArrayList<>();
        for (AtivoCripto ativo : pedidos) {
            CriptoCotacaoResponse coinbase = buscarNaCoinbase(ativo);
            if (coinbase.precoBrl() != null || coinbase.precoUsd() != null) {
                resultado.add(coinbase);
                continue;
            }
            CriptoCotacaoResponse alternativa = buscarCriptosCoinGecko(List.of(ativo), apiKey).get(0);
            resultado.add(new CriptoCotacaoResponse(alternativa.simbolo(), alternativa.coinGeckoId(), alternativa.precoBrl(),
                    alternativa.precoUsd(), alternativa.variacao24h(), "CoinGecko (reserva)", alternativa.erro()));
        }
        return resultado;
    }

    private CriptoCotacaoResponse buscarNaCoinbase(AtivoCripto ativo) {
        CoinbaseStats stats = buscarEstatisticasCoinbase(ativo.simbolo() + "-USD");
        Double usd = stats.preco() != null ? stats.preco() : buscarPrecoCoinbase(ativo.simbolo() + "-USD");
        Double brl = buscarPrecoCoinbase(ativo.simbolo() + "-BRL");
        String origem = "Coinbase";
        if (brl == null && usd != null) {
            CambioResponse dolar = cotacaoDolar();
            if (dolar.taxa() != null) {
                brl = usd * dolar.taxa();
                origem = "Coinbase + PTAX";
            }
        }
        return new CriptoCotacaoResponse(ativo.simbolo(), ativo.coinGeckoId(), brl, usd, stats.variacao24h(), origem,
                brl == null && usd == null ? "Cotacao Coinbase indisponivel" : null);
    }

    private CoinbaseStats buscarEstatisticasCoinbase(String produto) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.exchange.coinbase.com/products/" + produto + "/stats"))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new CoinbaseStats(null, null);
            }
            JsonNode dados = mapper.readTree(response.body());
            Double ultimo = valorFinitoTexto(dados.path("last").asText(""));
            Double abertura = valorFinitoTexto(dados.path("open").asText(""));
            Double variacao = ultimo != null && abertura != null && abertura > 0
                    ? (ultimo / abertura - 1) * 100
                    : null;
            return new CoinbaseStats(ultimo, variacao);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CoinbaseStats(null, null);
        } catch (RuntimeException ignored) {
            return new CoinbaseStats(null, null);
        }
    }

    private Double buscarPrecoCoinbase(String produto) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.coinbase.com/v2/prices/" + produto + "/spot"))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String amount = mapper.readTree(response.body()).path("data").path("amount").asText("");
            double preco = new java.math.BigDecimal(amount).doubleValue();
            return Double.isFinite(preco) && preco > 0 ? preco : null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Double valorFinitoTexto(String valor) {
        try {
            double numero = new java.math.BigDecimal(valor).doubleValue();
            return Double.isFinite(numero) && numero > 0 ? numero : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<CriptoCotacaoResponse> buscarCriptosCoinGecko(List<AtivoCripto> pedidos, String apiKey) {
        String ids = pedidos.stream().map(AtivoCripto::coinGeckoId).distinct()
                .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8)).reduce((a, b) -> a + "," + b).orElse("");
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + ids
                + "&vs_currencies=brl,usd&include_24hr_change=true";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("x-cg-demo-api-key", apiKey.trim());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return indisponiveis(pedidos, "Configure uma chave Demo do CoinGecko para atualizar as cotacoes");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return indisponiveis(pedidos, "Fonte de cotacao cripto indisponivel");
            }
            JsonNode raiz = mapper.readTree(response.body());
            List<CriptoCotacaoResponse> resultado = new ArrayList<>();
            for (AtivoCripto ativo : pedidos) {
                JsonNode dado = raiz.path(ativo.coinGeckoId());
                Double brl = valorFinito(dado, "brl");
                Double usd = valorFinito(dado, "usd");
                Double variacao = valorFinito(dado, "usd_24h_change");
                resultado.add(new CriptoCotacaoResponse(ativo.simbolo(), ativo.coinGeckoId(), brl, usd, variacao,
                        "CoinGecko", brl == null && usd == null ? "Cotacao indisponivel" : null));
            }
            return resultado;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return indisponiveis(pedidos, "Nao foi possivel atualizar as criptomoedas agora");
        }
    }

    private Double valorFinito(JsonNode node, String campo) {
        if (node == null || !node.hasNonNull(campo)) {
            return null;
        }
        double valor = node.path(campo).asDouble(Double.NaN);
        return Double.isFinite(valor) && valor >= 0 ? valor : null;
    }

    private List<CriptoCotacaoResponse> indisponiveis(List<AtivoCripto> pedidos, String erro) {
        return pedidos.stream().map(ativo -> new CriptoCotacaoResponse(
                ativo.simbolo(), ativo.coinGeckoId(), null, null, null, "CoinGecko", erro)).toList();
    }

    private List<CriptoCotacaoResponse> remapearSimbolos(List<AtivoCripto> pedidos, List<CriptoCotacaoResponse> cached) {
        Map<String, CriptoCotacaoResponse> porId = new LinkedHashMap<>();
        cached.forEach(cotacao -> porId.put(cotacao.coinGeckoId(), cotacao));
        return pedidos.stream().map(ativo -> {
            CriptoCotacaoResponse cotacao = porId.get(ativo.coinGeckoId());
            return cotacao == null
                    ? new CriptoCotacaoResponse(ativo.simbolo(), ativo.coinGeckoId(), null, null, null, "CoinGecko", "Cotacao indisponivel")
                    : new CriptoCotacaoResponse(ativo.simbolo(), ativo.coinGeckoId(), cotacao.precoBrl(), cotacao.precoUsd(),
                            cotacao.variacao24h(), cotacao.origem(), cotacao.erro());
        }).toList();
    }

    private List<AtivoCripto> normalizarAtivos(String ativos) {
        Map<String, AtivoCripto> unicos = new LinkedHashMap<>();
        for (String entrada : Objects.toString(ativos, "").split(",")) {
            String[] partes = entrada.trim().split(":", 2);
            String simbolo = partes.length > 0 ? partes[0].trim().toUpperCase(Locale.ROOT) : "";
            if (!simbolo.matches("[A-Z0-9]{2,16}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ativo cripto invalido");
            }
            String id = partes.length == 2 && !partes[1].isBlank()
                    ? partes[1].trim().toLowerCase(Locale.ROOT)
                    : idPadrao(simbolo);
            if (id == null || !id.matches("[a-z0-9-]{2,80}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de cotacao invalido para " + simbolo);
            }
            unicos.put(simbolo, new AtivoCripto(simbolo, id));
        }
        return new ArrayList<>(unicos.values());
    }

    private String idPadrao(String simbolo) {
        return Map.ofEntries(
                Map.entry("BTC", "bitcoin"), Map.entry("ETH", "ethereum"), Map.entry("USDT", "tether"),
                Map.entry("USDC", "usd-coin"), Map.entry("SOL", "solana"), Map.entry("BNB", "binancecoin"),
                Map.entry("XRP", "ripple"), Map.entry("ADA", "cardano"), Map.entry("DOGE", "dogecoin"),
                Map.entry("AVAX", "avalanche-2"), Map.entry("LINK", "chainlink"), Map.entry("DOT", "polkadot"),
                Map.entry("LTC", "litecoin"), Map.entry("ATOM", "cosmos"), Map.entry("UNI", "uniswap"),
                Map.entry("AAVE", "aave"), Map.entry("SUI", "sui"), Map.entry("ARB", "arbitrum"),
                Map.entry("OP", "optimism"), Map.entry("TRX", "tron"), Map.entry("PEPE", "pepe"))
                .get(simbolo);
    }

    private record AtivoCripto(String simbolo, String coinGeckoId) { }
    private record CoinbaseStats(Double preco, Double variacao24h) { }

    private record CacheEntry<T>(T valor, java.time.Instant recebidoEm) {
        CacheEntry(T valor) { this(valor, java.time.Instant.now()); }
        boolean expirou() { return recebidoEm.plus(CACHE_TTL).isBefore(java.time.Instant.now()); }
    }

    public record CambioResponse(Double taxa, String data, String origem, String erro) { }
    public record CriptoCotacaoResponse(String simbolo, String coinGeckoId, Double precoBrl, Double precoUsd,
            Double variacao24h, String origem, String erro) { }
}
