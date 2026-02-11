package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.ComentarioEvento;
import java.util.List;

@Repository
public interface ComentarioEventoRepository extends JpaRepository<ComentarioEvento, Integer> {

    @Query("SELECT c FROM ComentarioEvento c " +
           "JOIN FETCH c.empleado " +
           "WHERE c.evento.id = :eventoId " +
           "ORDER BY c.fechaComentario DESC")
    List<ComentarioEvento> findByEventoIdWithEmpleado(@Param("eventoId") Integer eventoId);

    Long countByEventoId(Integer eventoId);

    List<ComentarioEvento> findByEventoIdOrderByFechaComentarioDesc(Integer eventoId);
}
