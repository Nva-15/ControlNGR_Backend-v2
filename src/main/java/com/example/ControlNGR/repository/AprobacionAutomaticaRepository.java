package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.AprobacionAutomatica;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AprobacionAutomaticaRepository extends JpaRepository<AprobacionAutomatica, Integer> {

    Optional<AprobacionAutomatica> findByDepartamentoId(Integer departamentoId);

    /** Bloquea la fila del equipo para evaluar los cupos de a una solicitud por vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AprobacionAutomatica a WHERE a.departamento.id = :departamentoId")
    Optional<AprobacionAutomatica> bloquearPorDepartamento(@Param("departamentoId") Integer departamentoId);
}
