package com.yago.wheel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacaoRepository extends JpaRepository<Operacao, Long> {
    List<Operacao> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    long deleteByUsuarioId(Long usuarioId);
}
