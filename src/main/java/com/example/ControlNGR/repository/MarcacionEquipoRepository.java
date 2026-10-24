package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.MarcacionEquipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarcacionEquipoRepository extends JpaRepository<MarcacionEquipo, Integer> {

    Optional<MarcacionEquipo> findByDepartamentoId(Integer departamentoId);
}
