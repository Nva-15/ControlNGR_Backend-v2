package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.SolicitudEvidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitudEvidenciaRepository extends JpaRepository<SolicitudEvidencia, Integer> {
    List<SolicitudEvidencia> findBySolicitudId(Integer solicitudId);
    Optional<SolicitudEvidencia> findByIdAndSolicitudId(Integer id, Integer solicitudId);
}
