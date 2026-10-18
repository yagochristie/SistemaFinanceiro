package com.yago.wheel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/criptos/movimentos")
public class CriptoMovimentoController {

    private final CriptoMovimentoRepository movimentos;
    private final CriptoPosicaoRepository posicoes;
    private final UsuarioRepository usuarios;
    private final AcessoUsuario acesso;

    public CriptoMovimentoController(CriptoMovimentoRepository movimentos, CriptoPosicaoRepository posicoes,
            UsuarioRepository usuarios, AcessoUsuario acesso) {
        this.movimentos = movimentos;
        this.posicoes = posicoes;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @GetMapping
    public List<CriptoMovimento> listar(@RequestParam Long usuarioId, @RequestParam(required = false) String simbolo) {
        validarUsuario(usuarioId);
        if (simbolo == null || simbolo.isBlank()) {
            return movimentos.findByUsuarioIdOrderByDataDescIdDesc(usuarioId);
        }
        return movimentos.findByUsuarioIdAndSimboloOrderByDataDescIdDesc(usuarioId, normalizarSimbolo(simbolo));
    }

    @Transactional
    @org.springframework.web.bind.annotation.PostMapping("/importar")
    public ImportacaoResponse importar(@RequestBody ImportacaoRequest pedido) {
        if (pedido == null || pedido.usuarioId() == null || pedido.registros() == null || pedido.registros().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um movimento de criptomoeda");
        }
        acesso.bloquearGravacao(pedido.usuarioId());
        validarUsuario(pedido.usuarioId());
        String simbolo = normalizarSimbolo(pedido.simbolo());
        String produtoCoinbase = normalizarProduto(pedido.produtoCoinbase(), simbolo);
        Set<String> referenciasDoArquivo = new HashSet<>();
        if (pedido.registros().size() > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importe no maximo 10000 movimentos por arquivo");
        int importados = 0;
        int ignorados = 0;

        for (ImportacaoLinha linha : pedido.registros()) {
            validarLinha(linha);
            String referencia = linha.referencia().trim();
            if (!referenciasDoArquivo.add(referencia) || movimentos.findByUsuarioIdAndReferencia(pedido.usuarioId(), referencia).isPresent()) {
                ignorados++;
                continue;
            }
            CriptoMovimento movimento = new CriptoMovimento();
            movimento.setUsuarioId(pedido.usuarioId());
            movimento.setSimbolo(simbolo);
            movimento.setTipo(linha.tipo().trim().toUpperCase(Locale.ROOT));
            movimento.setData(linha.data());
            movimento.setQuantidade(linha.quantidade().abs());
            movimento.setPrecoUsd(linha.precoUsd().abs());
            movimento.setTotalUsd(linha.totalUsd().abs());
            movimento.setReferencia(referencia);
            movimento.setOrigem("CSV");
            movimentos.save(movimento);
            importados++;
        }

        BigDecimal saldo = calcularSaldo(pedido.usuarioId(), simbolo);
        CriptoPosicao posicao = posicoes.findByUsuarioIdAndSimbolo(pedido.usuarioId(), simbolo).orElseGet(CriptoPosicao::new);
        posicao.setUsuarioId(pedido.usuarioId());
        posicao.setSimbolo(simbolo);
        posicao.setCoinGeckoId(produtoCoinbase);
        if (saldo.signum() < 0 || saldo.precision() - saldo.scale() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo importado invalido; confira compras e vendas do arquivo");
        }
        posicao.setQuantidade(saldo.setScale(12, RoundingMode.UNNECESSARY));
        posicao.setAtualizadoEm(Instant.now());
        CriptoPosicao salva = posicoes.save(posicao);
        return new ImportacaoResponse(importados, ignorados, saldo, salva);
    }

    private BigDecimal calcularSaldo(Long usuarioId, String simbolo) {
        return movimentos.findByUsuarioIdAndSimboloOrderByDataDescIdDesc(usuarioId, simbolo).stream()
                .map(movimento -> "VENDA".equals(movimento.getTipo())
                        ? movimento.getQuantidade().negate() : movimento.getQuantidade())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarLinha(ImportacaoLinha linha) {
        if (linha == null || linha.data() == null || linha.referencia() == null || linha.referencia().isBlank()
                || linha.referencia().length() > 160 || linha.tipo() == null
                || !(linha.tipo().equalsIgnoreCase("COMPRA") || linha.tipo().equalsIgnoreCase("VENDA"))
                || ValidacaoCripto.decimalInvalido(linha.quantidade(), 16, 12)
                || ValidacaoCripto.decimalInvalido(linha.precoUsd(), 16, 8)
                || ValidacaoCripto.decimalInvalido(linha.totalUsd(), 16, 8)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimento de criptomoeda invalido");
        }
    }

    private String normalizarSimbolo(String simbolo) {
        return ValidacaoCripto.simbolo(simbolo);
    }

    private String normalizarProduto(String produto, String simbolo) {
        String normalizado = Objects.toString(produto, "").trim().toUpperCase(Locale.ROOT);
        if (normalizado.isBlank()) {
            return simbolo + "-USD";
        }
        if (!normalizado.matches("[A-Z0-9]{2,16}-[A-Z]{3,8}") || !normalizado.startsWith(simbolo + "-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto Coinbase invalido");
        }
        return normalizado;
    }

    private void validarUsuario(Long usuarioId) {
        acesso.exigir(usuarioId);
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }
    }

    public record ImportacaoRequest(Long usuarioId, String simbolo, String produtoCoinbase, List<ImportacaoLinha> registros) { }
    public record ImportacaoLinha(String referencia, LocalDateTime data, String tipo, BigDecimal quantidade,
            BigDecimal precoUsd, BigDecimal totalUsd) { }
    public record ImportacaoResponse(int importados, int ignorados, BigDecimal saldo, CriptoPosicao posicao) { }
}
