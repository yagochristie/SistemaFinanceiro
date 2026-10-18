package com.yago.wheel;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(indexes = @Index(name = "idx_darf_pagamento_usuario", columnList = "usuarioId"),
       uniqueConstraints = @UniqueConstraint(name = "uk_darf_pagamento_referencia", columnNames = {"usuarioId", "referencia"}))
public class DarfPagamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long usuarioId;
    public String competencia;
    @Column(precision = 19, scale = 2)
    public BigDecimal valor;
    public String dataPagamento;
    public String referencia;
    public Long operacaoComprovanteId;
    public String comprovanteNome;
    public String comprovanteTipo;
    @Lob @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String comprovanteDados;
}
