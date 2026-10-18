package com.yago.wheel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/carteira/movimentos")
public class CarteiraMovimentoController {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR_DATE = new DateTimeFormatterBuilder()
            .parseStrict()
            .appendPattern("dd/MM/uuuu")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final CarteiraMovimentoRepository movimentos;
    private final UsuarioRepository usuarios;
    private final AcessoUsuario acesso;

    public CarteiraMovimentoController(CarteiraMovimentoRepository movimentos, UsuarioRepository usuarios, AcessoUsuario acesso) {
        this.movimentos = movimentos;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @GetMapping
    public List<CarteiraMovimento> listar(@RequestParam Long usuarioId, @RequestParam(required = false) String mercado) {
        acesso.exigir(usuarioId);
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }
        String mercadoNormalizado = normalizarMercado(mercado);
        List<CarteiraMovimento> resultado = new ArrayList<>(
                movimentos.findByUsuarioIdAndMercadoOrderByDataDescIdDesc(usuarioId, mercadoNormalizado));
        if ("BR".equals(mercadoNormalizado)) {
            resultado.addAll(movimentos.findByUsuarioIdAndMercadoIsNullOrderByDataDescIdDesc(usuarioId));
        }
        resultado.sort(Comparator.comparing(CarteiraMovimento::getData, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CarteiraMovimento::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return resultado;
    }

    @PostMapping
    @Transactional
    public CarteiraMovimento salvar(@RequestBody CarteiraMovimento movimento) {
        if (movimento == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimento obrigatorio");
        acesso.bloquearGravacao(movimento.getUsuarioId());
        validar(movimento);
        if (movimento.getId() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimento novo nao pode informar id");
        if (textoPresente(movimento.getReferencia())
                && movimentos.findByUsuarioIdAndReferencia(movimento.getUsuarioId(), movimento.getReferencia()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este provento ja foi registrado na carteira");
        }
        return movimentos.save(movimento);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        CarteiraMovimento movimento = movimentos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimento nao encontrado"));
        acesso.exigir(movimento.getUsuarioId());
        movimentos.deleteById(id);
    }

    private void validar(CarteiraMovimento movimento) {
        if (movimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimento obrigatorio");
        }
        acesso.exigir(movimento.getUsuarioId());
        if (movimento.getUsuarioId() == null || !usuarios.existsById(movimento.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido");
        }
        movimento.setMercado(normalizarMercado(movimento.getMercado()));
        movimento.setData(normalizarData(movimento.getData()));
        movimento.setTipo(normalizarTipo(movimento.getTipo()));
        boolean saldoEmConta = "SALDO_EM_CONTA".equals(movimento.getTipo());
        if (movimento.getValor() == null || !Double.isFinite(movimento.getValor())
                || (saldoEmConta ? movimento.getValor() < 0 : movimento.getValor() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do movimento invalido");
        }
        String descricao = Objects.toString(movimento.getDescricao(), "").trim();
        movimento.setDescricao(descricao.length() > 160 ? descricao.substring(0, 160) : descricao);
        String referencia = Objects.toString(movimento.getReferencia(), "").trim();
        if (referencia.length() > 160) {
            referencia = referencia.substring(0, 160);
        }
        if (!referencia.isEmpty() && !referencia.matches("[A-Za-z0-9:_./-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referencia do movimento invalida");
        }
        movimento.setReferencia(referencia.isEmpty() ? null : referencia);
    }

    private String normalizarData(String data) {
        if (data == null || data.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data do movimento invalida");
        }
        String valor = data.trim();
        try {
            if (valor.contains("/")) {
                return LocalDate.parse(valor, BR_DATE).toString();
            }
            return LocalDate.parse(valor, ISO_DATE).toString();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data do movimento invalida");
        }
    }

    private String normalizarTipo(String tipo) {
        String valor = Objects.toString(tipo, "").trim().toUpperCase(Locale.ROOT);
        if (valor.equals("APORTE") || valor.equals("RETIRADA") || valor.equals("SALDO_EM_CONTA")
                || valor.equals("PROVENTO")) {
            return valor;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de movimento invalido");
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

    private boolean textoPresente(String valor) {
        return valor != null && !valor.isBlank();
    }
}
