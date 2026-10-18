package com.yago.wheel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
@RequestMapping("/operacoes")
public class OperacaoController {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR_DATE = new DateTimeFormatterBuilder()
            .parseStrict()
            .appendPattern("dd/MM/uuuu")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final OperacaoRepository repo;
    private final UsuarioRepository usuarios;
    private final AcessoUsuario acesso;

    public OperacaoController(OperacaoRepository repo, UsuarioRepository usuarios, AcessoUsuario acesso) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @GetMapping
    public List<Operacao> listar(@RequestParam(required = false) Long usuarioId) {
        if (usuarioId != null) {
            acesso.exigir(usuarioId);
            return repo.findByUsuarioIdOrderByIdDesc(usuarioId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usuarioId e obrigatorio");
    }

    @GetMapping("/{id}")
    public Operacao buscarPorId(@PathVariable Long id) {
        return buscarOperacao(id);
    }

    @GetMapping("/{id}/comprovante")
    public DarfComprovanteResponse buscarComprovante(@PathVariable Long id) {
        Operacao op = buscarOperacao(id);
        if (!textoPresente(op.getDarfComprovanteDados())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante nao encontrado");
        }
        return new DarfComprovanteResponse(
                op.getDarfComprovanteNome(),
                op.getDarfComprovanteTipo(),
                op.getDarfComprovanteDados());
    }

    @PostMapping
    @Transactional
    public Operacao salvar(@RequestBody Operacao op) {
        acesso.bloquearGravacao(op.getUsuarioId());
        validarOperacao(op);
        if (op.getId() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a edicao para alterar uma operacao");
        validarDuplicidade(op, null);
        return repo.save(op);
    }

    @PostMapping("/agrupar")
    @Transactional
    public Operacao agrupar(@RequestBody AgrupamentoRequest pedido) {
        if (pedido == null || pedido.usuarioId() == null || pedido.operacaoAgrupada() == null
                || pedido.operacaoIds() == null || pedido.operacaoIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do agrupamento invalidos");
        }
        acesso.bloquearGravacao(pedido.usuarioId());
        if (!usuarios.existsById(pedido.usuarioId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }

        Set<Long> ids = new HashSet<>(pedido.operacaoIds());
        if (ids.contains(null) || ids.size() != pedido.operacaoIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operacoes do agrupamento invalidas");
        }
        List<Operacao> anteriores = repo.findAllById(ids);
        if (anteriores.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operacao para agrupar nao encontrada");
        }
        for (Operacao anterior : anteriores) {
            if (!Objects.equals(anterior.getUsuarioId(), pedido.usuarioId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operacao pertence a outro usuario");
            }
        }

        Operacao agrupada = pedido.operacaoAgrupada();
        agrupada.setId(null);
        agrupada.setUsuarioId(pedido.usuarioId());
        validarOperacao(agrupada);
        for (Operacao anterior : anteriores) {
            if (!contratoAgrupavel(anterior).equals(contratoAgrupavel(agrupada))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrupe apenas opcoes abertas do mesmo contrato");
            }
        }
        validarDuplicidadeExceto(agrupada, ids);

        Operacao salva = repo.save(agrupada);
        repo.deleteAllByIdInBatch(ids);
        return salva;
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        buscarOperacao(id);
        repo.deleteById(id);
    }

    @Transactional
    @DeleteMapping
    public long deletarPorUsuario(@RequestParam Long usuarioId) {
        acesso.exigir(usuarioId);
        return repo.deleteByUsuarioId(usuarioId);
    }

    @PutMapping("/{id}")
    @Transactional
    public Operacao editar(@PathVariable Long id, @RequestBody Operacao nova) {
        Operacao op = buscarOperacao(id);
        acesso.bloquearGravacao(op.getUsuarioId());
        validarOperacao(nova);
        if (!Objects.equals(op.getUsuarioId(), nova.getUsuarioId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nao e possivel transferir operacoes entre contas");
        validarDuplicidade(nova, id);

        // Identificação
        op.setMercado(nova.getMercado());
        op.setMoeda(nova.getMoeda());
        op.setTicker(nova.getTicker());
        op.setData(nova.getData());
        op.setFase(nova.getFase());
        op.setStatusOperacao(nova.getStatusOperacao());
        op.setUsuarioId(nova.getUsuarioId());
        op.setTipoAtivo(nova.getTipoAtivo());

        // PUT
        op.setPutExercida(nova.getPutExercida());
        op.setPutStrike(nova.getPutStrike());
        op.setPutPremio(nova.getPutPremio());
        op.setPutQtd(nova.getPutQtd());
        op.setPutTaxas(nova.getPutTaxas());
        op.setPutFechamento(nova.getPutFechamento());
        op.setPutTaxasFechamento(nova.getPutTaxasFechamento());
        op.setPutDataFechamento(nova.getPutDataFechamento());
        op.setPutVencimento(nova.getPutVencimento());

        // CALL
        op.setCallExercida(nova.getCallExercida());
        op.setCallStrike(nova.getCallStrike());
        op.setCallPremio(nova.getCallPremio());
        op.setCallTaxas(nova.getCallTaxas());
        op.setCallFechamento(nova.getCallFechamento());
        op.setCallTaxasFechamento(nova.getCallTaxasFechamento());
        op.setCallDataFechamento(nova.getCallDataFechamento());
        op.setCallVencimento(nova.getCallVencimento());

        // Ação
        op.setAcaoCompra(nova.getAcaoCompra());
        op.setAcaoTaxaCompra(nova.getAcaoTaxaCompra());
        op.setAcaoDataCompra(nova.getAcaoDataCompra());
        op.setAcaoVenda(nova.getAcaoVenda());
        op.setAcaoDataVenda(nova.getAcaoDataVenda());
        op.setAcaoDataLiquidacao(nova.getAcaoDataLiquidacao());
        op.setAcaoTaxas(nova.getAcaoTaxas());
        op.setAcaoQtd(nova.getAcaoQtd());
        op.setAcaoQtdAtual(nova.getAcaoQtdAtual());

        // ETF de renda fixa
        op.setEtfQuantidade(nova.getEtfQuantidade());
        op.setEtfPrecoCompra(nova.getEtfPrecoCompra());
        op.setEtfPrecoVenda(nova.getEtfPrecoVenda());
        op.setEtfTaxasCompra(nova.getEtfTaxasCompra());
        op.setEtfTaxasVenda(nova.getEtfTaxasVenda());
        op.setEtfDataCompra(nova.getEtfDataCompra());
        op.setEtfDataVenda(nova.getEtfDataVenda());
        op.setEtfIrRetido(nova.getEtfIrRetido());
        op.setEtfAliquota(nova.getEtfAliquota());

        // Resultado
        op.setPremios(nova.getPremios());
        op.setIr(nova.getIr());
        op.setIrrf(nova.getIrrf());
        op.setIrrfData(nova.getIrrfData());
        op.setLucro(nova.getLucro());
        op.setDayTrade(nova.getDayTrade());
        op.setTaxaCambio(nova.getTaxaCambio());
        op.setDataCambio(nova.getDataCambio());
        op.setPremiosBrl(nova.getPremiosBrl());
        op.setLucroBrl(nova.getLucroBrl());
        op.setDarfStatus(nova.getDarfStatus());
        op.setDarfVencimento(nova.getDarfVencimento());
        op.setDarfPagoEm(nova.getDarfPagoEm());
        atualizarComprovanteDarf(op, nova);

        return repo.save(op);
    }

    private Operacao buscarOperacao(Long id) {
        Operacao op = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operacao nao encontrada"));
        acesso.exigir(op.getUsuarioId());
        return op;
    }

    private void validarOperacao(Operacao op) {
        if (op == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operacao obrigatoria");
        }
        op.setAcaoDataVenda(op.getAcaoDataVenda() == null || op.getAcaoDataVenda().isBlank() ? null : op.getAcaoDataVenda().trim());
        op.setAcaoDataCompra(op.getAcaoDataCompra() == null || op.getAcaoDataCompra().isBlank() ? null : op.getAcaoDataCompra().trim());
        op.setIrrfData(op.getIrrfData() == null || op.getIrrfData().isBlank() ? null : op.getIrrfData().trim());
        op.setAcaoDataLiquidacao(op.getAcaoDataLiquidacao() == null || op.getAcaoDataLiquidacao().isBlank() ? null : op.getAcaoDataLiquidacao().trim());
        op.setPutVencimento(op.getPutVencimento() == null || op.getPutVencimento().isBlank() ? null : op.getPutVencimento().trim());
        op.setCallVencimento(op.getCallVencimento() == null || op.getCallVencimento().isBlank() ? null : op.getCallVencimento().trim());
        op.setPutDataFechamento(op.getPutDataFechamento() == null || op.getPutDataFechamento().isBlank() ? null : op.getPutDataFechamento().trim());
        op.setCallDataFechamento(op.getCallDataFechamento() == null || op.getCallDataFechamento().isBlank() ? null : op.getCallDataFechamento().trim());
        op.setDarfVencimento(op.getDarfVencimento() == null || op.getDarfVencimento().isBlank() ? null : op.getDarfVencimento().trim());
        op.setDarfPagoEm(op.getDarfPagoEm() == null || op.getDarfPagoEm().isBlank() ? null : op.getDarfPagoEm().trim());
        op.setDataCambio(op.getDataCambio() == null || op.getDataCambio().isBlank() ? null : op.getDataCambio().trim());
        op.setEtfDataCompra(op.getEtfDataCompra() == null || op.getEtfDataCompra().isBlank() ? null : op.getEtfDataCompra().trim());
        op.setEtfDataVenda(op.getEtfDataVenda() == null || op.getEtfDataVenda().isBlank() ? null : op.getEtfDataVenda().trim());
        validarDataIsoOpcional(op.getAcaoDataVenda(), "Data da venda invalida");
        validarDataIsoOpcional(op.getAcaoDataCompra(), "Data da compra invalida");
        validarDataIsoOpcional(op.getIrrfData(), "Data da retencao invalida");
        validarDataIsoOpcional(op.getAcaoDataLiquidacao(), "Data da liquidacao invalida");
        if (textoPresente(op.getAcaoDataLiquidacao())) {
            String dataNegociacao = textoPresente(op.getAcaoDataVenda())
                    ? op.getAcaoDataVenda() : op.getAcaoDataCompra();
            if (!textoPresente(dataNegociacao)
                    || LocalDate.parse(op.getAcaoDataLiquidacao()).isBefore(LocalDate.parse(dataNegociacao))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liquidacao deve ocorrer na data da compra ou venda correspondente, ou depois");
            }
        }
        acesso.exigir(op.getUsuarioId());
        if (op.getUsuarioId() == null || !usuarios.existsById(op.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }
        if (op.getTicker() == null || op.getTicker().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker obrigatorio");
        }
        op.setTicker(normalizarTickerBR(op.getTicker()));

        String mercado = op.getMercado() == null || op.getMercado().isBlank()
                ? "BR"
                : op.getMercado().trim().toUpperCase(Locale.ROOT);
        if (!mercado.equals("BR") && !mercado.equals("US")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mercado invalido");
        }
        op.setMercado(mercado);

        String tipoAtivo = normalizarTipoAtivo(op.getTipoAtivo());
        op.setTipoAtivo(tipoAtivo);
        if (tipoAtivo.equals("ETF_RF")) {
            op.setMercado("BR");
            op.setMoeda("BRL");
            boolean vendaEtf = operacaoEtfVenda(op);
            op.setFase(vendaEtf ? "Venda ETF renda fixa" : "Compra ETF renda fixa");
            op.setStatusOperacao(vendaEtf ? "Encerrada" : "Em carteira");
            op.setDarfStatus("Sem DARF");
            op.setDarfVencimento(null);
            op.setDayTrade(false);
        }
        if (tipoAtivo.equals("ACAO")) {
            op.setMoeda("US".equals(mercado) ? "USD" : "BRL");
            boolean vendaAcao = numeroPositivo(op.getAcaoVenda());
            op.setFase(vendaAcao ? "Venda de ações" : "Compra de ações");
            op.setStatusOperacao(vendaAcao ? "Encerrada" : "Em carteira");
            op.setDarfStatus(vendaAcao && !"US".equals(mercado) ? normalizarDarfAcao(op.getDarfStatus()) : "Sem DARF");
            op.setDarfVencimento(vendaAcao ? op.getDarfVencimento() : null);
            op.setPremios(0.0);
            op.setPremiosBrl(0.0);
            op.setDayTrade(Boolean.TRUE.equals(op.getDayTrade()));
        }

        if (!op.getTicker().matches("[A-Z0-9.]{2,20}") || op.getTicker().equals("—") || op.getTicker().equals("-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker invalido");
        }

        validarDataOperacao(op.getData(), "Data da operacao invalida");
        validarDataIsoOpcional(op.getPutVencimento(), "Vencimento da PUT invalido");
        validarDataIsoOpcional(op.getCallVencimento(), "Vencimento da CALL invalido");
        validarDataIsoOpcional(op.getPutDataFechamento(), "Data de fechamento da PUT invalida");
        validarDataIsoOpcional(op.getCallDataFechamento(), "Data de fechamento da CALL invalida");
        validarDataIsoOpcional(op.getDarfVencimento(), "Vencimento do DARF invalido");
        validarDataIsoOpcional(op.getDarfPagoEm(), "Data de pagamento do DARF invalida");
        validarDataIsoOpcional(op.getDataCambio(), "Data do cambio invalida");
        validarDataIsoOpcional(op.getEtfDataCompra(), "Data de compra do ETF invalida");
        validarDataIsoOpcional(op.getEtfDataVenda(), "Data de venda do ETF invalida");
        validarDarfStatus(op);
        validarComprovanteDarf(op);
        validarVencimentoDepoisDaOperacao(op.getData(), op.getPutVencimento(), "Vencimento da PUT anterior a operacao");
        validarVencimentoDepoisDaOperacao(op.getData(), op.getCallVencimento(), "Vencimento da CALL anterior a operacao");

        if ((op.getLucro() != null && !Double.isFinite(op.getLucro()))
                || (op.getLucroBrl() != null && !Double.isFinite(op.getLucroBrl()))
                || (op.getPremiosBrl() != null && !Double.isFinite(op.getPremiosBrl()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultado financeiro invalido");
        }
        validarNumeroNaoNegativo(op.getPutStrike(), "Strike da PUT invalido");
        validarNumeroNaoNegativo(op.getPutPremio(), "Premio da PUT invalido");
        validarNumeroNaoNegativo(op.getPutTaxas(), "Taxas da PUT invalidas");
        validarNumeroNaoNegativo(op.getPutFechamento(), "Fechamento da PUT invalido");
        validarNumeroNaoNegativo(op.getPutTaxasFechamento(), "Taxas de fechamento da PUT invalidas");
        validarNumeroNaoNegativo(op.getCallStrike(), "Strike da CALL invalido");
        validarNumeroNaoNegativo(op.getCallPremio(), "Premio da CALL invalido");
        validarNumeroNaoNegativo(op.getCallTaxas(), "Taxas da CALL invalidas");
        validarNumeroNaoNegativo(op.getCallFechamento(), "Fechamento da CALL invalido");
        validarNumeroNaoNegativo(op.getCallTaxasFechamento(), "Taxas de fechamento da CALL invalidas");
        validarNumeroNaoNegativo(op.getAcaoCompra(), "Preco de compra da acao invalido");
        validarNumeroNaoNegativo(op.getAcaoTaxaCompra(), "Taxa de compra da acao invalida");
        validarNumeroNaoNegativo(op.getAcaoVenda(), "Preco de venda da acao invalido");
        validarNumeroNaoNegativo(op.getAcaoTaxas(), "Taxas da acao invalidas");
        validarNumeroNaoNegativo(op.getPremios(), "Premios invalidos");
        validarNumeroNaoNegativo(op.getIr(), "IR invalido");
        validarNumeroNaoNegativo(op.getIrrf(), "IRRF invalido");
        validarNumeroNaoNegativo(op.getTaxaCambio(), "Taxa de cambio invalida");
        validarNumeroNaoNegativo(op.getEtfPrecoCompra(), "Preco de compra do ETF invalido");
        validarNumeroNaoNegativo(op.getEtfPrecoVenda(), "Preco de venda do ETF invalido");
        validarNumeroNaoNegativo(op.getEtfTaxasCompra(), "Taxas de compra do ETF invalidas");
        validarNumeroNaoNegativo(op.getEtfTaxasVenda(), "Taxas de venda do ETF invalidas");
        validarNumeroNaoNegativo(op.getEtfIrRetido(), "IR retido do ETF invalido");
        validarNumeroNaoNegativo(op.getEtfAliquota(), "Aliquota do ETF invalida");
        validarInteiroNaoNegativo(op.getPutQtd(), "Quantidade da PUT invalida");
        validarInteiroNaoNegativo(op.getAcaoQtd(), "Quantidade de acoes invalida");
        validarInteiroNaoNegativo(op.getAcaoQtdAtual(), "Quantidade atual de acoes invalida");
        validarInteiroNaoNegativo(op.getEtfQuantidade(), "Quantidade do ETF invalida");

        if (tipoAtivo.equals("ETF_RF")) {
            validarEtfRendaFixa(op);
            return;
        }
        if (tipoAtivo.equals("ACAO")) {
            validarAcaoCarteira(op);
            return;
        }

        if (numeroPositivo(op.getPutPremio())) {
            if (!inteiroPositivo(op.getPutQtd())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade da PUT obrigatoria");
            }
            if (op.getPutVencimento() == null || op.getPutVencimento().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vencimento da PUT obrigatorio");
            }
            if (situacaoFechada(op.getPutExercida()) && !numeroPositivo(op.getPutFechamento())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fechamento da PUT obrigatorio");
            }
        }

        if (numeroPositivo(op.getCallPremio())) {
            int qtdCall = primeiroInteiroPositivo(op.getAcaoQtd(), op.getAcaoQtdAtual(), op.getPutQtd());
            if (qtdCall <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade da CALL obrigatoria");
            }
            if (op.getCallVencimento() == null || op.getCallVencimento().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vencimento da CALL obrigatorio");
            }
            if (situacaoFechada(op.getCallExercida()) && !numeroPositivo(op.getCallFechamento())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fechamento da CALL obrigatorio");
            }
        }
    }

    private void atualizarComprovanteDarf(Operacao op, Operacao nova) {
        if (textoPresente(nova.getDarfComprovanteDados())) {
            op.setDarfComprovanteNome(nova.getDarfComprovanteNome());
            op.setDarfComprovanteTipo(nova.getDarfComprovanteTipo());
            op.setDarfComprovanteDados(nova.getDarfComprovanteDados());
            return;
        }

        if (!textoPresente(op.getDarfComprovanteDados())) {
            return;
        }
        if (textoPresente(nova.getDarfComprovanteNome())) {
            op.setDarfComprovanteNome(nova.getDarfComprovanteNome());
        }
        if (textoPresente(nova.getDarfComprovanteTipo())) {
            op.setDarfComprovanteTipo(nova.getDarfComprovanteTipo());
        }
    }

    private void validarDarfStatus(Operacao op) {
        if (!textoPresente(op.getDarfStatus())) {
            return;
        }
        String status = op.getDarfStatus().trim();
        if (status.equals("Aguardando apuracao")) {
            status = "Aguardando apuração";
        }
        if (!status.equals("Pendente")
                && !status.equals("Pago")
                && !status.equals("Sem DARF")
                && !status.equals("Aguardando apuração")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status do DARF invalido");
        }
        op.setDarfStatus(status);
    }

    private void validarComprovanteDarf(Operacao op) {
        op.setDarfComprovanteNome(normalizarTextoOpcional(
                op.getDarfComprovanteNome(), 180, "Nome do comprovante muito longo"));
        op.setDarfComprovanteTipo(normalizarTextoOpcional(
                op.getDarfComprovanteTipo(), 120, "Tipo do comprovante muito longo"));

        String dados = op.getDarfComprovanteDados();
        if (!textoPresente(dados)) {
            op.setDarfComprovanteDados(null);
            return;
        }
        dados = dados.trim();
        String mime = ComprovanteDarf.validar(dados);
        op.setDarfComprovanteDados(dados);
        if (!textoPresente(op.getDarfComprovanteNome())) {
            op.setDarfComprovanteNome("comprovante-darf");
        }
        op.setDarfComprovanteTipo(mime);
    }

    private String normalizarTextoOpcional(String valor, int maxLength, String mensagem) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        return limpo.isBlank() ? null : limpo;
    }

    private boolean textoPresente(String valor) {
        return valor != null && !valor.isBlank();
    }

    private void validarDuplicidade(Operacao op, Long idAtual) {
        List<Operacao> existentes = repo.findByUsuarioIdOrderByIdDesc(op.getUsuarioId());
        for (Operacao existente : existentes) {
            if (idAtual != null && Objects.equals(existente.getId(), idAtual)) {
                continue;
            }
            if (chaveDuplicidade(existente).equals(chaveDuplicidade(op))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Operacao duplicada");
            }
        }
    }

    private void validarDuplicidadeExceto(Operacao op, Set<Long> idsIgnorados) {
        List<Operacao> existentes = repo.findByUsuarioIdOrderByIdDesc(op.getUsuarioId());
        for (Operacao existente : existentes) {
            if (idsIgnorados.contains(existente.getId())) {
                continue;
            }
            if (chaveDuplicidade(existente).equals(chaveDuplicidade(op))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Operacao duplicada");
            }
        }
    }

    private String chaveDuplicidade(Operacao op) {
        return normalizar(op.getUsuarioId()) + "|"
                + normalizar(op.getMercado()) + "|"
                + normalizarTipoAtivo(op.getTipoAtivo()) + "|"
                + normalizar(op.getTicker()) + "|"
                + normalizarDataOperacao(op.getData()) + "|"
                + normalizar(op.getPutVencimento()) + "|"
                + normalizar(op.getCallVencimento()) + "|"
                + normalizarNumero(op.getPutStrike()) + "|"
                + normalizarNumero(op.getPutPremio()) + "|"
                + normalizarInteiro(op.getPutQtd()) + "|"
                + normalizarNumero(op.getCallStrike()) + "|"
                + normalizarNumero(op.getCallPremio()) + "|"
                + normalizarInteiro(primeiroInteiroPositivo(op.getAcaoQtd(), op.getAcaoQtdAtual(), op.getPutQtd())) + "|"
                + normalizar(op.getEtfDataCompra()) + "|"
                + normalizar(op.getEtfDataVenda()) + "|"
                + normalizarNumero(op.getEtfPrecoCompra()) + "|"
                + normalizarNumero(op.getEtfPrecoVenda()) + "|"
                + normalizarInteiro(op.getEtfQuantidade()) + "|"
                + normalizarNumero(op.getAcaoCompra()) + "|"
                + normalizarNumero(op.getAcaoVenda()) + "|"
                + normalizarNumero(op.getAcaoTaxaCompra()) + "|"
                + normalizarNumero(op.getAcaoTaxas());
    }

    private String normalizar(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim().toUpperCase(Locale.ROOT);
    }

    public record AgrupamentoRequest(Long usuarioId, List<Long> operacaoIds, Operacao operacaoAgrupada) {
    }

    private String normalizarTickerBR(String ticker) {
        String normalizado = normalizar(ticker);
        if (normalizado.equals("B3AS3") || normalizado.equals("B3SA")) {
            return "B3SA3";
        }
        return normalizado;
    }

    private String normalizarTipoAtivo(String valor) {
        if (valor == null || valor.isBlank()) {
            return "OPCAO";
        }
        String tipo = valor.trim().toUpperCase(Locale.ROOT);
        if (!tipo.equals("OPCAO") && !tipo.equals("ETF_RF") && !tipo.equals("ACAO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de ativo invalido");
        }
        return tipo;
    }

    private String normalizarDarfAcao(String valor) {
        if (!textoPresente(valor)) {
            return "Aguardando apuração";
        }
        String status = valor.trim();
        if (status.equals("Aguardando apuracao")) {
            return "Aguardando apuração";
        }
        if (status.equals("Pendente") || status.equals("Pago") || status.equals("Sem DARF") || status.equals("Aguardando apuração")) {
            return status;
        }
        return "Aguardando apuração";
    }

    private boolean tickerEtfRendaFixa(String ticker) {
        String normalizado = normalizar(ticker);
        return normalizado.equals("LFTB11") || normalizado.equals("LFTS11");
    }

    private boolean operacaoEtfVenda(Operacao op) {
        return numeroPositivo(op.getEtfPrecoVenda())
                && op.getEtfDataVenda() != null
                && !op.getEtfDataVenda().isBlank();
    }

    private double aliquotaEtfRendaFixa(String ticker) {
        String normalizado = normalizar(ticker);
        return normalizado.equals("LFTS11") ? 0.25 : 0.15;
    }

    private double valor(Double numero) {
        return numero == null ? 0.0 : numero;
    }

    private void validarEtfRendaFixa(Operacao op) {
        if (!tickerEtfRendaFixa(op.getTicker())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ETF de renda fixa deve ser LFTB11 ou LFTS11");
        }
        if (!inteiroPositivo(op.getEtfQuantidade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade do ETF obrigatoria");
        }
        if (!numeroPositivo(op.getEtfPrecoCompra())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preco de compra do ETF obrigatorio");
        }
        if (op.getEtfDataCompra() == null || op.getEtfDataCompra().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de compra do ETF obrigatoria");
        }
        boolean vendaEtf = operacaoEtfVenda(op);
        if ((numeroPositivo(op.getEtfPrecoVenda()) || (op.getEtfDataVenda() != null && !op.getEtfDataVenda().isBlank())) && !vendaEtf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venda do ETF incompleta");
        }
        LocalDate compra = parseData(op.getEtfDataCompra().trim(), ISO_DATE);
        LocalDate venda = vendaEtf ? parseData(op.getEtfDataVenda().trim(), ISO_DATE) : null;
        if (vendaEtf) {
            if (compra != null && venda != null && venda.isBefore(compra)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venda do ETF anterior a compra");
            }
            double aliquota = aliquotaEtfRendaFixa(op.getTicker());
            op.setEtfAliquota(aliquota);
            double custo = op.getEtfQuantidade() * valor(op.getEtfPrecoCompra()) + valor(op.getEtfTaxasCompra());
            double vendaLiquida = op.getEtfQuantidade() * valor(op.getEtfPrecoVenda()) - valor(op.getEtfTaxasVenda());
            double resultado = vendaLiquida - custo;
            double impostoEstimado = resultado > 0 ? resultado * aliquota : 0.0;
            if (op.getEtfIrRetido() == null || (impostoEstimado > 0 && valor(op.getEtfIrRetido()) == 0.0 && valor(op.getIrrf()) == 0.0)) {
                op.setEtfIrRetido(impostoEstimado);
            }
            op.setIr(impostoEstimado);
            op.setIrrf(op.getEtfIrRetido());
            op.setLucro(resultado - valor(op.getEtfIrRetido()));
        } else {
            op.setEtfAliquota(aliquotaEtfRendaFixa(op.getTicker()));
            op.setEtfDataVenda(null);
            op.setEtfPrecoVenda(0.0);
            op.setEtfTaxasVenda(0.0);
            op.setEtfIrRetido(0.0);
            op.setIrrf(0.0);
            op.setIr(0.0);
            op.setLucro(0.0);
        }
        op.setFase(vendaEtf ? "Venda ETF renda fixa" : "Compra ETF renda fixa");
        op.setStatusOperacao(vendaEtf ? "Encerrada" : "Em carteira");
        op.setPremios(0.0);
        op.setPremiosBrl(0.0);
        op.setLucroBrl(op.getLucro());
    }

    private void validarAcaoCarteira(Operacao op) {
        if (!inteiroPositivo(op.getAcaoQtd())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade de acoes obrigatoria");
        }
        if (!numeroPositivo(op.getAcaoCompra())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preco de compra da acao obrigatorio");
        }
        boolean vendaAcao = numeroPositivo(op.getAcaoVenda());
        if (!vendaAcao && !inteiroPositivo(op.getAcaoQtdAtual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade atual de acoes obrigatoria");
        }
        if (vendaAcao) {
            op.setAcaoQtdAtual(0);
            if (!textoPresente(op.getDarfStatus())) {
                op.setDarfStatus("Aguardando apuração");
            }
        } else {
            op.setAcaoVenda(0.0);
            op.setAcaoTaxas(0.0);
            op.setDarfStatus("Sem DARF");
            op.setDarfVencimento(null);
            op.setIr(0.0);
            op.setIrrf(0.0);
            op.setLucro(0.0);
        }
        op.setPutExercida("Carteira");
        op.setPutPremio(0.0);
        op.setPutQtd(0);
        op.setPutTaxas(0.0);
        op.setCallPremio(0.0);
        op.setCallTaxas(0.0);
        op.setFase(vendaAcao ? "Venda de ações" : "Compra de ações");
        op.setStatusOperacao(vendaAcao ? "Encerrada" : "Em carteira");
        op.setPremios(0.0);
        op.setPremiosBrl(0.0);
        op.setLucroBrl("US".equals(op.getMercado())
                ? (numeroPositivo(op.getTaxaCambio()) ? valor(op.getLucro()) * op.getTaxaCambio() : null) : op.getLucro());
    }

    private String contratoAgrupavel(Operacao op) {
        boolean put = numeroPositivo(op.getPutPremio()) && "PENDENTE".equals(normalizar(op.getPutExercida()));
        boolean call = numeroPositivo(op.getCallPremio()) && "PENDENTE".equals(normalizar(op.getCallExercida()));
        if (!"OPCAO".equals(normalizarTipoAtivo(op.getTipoAtivo())) || put == call) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrupamento exige uma opcao aberta por operacao");
        }
        return (normalizar(op.getMercado()).isEmpty() ? "BR" : normalizar(op.getMercado())) + "|" + normalizar(op.getTicker()) + "|"
                + (put ? "PUT|" : "CALL|" + normalizar(op.getPutExercida()) + "|")
                + normalizarNumero(put ? op.getPutStrike() : op.getCallStrike()) + "|"
                + normalizar(put ? op.getPutVencimento() : op.getCallVencimento());
    }

    private String normalizarDataOperacao(String data) {
        LocalDate parsed = parseDataOperacao(data);
        return parsed == null ? normalizar(data) : parsed.toString();
    }

    private String normalizarNumero(Double valor) {
        return valor == null ? "0.000000" : String.format(Locale.ROOT, "%.6f", valor);
    }

    private String normalizarInteiro(Integer valor) {
        return valor == null ? "0" : String.valueOf(valor);
    }

    private void validarDataOperacao(String data, String mensagem) {
        if (data == null || data.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        if (parseDataOperacao(data) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private LocalDate parseDataOperacao(String data) {
        String limpa = data == null ? "" : data.trim();
        return parseData(limpa, ISO_DATE, BR_DATE);
    }

    private void validarDataIsoOpcional(String data, String mensagem) {
        if (data == null || data.isBlank()) {
            return;
        }
        if (parseData(data.trim(), ISO_DATE) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private void validarVencimentoDepoisDaOperacao(String dataOperacao, String vencimento, String mensagem) {
        if (vencimento == null || vencimento.isBlank()) {
            return;
        }
        LocalDate data = parseDataOperacao(dataOperacao);
        LocalDate venc = parseData(vencimento.trim(), ISO_DATE);
        if (data != null && venc != null && venc.isBefore(data)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private LocalDate parseData(String data, DateTimeFormatter... formatos) {
        for (DateTimeFormatter formato : formatos) {
            try {
                return LocalDate.parse(data, formato);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private void validarNumeroNaoNegativo(Double valor, String mensagem) {
        if (valor == null) {
            return;
        }
        if (!Double.isFinite(valor) || valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private void validarInteiroNaoNegativo(Integer valor, String mensagem) {
        if (valor != null && valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private boolean numeroPositivo(Double valor) {
        return valor != null && Double.isFinite(valor) && valor > 0;
    }

    private boolean inteiroPositivo(Integer valor) {
        return valor != null && valor > 0;
    }

    private int primeiroInteiroPositivo(Integer... valores) {
        for (Integer valor : valores) {
            if (valor != null && valor > 0) {
                return valor;
            }
        }
        return 0;
    }

    private boolean situacaoFechada(String valor) {
        if (valor == null) {
            return false;
        }
        String limpa = valor.trim().toLowerCase(Locale.ROOT);
        return limpa.equals("fechada") || limpa.equals("encerrada");
    }

    public record DarfComprovanteResponse(String nome, String tipo, String dados) {}
}
