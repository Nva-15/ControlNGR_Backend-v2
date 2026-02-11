package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.HorarioSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioSemanalRepository extends JpaRepository<HorarioSemanal, Integer> {

    // Buscar por rango exacto de fechas
    Optional<HorarioSemanal> findByFechaInicioAndFechaFin(LocalDate fechaInicio, LocalDate fechaFin);

    // Buscar por estado
    List<HorarioSemanal> findByEstado(String estado);

    // Buscar semana que contiene una fecha específica
    @Query("SELECT h FROM HorarioSemanal h WHERE :fecha BETWEEN h.fechaInicio AND h.fechaFin")
    Optional<HorarioSemanal> findByFechaContenida(@Param("fecha") LocalDate fecha);

    // Buscar semana activa que contiene una fecha
    @Query("SELECT h FROM HorarioSemanal h WHERE :fecha BETWEEN h.fechaInicio AND h.fechaFin AND h.estado = 'activo'")
    Optional<HorarioSemanal> findActivoByFechaContenida(@Param("fecha") LocalDate fecha);

    // Listar todas ordenadas por fecha de inicio descendente
    List<HorarioSemanal> findAllByOrderByFechaInicioDesc();

    // Listar activas ordenadas por fecha
    @Query("SELECT h FROM HorarioSemanal h WHERE h.estado = 'activo' ORDER BY h.fechaInicio DESC")
    List<HorarioSemanal> findAllActivos();

    // Buscar semana anterior más reciente
    @Query("SELECT h FROM HorarioSemanal h WHERE h.fechaFin < :fecha ORDER BY h.fechaFin DESC")
    List<HorarioSemanal> findSemanasAnteriores(@Param("fecha") LocalDate fecha);

    // Verificar si existe semana que se solape con el rango dado
    @Query("SELECT h FROM HorarioSemanal h WHERE " +
           "(h.fechaInicio <= :fechaFin AND h.fechaFin >= :fechaInicio)")
    List<HorarioSemanal> findSemanasSolapadas(
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    // Contar semanas por estado
    long countByEstado(String estado);
}
