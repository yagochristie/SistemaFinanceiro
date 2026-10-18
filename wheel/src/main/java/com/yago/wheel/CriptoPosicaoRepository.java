package com.yago.wheel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CriptoPosicaoRepository extends JpaRepository<CriptoPosicao, Long> {
    List<CriptoPosicao> findByUsuarioIdOrderBySimboloAsc(Long usuarioId);
    Optional<CriptoPosicao> findByUsuarioIdAndSimbolo(Long usuarioId, String simbolo);
}
