package com.yago.wheel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final String HASH_PREFIX = "pbkdf2";
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_BYTES = 32;

    private final UsuarioRepository repo;
    private final AcessoUsuario acesso;
    private final SecureRandom secureRandom = new SecureRandom();

    public UsuarioController(UsuarioRepository repo, AcessoUsuario acesso) {
        this.repo = repo;
        this.acesso = acesso;
    }

    @PostMapping("/cadastrar")
    public UsuarioResponse cadastrar(@RequestBody UsuarioRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login e senha sao obrigatorios");
        }
        String login = limparLogin(req.login());
        String senha = req.senha() == null ? "" : req.senha();
        if (login.isBlank() || senha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login e senha sao obrigatorios");
        }
        if (login.length() > 255 || senha.length() > 1024 || (req.nome() != null && req.nome().trim().length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, login ou senha muito longos");
        }
        if (repo.existsByLogin(login)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login ja existe");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(req.nome() == null || req.nome().isBlank() ? login : req.nome().trim());
        usuario.setLogin(login);
        usuario.setSenhaHash(hashSenhaNova(login, senha));
        try {
            return resposta(repo.save(usuario));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login ja existe");
        }
    }

    @PostMapping("/login")
    public UsuarioResponse login(@RequestBody UsuarioRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login e senha sao obrigatorios");
        }
        String login = limparLogin(req.login());
        Usuario usuario = repo.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login invalido"));
        if (!senhaConfere(usuario.getSenhaHash(), login, req.senha() == null ? "" : req.senha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha invalida");
        }
        if (!usuario.getSenhaHash().startsWith(HASH_PREFIX + "$")) {
            usuario.setSenhaHash(hashSenhaNova(login, req.senha() == null ? "" : req.senha()));
            repo.save(usuario);
        }
        return resposta(usuario);
    }

    private String limparLogin(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    private boolean senhaConfere(String hashSalvo, String login, String senha) {
        if (hashSalvo == null || hashSalvo.isBlank()) {
            return false;
        }
        if (!hashSalvo.startsWith(HASH_PREFIX + "$")) {
            return MessageDigest.isEqual(
                    hashSalvo.getBytes(StandardCharsets.UTF_8),
                    hashSenhaLegada(login, senha).getBytes(StandardCharsets.UTF_8));
        }

        String[] partes = hashSalvo.split("\\$");
        if (partes.length != 4) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] recebido = pbkdf2(login, senha, salt, iteracoes, esperado.length);
            return MessageDigest.isEqual(esperado, recebido);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String hashSenhaNova(String login, String senha) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(login, senha, salt, HASH_ITERATIONS, HASH_BYTES);
        return HASH_PREFIX + "$" + HASH_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private byte[] pbkdf2(String login, String senha, byte[] salt, int iteracoes, int tamanhoBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec((login + ":" + senha).toCharArray(), salt, iteracoes, tamanhoBytes * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hashSenhaLegada(String login, String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((login + ":" + senha).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record UsuarioRequest(String nome, String login, String senha) {}

    @PostMapping("/sair")
    public void sair() { acesso.sair(); }

    private UsuarioResponse resposta(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getLogin(), acesso.criarSessao(usuario.getId()));
    }

    public record UsuarioResponse(Long id, String nome, String login, String token) { }
}
