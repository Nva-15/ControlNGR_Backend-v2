package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Evento;
import com.example.ControlNGR.entity.Evento.EstadoEvento;
import com.example.ControlNGR.entity.Evento.TipoEvento;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Integer> {

    List<Evento> findByEstado(EstadoEvento estado);

    List<Evento> findByTipoEvento(TipoEvento tipoEvento);

    List<Evento> findByCreadoPorId(Integer creadoPorId);

    @Query("SELECT e FROM Evento e WHERE e.estado = 'ACTIVO' " +
           "AND e.fechaInicio <= :ahora " +
           "AND (e.fechaFin IS NULL OR e.fechaFin >= :ahora) " +
           "ORDER BY e.fechaInicio DESC")
    List<Evento> findEventosActivos(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT e FROM Evento e WHERE e.estado = 'ACTIVO' " +
           "AND e.fechaInicio > :ahora " +
           "ORDER BY e.fechaInicio ASC")
    List<Evento> findProximosEventos(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT e FROM Evento e WHERE e.estado IN ('ACTIVO', 'FINALIZADO') " +
           "ORDER BY e.fechaInicio DESC")
    List<Evento> findEventosPublicos();

    @Query("SELECT e FROM Evento e LEFT JOIN FETCH e.opciones " +
           "WHERE e.id = :id")
    Evento findByIdWithOpciones(@Param("id") Integer id);

    @Query("SELECT e FROM Evento e " +
           "LEFT JOIN FETCH e.opciones " +
           "LEFT JOIN FETCH e.respuestas r " +
           "LEFT JOIN FETCH r.empleado " +
           "WHERE e.id = :id")
    Evento findByIdWithOpcionesAndRespuestas(@Param("id") Integer id);

    @Query("SELECT COUNT(r) FROM RespuestaEvento r WHERE r.evento.id = :eventoId AND r.empleado.activo = true")
    Long countRespuestasByEventoId(@Param("eventoId") Integer eventoId);

    @Query("SELECT e FROM Evento e WHERE e.estado = 'ACTIVO' " +
           "AND e.fechaFin IS NOT NULL " +
           "AND e.fechaFin < :ahora")
    List<Evento> findEventosExpirados(@Param("ahora") LocalDateTime ahora);

    List<Evento> findByEstadoOrderByFechaInicioDesc(EstadoEvento estado);
}
