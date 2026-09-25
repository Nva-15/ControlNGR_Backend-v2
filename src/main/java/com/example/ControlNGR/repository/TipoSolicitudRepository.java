package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.TipoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TipoSolicitudRepository extends JpaRepository<TipoSolicitud, Integer> {
    Optional<TipoSolicitud> findByCodigo(String codigo);
    List<TipoSolicitud> findByActivoTrue();
}
