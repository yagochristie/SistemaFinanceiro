package com.yago.wheel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/criptos/posicoes")
public class CriptoPosicaoController {

    private final CriptoPosicaoRepository posicoes;
    private final UsuarioRepository usuarios;
    private final AcessoUsuario acesso;

    public CriptoPosicaoController(CriptoPosicaoRepository posicoes, UsuarioRepository usuarios, AcessoUsuario acesso) {
        this.posicoes = posicoes;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @GetMapping
    public List<CriptoPosicao> listar(@RequestParam Long usuarioId) {
        validarUsuario(usuarioId);
        return posicoes.findByUsuarioIdOrderBySimboloAsc(usuarioId);
    }

    /**
     * Cria uma posição. Se o ativo já existir, a quantidade informada passa a
     * ser o novo saldo da posição, evitando linhas duplicadas para a mesma cripto.
     */
    @PostMapping
    @Transactional
    public CriptoPosicao salvar(@RequestBody CriptoPosicao entrada) {
        if (entrada == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posicao obrigatoria");
        acesso.bloquearGravacao(entrada.getUsuarioId());
        validarEntrada(entrada);
        CriptoPosicao destino = posicoes.findByUsuarioIdAndSimbolo(entrada.getUsuarioId(), entrada.getSimbolo())
                .orElseGet(CriptoPosicao::new);
        copiar(entrada, destino);
        return posicoes.save(destino);
    }

    @PutMapping("/{id}")
    @Transactional
    public CriptoPosicao atualizar(@PathVariable Long id, @RequestBody CriptoPosicao entrada) {
        validarEntrada(entrada);
        CriptoPosicao destino = posicoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posicao cripto nao encontrada"));
        if (!Objects.equals(destino.getUsuarioId(), entrada.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Posicao pertence a outro usuario");
        }
        posicoes.findByUsuarioIdAndSimbolo(entrada.getUsuarioId(), entrada.getSimbolo())
                .filter(existente -> !Objects.equals(existente.getId(), id))
                .ifPresent(existente -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe uma posicao para este ativo");
                });
        copiar(entrada, destino);
        return posicoes.save(destino);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id, @RequestParam Long usuarioId) {
        validarUsuario(usuarioId);
        CriptoPosicao posicao = posicoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posicao cripto nao encontrada"));
        if (!Objects.equals(posicao.getUsuarioId(), usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Posicao pertence a outro usuario");
        }
        posicoes.delete(posicao);
    }

    private void copiar(CriptoPosicao origem, CriptoPosicao destino) {
        destino.setUsuarioId(origem.getUsuarioId());
        destino.setSimbolo(normalizarSimbolo(origem.getSimbolo()));
        destino.setCoinGeckoId(normalizarCoinGeckoId(origem.getCoinGeckoId()));
        destino.setQuantidade(origem.getQuantidade().stripTrailingZeros());
        destino.setPrecoMedioUsd(normalizarPrecoMedioUsd(origem.getPrecoMedioUsd()));
        destino.setAtualizadoEm(Instant.now());
    }

    private void validarEntrada(CriptoPosicao posicao) {
        if (posicao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posicao cripto obrigatoria");
        }
        validarUsuario(posicao.getUsuarioId());
        posicao.setSimbolo(normalizarSimbolo(posicao.getSimbolo()));
        normalizarCoinGeckoId(posicao.getCoinGeckoId());
        BigDecimal quantidade = posicao.getQuantidade();
        if (ValidacaoCripto.decimalInvalido(quantidade, 16, 12)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade de cripto invalida");
        }
        normalizarPrecoMedioUsd(posicao.getPrecoMedioUsd());
    }

    private void validarUsuario(Long usuarioId) {
        acesso.exigir(usuarioId);
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }
    }

    private String normalizarSimbolo(String simbolo) {
        return ValidacaoCripto.simbolo(simbolo);
    }

    private String normalizarCoinGeckoId(String coinGeckoId) {
        String valor = Objects.toString(coinGeckoId, "").trim().toLowerCase(Locale.ROOT);
        if (valor.isEmpty()) {
            return null;
        }
        if (!valor.matches("[a-z0-9-]{2,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de cotacao invalido");
        }
        return valor;
    }

    private BigDecimal normalizarPrecoMedioUsd(BigDecimal precoMedioUsd) {
        if (precoMedioUsd == null) {
            return null;
        }
        if (ValidacaoCripto.decimalInvalido(precoMedioUsd, 16, 12)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preco medio em dolar invalido");
        }
        return precoMedioUsd.stripTrailingZeros();
    }
}
