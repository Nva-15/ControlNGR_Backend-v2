package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Horario;
import com.example.ControlNGR.entity.Empleado;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Integer> {
    
    List<Horario> findByEmpleado(Empleado empleado);
    
    List<Horario> findByEmpleadoId(Integer empleadoId);
    
    Optional<Horario> findByEmpleadoAndDiaSemana(Empleado empleado, String diaSemana);
    
    @Query("SELECT h FROM Horario h WHERE h.empleado.id = :empleadoId AND h.diaSemana = :diaSemana")
    Optional<Horario> findByEmpleadoIdAndDiaSemana(
            @Param("empleadoId") Integer empleadoId,
            @Param("diaSemana") String diaSemana);
    
    void deleteByEmpleadoId(Integer empleadoId);
}