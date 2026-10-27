package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.FuncionTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FuncionTurnoRepository extends JpaRepository<FuncionTurno, Integer> {
    List<FuncionTurno> findAllByOrderByOrdenAscCodigoAsc();
    boolean existsByCodigoIgnoreCase(String codigo);
}
