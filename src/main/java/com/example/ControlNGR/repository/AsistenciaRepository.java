package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Asistencia;
import com.example.ControlNGR.entity.Empleado;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Integer> {
    
    // Validar si ya existe asistencia para un empleado en una fecha
    Optional<Asistencia> findByEmpleadoAndFecha(Empleado empleado, LocalDate fecha);
    
    // Historial completo de un empleado
    List<Asistencia> findByEmpleadoId(Integer empleadoId);
    
    // Asistencias de un día específico
    List<Asistencia> findByFecha(LocalDate fecha);
    
    // Rango de fechas
    List<Asistencia> findByFechaBetween(LocalDate fechaInicio, LocalDate fechaFin);
    
    // Por estado (tardanza, presente, etc)
    List<Asistencia> findByEstado(String estado);
    
    // Reporte mensual: Usa funciones YEAR() y MONTH() de JPQL
    @Query("SELECT a FROM Asistencia a WHERE a.empleado.id = :empleadoId AND YEAR(a.fecha) = :year AND MONTH(a.fecha) = :month")
    List<Asistencia> findByEmpleadoAndMonthYear(
            @Param("empleadoId") Integer empleadoId,
            @Param("year") int year,
            @Param("month") int month);
    
    // Buscar asistencias sin marcar salida de días anteriores o de hoy
    @Query("SELECT a FROM Asistencia a WHERE a.horaEntrada IS NOT NULL AND a.horaSalida IS NULL AND a.fecha <= CURRENT_DATE")
    List<Asistencia> findAsistenciasConSalidaPendiente();
}