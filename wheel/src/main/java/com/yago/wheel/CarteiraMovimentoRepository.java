package com.yago.wheel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarteiraMovimentoRepository extends JpaRepository<CarteiraMovimento, Long> {
    List<CarteiraMovimento> findByUsuarioIdOrderByDataDescIdDesc(Long usuarioId);
    List<CarteiraMovimento> findByUsuarioIdAndMercadoOrderByDataDescIdDesc(Long usuarioId, String mercado);
    List<CarteiraMovimento> findByUsuarioIdAndMercadoIsNullOrderByDataDescIdDesc(Long usuarioId);
    Optional<CarteiraMovimento> findByUsuarioIdAndReferencia(Long usuarioId, String referencia);
}
