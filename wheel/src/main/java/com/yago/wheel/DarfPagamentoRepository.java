package com.yago.wheel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DarfPagamentoRepository extends JpaRepository<DarfPagamento, Long> {
    List<DarfPagamento> findByUsuarioIdOrderByCompetenciaAscIdAsc(Long usuarioId);
    Optional<DarfPagamento> findByUsuarioIdAndReferencia(Long usuarioId, String referencia);
}
