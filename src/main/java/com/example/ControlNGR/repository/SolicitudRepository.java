package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Solicitud;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SolicitudRepository extends JpaRepository<Solicitud, Integer> {
    
    List<Solicitud> findByEmpleadoId(Integer empleadoId);
    List<Solicitud> findByEstado(String estado);
    List<Solicitud> findByTipoSolicitudCodigo(String codigo);
    
    List<Solicitud> findByEmpleadoIdOrderByFechaSolicitudDesc(Integer empleadoId);
    
    List<Solicitud> findByEstadoOrderByFechaSolicitudDesc(String estado);
    
    @Query("SELECT s FROM Solicitud s WHERE s.empleado.id = :empleadoId AND s.estado = 'aprobado' " +
           "AND :fecha BETWEEN s.fechaInicio AND s.fechaFin")
    List<Solicitud> findSolicitudesAprobadasPorEmpleadoEnFecha(
            @Param("empleadoId") Integer empleadoId,
            @Param("fecha") LocalDate fecha);
    
    List<Solicitud> findByAprobadoPorId(Integer aprobadoPorId);
    
    @Query("SELECT s FROM Solicitud s WHERE s.estado = 'pendiente'")
    List<Solicitud> findSolicitudesPendientes();
    
    @Query("SELECT s FROM Solicitud s WHERE s.empleado.id = :empleadoId " +
           "AND s.estado IN ('pendiente', 'aprobado') " +
           "AND ((s.fechaInicio BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(s.fechaFin BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(:fechaInicio BETWEEN s.fechaInicio AND s.fechaFin) OR " +
           "(:fechaFin BETWEEN s.fechaInicio AND s.fechaFin))")
    List<Solicitud> findConflictosPorRangoFechas(
            @Param("empleadoId") Integer empleadoId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);
    
    @Query("SELECT s FROM Solicitud s WHERE s.empleado.id != :empleadoId " +
           "AND s.empleado.usuario.tipoUsuario.codigo = :rolEmpleado " +
           "AND s.estado IN ('pendiente', 'aprobado') " +
           "AND ((s.fechaInicio BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(s.fechaFin BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(:fechaInicio BETWEEN s.fechaInicio AND s.fechaFin) OR " +
           "(:fechaFin BETWEEN s.fechaInicio AND s.fechaFin)) " +
           "ORDER BY s.fechaInicio")
    List<Solicitud> findConflictosPorRolYRangoFechas(
            @Param("empleadoId") Integer empleadoId,
            @Param("rolEmpleado") String rolEmpleado,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    // Buscar solicitudes aprobadas que se solapan con un rango de fechas
    @Query("SELECT s FROM Solicitud s WHERE s.estado = 'aprobado' " +
           "AND ((s.fechaInicio BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(s.fechaFin BETWEEN :fechaInicio AND :fechaFin) OR " +
           "(:fechaInicio BETWEEN s.fechaInicio AND s.fechaFin) OR " +
           "(:fechaFin BETWEEN s.fechaInicio AND s.fechaFin))")
    List<Solicitud> findSolicitudesAprobadasEnRango(
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    /** Dias ya comprometidos en solicitudes pendientes que descuentan del mismo saldo. */
    @Query("SELECT COALESCE(SUM(s.diasSolicitados), 0) FROM Solicitud s WHERE s.empleado.id = :empleadoId " +
           "AND s.estado = 'pendiente' AND s.tipoSolicitud.descuentaDe = :descuentaDe " +
           "AND (:excluirId IS NULL OR s.id <> :excluirId)")
    java.math.BigDecimal sumarDiasPendientes(
            @Param("empleadoId") Integer empleadoId,
            @Param("descuentaDe") String descuentaDe,
            @Param("excluirId") Integer excluirId);

    /** Pendientes cuyos solicitantes tienen alguno de los roles indicados. */
    @Query("SELECT s FROM Solicitud s WHERE s.estado = 'pendiente' " +
           "AND s.empleado.usuario.tipoUsuario.codigo IN :roles ORDER BY s.fechaSolicitud DESC")
    List<Solicitud> findPendientesDeRoles(@Param("roles") List<String> roles);

    // Contar solicitudes con cambio de estado reciente para un empleado
    @Query("SELECT COUNT(s) FROM Solicitud s WHERE s.empleado.id = :empleadoId " +
           "AND s.estado = :estado AND s.fechaAprobacion >= :desde")
    long countSolicitudesConEstadoDesde(
            @Param("empleadoId") Integer empleadoId,
            @Param("estado") String estado,
            @Param("desde") LocalDateTime desde);

}