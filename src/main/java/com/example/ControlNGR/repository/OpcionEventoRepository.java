package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.OpcionEvento;
import java.util.List;

@Repository
public interface OpcionEventoRepository extends JpaRepository<OpcionEvento, Integer> {

    List<OpcionEvento> findByEventoIdOrderByOrdenAsc(Integer eventoId);

    @Query("SELECT o, COUNT(r) FROM OpcionEvento o " +
           "LEFT JOIN RespuestaEvento r ON r.opcion.id = o.id " +
           "WHERE o.evento.id = :eventoId " +
           "GROUP BY o.id ORDER BY o.orden")
    List<Object[]> findOpcionesConConteo(@Param("eventoId") Integer eventoId);

    void deleteByEventoId(Integer eventoId);
}
