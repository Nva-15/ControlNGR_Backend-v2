package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioSemanalDetalleRepository extends JpaRepository<HorarioSemanalDetalle, Integer> {

    // Buscar todos los detalles de una semana
    List<HorarioSemanalDetalle> findByHorarioSemanalId(Integer horarioSemanalId);

    // Buscar detalles de un empleado en un rango de fechas
    List<HorarioSemanalDetalle> findByEmpleadoIdAndFechaBetween(
            Integer empleadoId, LocalDate fechaInicio, LocalDate fechaFin);

    // Buscar detalle específico de empleado en una fecha
    Optional<HorarioSemanalDetalle> findByEmpleadoIdAndFecha(Integer empleadoId, LocalDate fecha);

    // Buscar horario de empleado en fecha específica (query explícito)
    @Query("SELECT d FROM HorarioSemanalDetalle d WHERE d.empleado.id = :empleadoId AND d.fecha = :fecha")
    Optional<HorarioSemanalDetalle> findHorarioEmpleadoEnFecha(
            @Param("empleadoId") Integer empleadoId,
            @Param("fecha") LocalDate fecha);

    // Buscar horario activo de empleado en fecha específica
    @Query("SELECT d FROM HorarioSemanalDetalle d " +
           "WHERE d.empleado.id = :empleadoId AND d.fecha = :fecha " +
           "AND d.horarioSemanal.estado = 'activo'")
    Optional<HorarioSemanalDetalle> findHorarioActivoEmpleadoEnFecha(
            @Param("empleadoId") Integer empleadoId,
            @Param("fecha") LocalDate fecha);

    // Buscar todos los detalles asociados a una solicitud
    List<HorarioSemanalDetalle> findBySolicitudRefId(Integer solicitudId);

    // Buscar detalles de un empleado en una semana específica
    @Query("SELECT d FROM HorarioSemanalDetalle d " +
           "WHERE d.horarioSemanal.id = :semanaId AND d.empleado.id = :empleadoId " +
           "ORDER BY d.fecha ASC")
    List<HorarioSemanalDetalle> findByHorarioSemanalIdAndEmpleadoId(
            @Param("semanaId") Integer semanaId,
            @Param("empleadoId") Integer empleadoId);

    // Buscar detalles por tipo de día
    List<HorarioSemanalDetalle> findByHorarioSemanalIdAndTipoDia(
            Integer horarioSemanalId, String tipoDia);

    // Buscar detalles originados por solicitud
    @Query("SELECT d FROM HorarioSemanalDetalle d " +
           "WHERE d.horarioSemanal.id = :semanaId AND d.origenTipoDia = 'solicitud_aprobada'")
    List<HorarioSemanalDetalle> findDetallesConSolicitud(@Param("semanaId") Integer semanaId);

    // Eliminar detalles de una semana por empleado
    @Modifying
    @Query("DELETE FROM HorarioSemanalDetalle d " +
           "WHERE d.horarioSemanal.id = :semanaId AND d.empleado.id = :empleadoId")
    void deleteByHorarioSemanalIdAndEmpleadoId(
            @Param("semanaId") Integer semanaId,
            @Param("empleadoId") Integer empleadoId);

    // Contar detalles por tipo de día en una semana
    @Query("SELECT COUNT(d) FROM HorarioSemanalDetalle d " +
           "WHERE d.horarioSemanal.id = :semanaId AND d.tipoDia = :tipoDia")
    long countByTipoDiaInSemana(
            @Param("semanaId") Integer semanaId,
            @Param("tipoDia") String tipoDia);

    // Buscar todos los detalles de una semana ordenados por empleado y fecha
    @Query("SELECT d FROM HorarioSemanalDetalle d " +
           "WHERE d.horarioSemanal.id = :semanaId " +
           "ORDER BY d.empleado.nombre ASC, d.fecha ASC")
    List<HorarioSemanalDetalle> findByHorarioSemanalIdOrdenado(@Param("semanaId") Integer semanaId);
}
