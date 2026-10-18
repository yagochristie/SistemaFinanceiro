package com.yago.wheel;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/darf-pagamentos")
public class DarfPagamentoController {
    private final DarfPagamentoRepository pagamentos;
    private final UsuarioRepository usuarios;
    private final AcessoUsuario acesso;
    private final OperacaoRepository operacoes;

    public DarfPagamentoController(DarfPagamentoRepository pagamentos, UsuarioRepository usuarios, OperacaoRepository operacoes, AcessoUsuario acesso) {
        this.pagamentos = pagamentos;
        this.usuarios = usuarios;
        this.acesso = acesso;
        this.operacoes = operacoes;
    }

    @GetMapping
    public List<DarfPagamento> listar(@RequestParam Long usuarioId) {
        validarUsuario(usuarioId);
        return pagamentos.findByUsuarioIdOrderByCompetenciaAscIdAsc(usuarioId);
    }

    @PostMapping
    @Transactional
    public DarfPagamento salvar(@RequestBody DarfPagamento pagamento) {
        if (pagamento == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagamento obrigatorio");
        acesso.bloquearGravacao(pagamento.usuarioId);
        validarUsuario(pagamento.usuarioId);
        try {
            if (pagamento.competencia == null || !pagamento.competencia.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
            YearMonth.parse(pagamento.competencia);
            LocalDate.parse(pagamento.dataPagamento);
            if (pagamento.valor == null || pagamento.valor.signum() <= 0 || pagamento.valor.precision() - pagamento.valor.scale() > 17) throw new IllegalArgumentException();
            pagamento.valor = pagamento.valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Competencia, data ou valor do pagamento invalido");
        }
        if (pagamento.operacaoComprovanteId != null) operacaoComprovante(pagamento);
        if (pagamento.comprovanteNome != null && pagamento.comprovanteNome.length() > 180) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do comprovante muito longo");
        if (pagamento.comprovanteDados != null) {
            pagamento.comprovanteTipo = ComprovanteDarf.validar(pagamento.comprovanteDados);
        }
        if (pagamento.referencia == null || pagamento.referencia.isBlank()) pagamento.referencia = UUID.randomUUID().toString();
        if (pagamento.referencia.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referencia invalida");
        DarfPagamento existente = pagamentos.findByUsuarioIdAndReferencia(pagamento.usuarioId, pagamento.referencia).orElse(null);
        if (existente != null) {
            if (Objects.equals(existente.competencia, pagamento.competencia) && existente.valor.compareTo(pagamento.valor) == 0
                    && Objects.equals(existente.dataPagamento, pagamento.dataPagamento)) return existente;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referencia de pagamento ja utilizada");
        }
        pagamento.id = null;
        return pagamentos.save(pagamento);
    }

    @GetMapping("/{id}/comprovante")
    public OperacaoController.DarfComprovanteResponse comprovante(@PathVariable Long id, @RequestParam Long usuarioId) {
        DarfPagamento pagamento = buscar(id, usuarioId);
        if (pagamento.comprovanteDados != null) return new OperacaoController.DarfComprovanteResponse(
                pagamento.comprovanteNome, pagamento.comprovanteTipo, pagamento.comprovanteDados);
        if (pagamento.operacaoComprovanteId != null) {
            Operacao operacao = operacaoComprovante(pagamento);
            if (operacao.getDarfComprovanteDados() != null) return new OperacaoController.DarfComprovanteResponse(
                    operacao.getDarfComprovanteNome(), operacao.getDarfComprovanteTipo(), operacao.getDarfComprovanteDados());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante nao encontrado");
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id, @RequestParam Long usuarioId) {
        pagamentos.delete(buscar(id, usuarioId));
    }

    private DarfPagamento buscar(Long id, Long usuarioId) {
        validarUsuario(usuarioId);
        return pagamentos.findById(id).filter(p -> Objects.equals(p.usuarioId, usuarioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento nao encontrado"));
    }

    private Operacao operacaoComprovante(DarfPagamento pagamento) {
        return operacoes.findById(pagamento.operacaoComprovanteId).filter(op -> Objects.equals(op.getUsuarioId(), pagamento.usuarioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comprovante nao pertence a esta conta"));
    }

    private void validarUsuario(Long usuarioId) {
        acesso.exigir(usuarioId);
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
    }
}
