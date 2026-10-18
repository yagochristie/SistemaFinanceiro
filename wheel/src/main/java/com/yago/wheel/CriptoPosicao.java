package com.yago.wheel;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        indexes = @Index(name = "idx_cripto_posicao_usuario", columnList = "usuarioId"),
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_cripto_posicao_usuario_simbolo", columnNames = { "usuarioId", "simbolo" }))
public class CriptoPosicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 16)
    private String simbolo;

    @Column(length = 80)
    private String coinGeckoId;

    @Column(nullable = false, precision = 28, scale = 12)
    private BigDecimal quantidade;

    @Column(precision = 28, scale = 12)
    private BigDecimal precoMedioUsd;

    @Column(nullable = false)
    private Instant atualizadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getSimbolo() { return simbolo; }
    public void setSimbolo(String simbolo) { this.simbolo = simbolo; }

    public String getCoinGeckoId() { return coinGeckoId; }
    public void setCoinGeckoId(String coinGeckoId) { this.coinGeckoId = coinGeckoId; }

    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoMedioUsd() { return precoMedioUsd; }
    public void setPrecoMedioUsd(BigDecimal precoMedioUsd) { this.precoMedioUsd = precoMedioUsd; }

    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
