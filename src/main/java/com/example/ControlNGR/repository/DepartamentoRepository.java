package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.Departamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
    List<Departamento> findAllByOrderByNombreAsc();
    boolean existsByNombreIgnoreCase(String nombre);
}
