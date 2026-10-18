package com.yago.wheel;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ValidacaoCripto {
    private ValidacaoCripto() { }

    static String simbolo(String entrada) {
        String valor = Objects.toString(entrada, "").trim().toUpperCase(Locale.ROOT);
        if (!valor.matches("[A-Z0-9]{2,16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simbolo de cripto invalido");
        }
        return valor;
    }

    static boolean decimalInvalido(BigDecimal valor, int inteiros, int decimais) {
        return valor == null || valor.signum() <= 0
                || valor.stripTrailingZeros().scale() > decimais
                || valor.precision() - valor.scale() > inteiros;
    }
}
