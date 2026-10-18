package com.yago.wheel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(indexes = @Index(name = "idx_operacao_usuario_id", columnList = "usuarioId"))
public class Operacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // — Identificação —
    private String mercado;
    private String moeda;
    private String ticker;
    private String data;
    private String fase;
    private String statusOperacao;
    private Long usuarioId;
    private String tipoAtivo;

    // — PUT —
    private String putExercida;
    private Double putStrike;
    private Double putPremio;
    private Integer putQtd;
    private Double putTaxas;
    private Double putFechamento;
    private Double putTaxasFechamento;
    private String putDataFechamento;
    private String putVencimento;   // formato YYYY-MM-DD

    // — CALL —
    private String callExercida;
    private Double callStrike;
    private Double callPremio;
    private Double callTaxas;
    private Double callFechamento;
    private Double callTaxasFechamento;
    private String callDataFechamento;
    private String callVencimento;  // formato YYYY-MM-DD

    // — Ação —
    private Double acaoCompra;
    private Double acaoTaxaCompra;
    private String acaoDataCompra;
    private Double acaoVenda;
    private String acaoDataVenda;
    private String acaoDataLiquidacao;
    private Double acaoTaxas;
    private Integer acaoQtd;
    private Integer acaoQtdAtual;

    // — ETF de renda fixa —
    private Integer etfQuantidade;
    private Double etfPrecoCompra;
    private Double etfPrecoVenda;
    private Double etfTaxasCompra;
    private Double etfTaxasVenda;
    private String etfDataCompra;
    private String etfDataVenda;
    private Double etfIrRetido;
    private Double etfAliquota;

    // — Resultado —
    private Double premios;
    private Double ir;
    private Double irrf;
    private String irrfData;
    private Double lucro;
    private Boolean dayTrade;
    private Double taxaCambio;
    private String dataCambio;
    private Double premiosBrl;
    private Double lucroBrl;
    private String darfStatus;
    private String darfVencimento;
    private String darfPagoEm;
    private String darfComprovanteNome;
    private String darfComprovanteTipo;
    @Lob
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String darfComprovanteDados;

    // ── Getters / Setters ──────────────────────────────
    public String getAcaoDataCompra() { return acaoDataCompra; }
    public void setAcaoDataCompra(String valor) { acaoDataCompra = valor; }
    public String getIrrfData() { return irrfData; }
    public void setIrrfData(String valor) { irrfData = valor; }
    public String getAcaoDataVenda() { return acaoDataVenda; }
    public void setAcaoDataVenda(String valor) { acaoDataVenda = valor; }
    public String getAcaoDataLiquidacao() { return acaoDataLiquidacao; }
    public void setAcaoDataLiquidacao(String valor) { acaoDataLiquidacao = valor; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMercado() { return mercado; }
    public void setMercado(String mercado) { this.mercado = mercado; }

    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }

    public String getStatusOperacao() { return statusOperacao; }
    public void setStatusOperacao(String statusOperacao) { this.statusOperacao = statusOperacao; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTipoAtivo() { return tipoAtivo; }
    public void setTipoAtivo(String tipoAtivo) { this.tipoAtivo = tipoAtivo; }

    public String getPutExercida() { return putExercida; }
    public void setPutExercida(String putExercida) { this.putExercida = putExercida; }

    public Double getPutStrike() { return putStrike; }
    public void setPutStrike(Double putStrike) { this.putStrike = putStrike; }

    public Double getPutPremio() { return putPremio; }
    public void setPutPremio(Double putPremio) { this.putPremio = putPremio; }

    public Integer getPutQtd() { return putQtd; }
    public void setPutQtd(Integer putQtd) { this.putQtd = putQtd; }

    public Double getPutTaxas() { return putTaxas; }
    public void setPutTaxas(Double putTaxas) { this.putTaxas = putTaxas; }

    public Double getPutFechamento() { return putFechamento; }
    public void setPutFechamento(Double putFechamento) { this.putFechamento = putFechamento; }

    public Double getPutTaxasFechamento() { return putTaxasFechamento; }
    public void setPutTaxasFechamento(Double putTaxasFechamento) { this.putTaxasFechamento = putTaxasFechamento; }

    public String getPutDataFechamento() { return putDataFechamento; }
    public void setPutDataFechamento(String putDataFechamento) { this.putDataFechamento = putDataFechamento; }

    public String getPutVencimento() { return putVencimento; }
    public void setPutVencimento(String putVencimento) { this.putVencimento = putVencimento; }

    public String getCallExercida() { return callExercida; }
    public void setCallExercida(String callExercida) { this.callExercida = callExercida; }

    public Double getCallStrike() { return callStrike; }
    public void setCallStrike(Double callStrike) { this.callStrike = callStrike; }

    public Double getCallPremio() { return callPremio; }
    public void setCallPremio(Double callPremio) { this.callPremio = callPremio; }

    public Double getCallTaxas() { return callTaxas; }
    public void setCallTaxas(Double callTaxas) { this.callTaxas = callTaxas; }

    public Double getCallFechamento() { return callFechamento; }
    public void setCallFechamento(Double callFechamento) { this.callFechamento = callFechamento; }

    public Double getCallTaxasFechamento() { return callTaxasFechamento; }
    public void setCallTaxasFechamento(Double callTaxasFechamento) { this.callTaxasFechamento = callTaxasFechamento; }

    public String getCallDataFechamento() { return callDataFechamento; }
    public void setCallDataFechamento(String callDataFechamento) { this.callDataFechamento = callDataFechamento; }

    public String getCallVencimento() { return callVencimento; }
    public void setCallVencimento(String callVencimento) { this.callVencimento = callVencimento; }

    public Double getAcaoCompra() { return acaoCompra; }
    public void setAcaoCompra(Double acaoCompra) { this.acaoCompra = acaoCompra; }

    public Double getAcaoTaxaCompra() { return acaoTaxaCompra; }
    public void setAcaoTaxaCompra(Double acaoTaxaCompra) { this.acaoTaxaCompra = acaoTaxaCompra; }

    public Double getAcaoVenda() { return acaoVenda; }
    public void setAcaoVenda(Double acaoVenda) { this.acaoVenda = acaoVenda; }

    public Double getAcaoTaxas() { return acaoTaxas; }
    public void setAcaoTaxas(Double acaoTaxas) { this.acaoTaxas = acaoTaxas; }

    public Integer getAcaoQtd() { return acaoQtd; }
    public void setAcaoQtd(Integer acaoQtd) { this.acaoQtd = acaoQtd; }

    public Integer getAcaoQtdAtual() { return acaoQtdAtual; }
    public void setAcaoQtdAtual(Integer acaoQtdAtual) { this.acaoQtdAtual = acaoQtdAtual; }

    public Integer getEtfQuantidade() { return etfQuantidade; }
    public void setEtfQuantidade(Integer etfQuantidade) { this.etfQuantidade = etfQuantidade; }

    public Double getEtfPrecoCompra() { return etfPrecoCompra; }
    public void setEtfPrecoCompra(Double etfPrecoCompra) { this.etfPrecoCompra = etfPrecoCompra; }

    public Double getEtfPrecoVenda() { return etfPrecoVenda; }
    public void setEtfPrecoVenda(Double etfPrecoVenda) { this.etfPrecoVenda = etfPrecoVenda; }

    public Double getEtfTaxasCompra() { return etfTaxasCompra; }
    public void setEtfTaxasCompra(Double etfTaxasCompra) { this.etfTaxasCompra = etfTaxasCompra; }

    public Double getEtfTaxasVenda() { return etfTaxasVenda; }
    public void setEtfTaxasVenda(Double etfTaxasVenda) { this.etfTaxasVenda = etfTaxasVenda; }

    public String getEtfDataCompra() { return etfDataCompra; }
    public void setEtfDataCompra(String etfDataCompra) { this.etfDataCompra = etfDataCompra; }

    public String getEtfDataVenda() { return etfDataVenda; }
    public void setEtfDataVenda(String etfDataVenda) { this.etfDataVenda = etfDataVenda; }

    public Double getEtfIrRetido() { return etfIrRetido; }
    public void setEtfIrRetido(Double etfIrRetido) { this.etfIrRetido = etfIrRetido; }

    public Double getEtfAliquota() { return etfAliquota; }
    public void setEtfAliquota(Double etfAliquota) { this.etfAliquota = etfAliquota; }

    public Double getPremios() { return premios; }
    public void setPremios(Double premios) { this.premios = premios; }

    public Double getIr() { return ir; }
    public void setIr(Double ir) { this.ir = ir; }

    public Double getIrrf() { return irrf; }
    public void setIrrf(Double irrf) { this.irrf = irrf; }

    public Double getLucro() { return lucro; }
    public void setLucro(Double lucro) { this.lucro = lucro; }

    public Boolean getDayTrade() { return dayTrade; }
    public void setDayTrade(Boolean dayTrade) { this.dayTrade = dayTrade; }

    public Double getTaxaCambio() { return taxaCambio; }
    public void setTaxaCambio(Double taxaCambio) { this.taxaCambio = taxaCambio; }

    public String getDataCambio() { return dataCambio; }
    public void setDataCambio(String dataCambio) { this.dataCambio = dataCambio; }

    public Double getPremiosBrl() { return premiosBrl; }
    public void setPremiosBrl(Double premiosBrl) { this.premiosBrl = premiosBrl; }

    public Double getLucroBrl() { return lucroBrl; }
    public void setLucroBrl(Double lucroBrl) { this.lucroBrl = lucroBrl; }

    public String getDarfStatus() { return darfStatus; }
    public void setDarfStatus(String darfStatus) { this.darfStatus = darfStatus; }

    public String getDarfVencimento() { return darfVencimento; }
    public void setDarfVencimento(String darfVencimento) { this.darfVencimento = darfVencimento; }

    public String getDarfPagoEm() { return darfPagoEm; }
    public void setDarfPagoEm(String darfPagoEm) { this.darfPagoEm = darfPagoEm; }

    public String getDarfComprovanteNome() { return darfComprovanteNome; }
    public void setDarfComprovanteNome(String darfComprovanteNome) { this.darfComprovanteNome = darfComprovanteNome; }

    public String getDarfComprovanteTipo() { return darfComprovanteTipo; }
    public void setDarfComprovanteTipo(String darfComprovanteTipo) { this.darfComprovanteTipo = darfComprovanteTipo; }

    public String getDarfComprovanteDados() { return darfComprovanteDados; }
    public void setDarfComprovanteDados(String darfComprovanteDados) { this.darfComprovanteDados = darfComprovanteDados; }
}
