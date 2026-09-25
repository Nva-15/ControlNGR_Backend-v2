package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.FeriadoLaborado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeriadoLaboradoRepository extends JpaRepository<FeriadoLaborado, Integer> {
    boolean existsByEmpleadoIdAndFeriadoId(Integer empleadoId, Integer feriadoId);
    List<FeriadoLaborado> findByEmpleadoIdOrderByFechaDesc(Integer empleadoId);
    List<FeriadoLaborado> findAllByOrderByFechaDesc();
}
