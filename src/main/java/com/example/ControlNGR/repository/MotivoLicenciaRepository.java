package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.MotivoLicencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MotivoLicenciaRepository extends JpaRepository<MotivoLicencia, Integer> {
    List<MotivoLicencia> findByActivoTrueOrderByNombreAsc();
}
