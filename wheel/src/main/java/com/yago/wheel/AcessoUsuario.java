package com.yago.wheel;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Sessões locais revogáveis; o identificador do usuário nunca autentica uma requisição. */
@Service
public class AcessoUsuario {
    private static final Duration VALIDADE = Duration.ofHours(12);
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final HttpServletRequest request;
    private final UsuarioRepository usuarios;

    public AcessoUsuario(HttpServletRequest request, UsuarioRepository usuarios) {
        this.request = request;
        this.usuarios = usuarios;
    }

    public String criarSessao(Long usuarioId) {
        Instant agora = Instant.now();
        sessoes.entrySet().removeIf(entry -> !entry.getValue().expiraEm().isAfter(agora));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.put(token, new Sessao(usuarioId, agora.plus(VALIDADE)));
        return token;
    }

    public void exigir(Long usuarioId) {
        Sessao sessao = sessoes.get(tokenAtual());
        if (sessao == null || !sessao.expiraEm().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre novamente para continuar");
        }
        if (!Objects.equals(sessao.usuarioId(), usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registro pertence a outro usuario");
        }
    }

    public void sair() {
        sessoes.remove(tokenAtual());
    }

    // Chamado dentro da transação de escrita para serializar a deduplicação por conta.
    public void bloquearGravacao(Long usuarioId) {
        exigir(usuarioId);
        usuarios.bloquearPorId(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario invalido"));
    }

    private String tokenAtual() {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
    }

    private record Sessao(Long usuarioId, Instant expiraEm) { }
}
