package com.yago.wheel;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validação comum aos comprovantes antigos por operação e aos pagamentos mensais. */
final class ComprovanteDarf {
    private static final Pattern DATA_URL = Pattern.compile("^data:([^;,]+);base64,([A-Za-z0-9+/=]+)$");
    private static final Set<String> TIPOS = Set.of("application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp");

    private ComprovanteDarf() { }

    static String validar(String dados) {
        if (dados == null || dados.length() > 5_700_000) throw invalido();
        Matcher matcher = DATA_URL.matcher(dados);
        if (!matcher.matches()) throw invalido();
        String tipo = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw invalido();
        try {
            byte[] bytes = Base64.getDecoder().decode(matcher.group(2));
            if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024) throw invalido();
        } catch (IllegalArgumentException e) {
            throw invalido();
        }
        return tipo;
    }

    private static ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comprovante deve ser PDF ou imagem, com ate 4 MB");
    }
}
