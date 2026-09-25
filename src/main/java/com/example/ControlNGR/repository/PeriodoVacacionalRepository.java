package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.PeriodoVacacional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PeriodoVacacionalRepository extends JpaRepository<PeriodoVacacional, Integer> {
    boolean existsByEmpleadoIdAndPeriodoInicio(Integer empleadoId, LocalDate periodoInicio);
    List<PeriodoVacacional> findByEmpleadoIdOrderByPeriodoInicioDesc(Integer empleadoId);
}
