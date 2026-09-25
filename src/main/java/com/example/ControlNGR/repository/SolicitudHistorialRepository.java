package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.SolicitudHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SolicitudHistorialRepository extends JpaRepository<SolicitudHistorial, Integer> {
    List<SolicitudHistorial> findBySolicitudIdOrderByFechaAsc(Integer solicitudId);
}
