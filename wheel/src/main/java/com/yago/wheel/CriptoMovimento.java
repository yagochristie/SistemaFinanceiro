package com.yago.wheel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(indexes = @Index(name = "idx_cripto_movimento_usuario_simbolo", columnList = "usuarioId,simbolo"),
        uniqueConstraints = @UniqueConstraint(name = "uk_cripto_movimento_usuario_referencia", columnNames = { "usuarioId", "referencia" }))
public class CriptoMovimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 16)
    private String simbolo;

    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(nullable = false)
    private LocalDateTime data;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantidade;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal precoUsd;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal totalUsd;

    @Column(nullable = false, length = 160)
    private String referencia;

    @Column(nullable = false, length = 80)
    private String origem;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getSimbolo() { return simbolo; }
    public void setSimbolo(String simbolo) { this.simbolo = simbolo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }
    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }
    public BigDecimal getPrecoUsd() { return precoUsd; }
    public void setPrecoUsd(BigDecimal precoUsd) { this.precoUsd = precoUsd; }
    public BigDecimal getTotalUsd() { return totalUsd; }
    public void setTotalUsd(BigDecimal totalUsd) { this.totalUsd = totalUsd; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
}
