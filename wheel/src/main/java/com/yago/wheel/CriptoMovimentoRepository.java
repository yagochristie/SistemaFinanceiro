package com.yago.wheel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CriptoMovimentoRepository extends JpaRepository<CriptoMovimento, Long> {
    List<CriptoMovimento> findByUsuarioIdOrderByDataDescIdDesc(Long usuarioId);
    List<CriptoMovimento> findByUsuarioIdAndSimboloOrderByDataDescIdDesc(Long usuarioId, String simbolo);
    Optional<CriptoMovimento> findByUsuarioIdAndReferencia(Long usuarioId, String referencia);
}
