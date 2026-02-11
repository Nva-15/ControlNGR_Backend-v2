package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.RespuestaEvento;
import com.example.ControlNGR.entity.RespuestaEvento.ConfirmacionAsistencia;
import java.util.List;
import java.util.Optional;

@Repository
public interface RespuestaEventoRepository extends JpaRepository<RespuestaEvento, Integer> {

    List<RespuestaEvento> findByEventoId(Integer eventoId);

    List<RespuestaEvento> findByEmpleadoId(Integer empleadoId);

    Optional<RespuestaEvento> findByEventoIdAndEmpleadoId(Integer eventoId, Integer empleadoId);

    boolean existsByEventoIdAndEmpleadoId(Integer eventoId, Integer empleadoId);

    @Query("SELECT COUNT(r) FROM RespuestaEvento r " +
           "WHERE r.evento.id = :eventoId AND r.respuestaSiNo = true " +
           "AND r.empleado.activo = true")
    Long countRespuestasSi(@Param("eventoId") Integer eventoId);

    @Query("SELECT COUNT(r) FROM RespuestaEvento r " +
           "WHERE r.evento.id = :eventoId AND r.respuestaSiNo = false " +
           "AND r.empleado.activo = true")
    Long countRespuestasNo(@Param("eventoId") Integer eventoId);

    @Query("SELECT COUNT(r) FROM RespuestaEvento r " +
           "WHERE r.evento.id = :eventoId AND r.confirmacionAsistencia = :estado " +
           "AND r.empleado.activo = true")
    Long countConfirmaciones(@Param("eventoId") Integer eventoId,
                             @Param("estado") ConfirmacionAsistencia estado);

    @Query("SELECT r FROM RespuestaEvento r " +
           "JOIN FETCH r.empleado e " +
           "WHERE r.evento.id = :eventoId AND e.activo = true " +
           "ORDER BY r.fechaRespuesta DESC")
    List<RespuestaEvento> findByEventoIdWithEmpleado(@Param("eventoId") Integer eventoId);

    @Query("SELECT r.opcion.id, COUNT(r) FROM RespuestaEvento r " +
           "WHERE r.evento.id = :eventoId AND r.opcion IS NOT NULL " +
           "AND r.empleado.activo = true " +
           "GROUP BY r.opcion.id")
    List<Object[]> countRespuestasPorOpcion(@Param("eventoId") Integer eventoId);
}
