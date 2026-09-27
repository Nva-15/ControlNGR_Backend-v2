package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.RostroEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RostroEmpleadoRepository extends JpaRepository<RostroEmpleado, Integer> {

    List<RostroEmpleado> findByEmpleadoId(Integer empleadoId);

    long countByEmpleadoId(Integer empleadoId);

    /** Muestras de todos los empleados excepto uno (para evitar un mismo rostro en dos cuentas). */
    @Query("SELECT r FROM RostroEmpleado r JOIN FETCH r.empleado e WHERE e.id <> :empleadoId")
    List<RostroEmpleado> findDeOtrosEmpleados(@Param("empleadoId") Integer empleadoId);

    /** Todas las muestras con su empleado (para las pruebas del panel admin). */
    @Query("SELECT r FROM RostroEmpleado r JOIN FETCH r.empleado")
    List<RostroEmpleado> findTodasConEmpleado();

    @Query("SELECT DISTINCT r.empleado.id FROM RostroEmpleado r")
    List<Integer> findEmpleadosRegistrados();

    @Modifying
    @Query("DELETE FROM RostroEmpleado r WHERE r.empleado.id = :empleadoId")
    int eliminarDeEmpleado(@Param("empleadoId") Integer empleadoId);
}
