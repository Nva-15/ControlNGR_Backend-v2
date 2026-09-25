package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.DiaFeriado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaFeriadoRepository extends JpaRepository<DiaFeriado, Integer> {
    Optional<DiaFeriado> findByFechaAndActivoTrue(LocalDate fecha);
    boolean existsByFecha(LocalDate fecha);

    @Query("SELECT f FROM DiaFeriado f WHERE YEAR(f.fecha) = :anio ORDER BY f.fecha")
    List<DiaFeriado> findByAnio(@Param("anio") int anio);

    List<DiaFeriado> findAllByOrderByFechaAsc();
}
