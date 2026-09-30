package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.Herramienta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HerramientaRepository extends JpaRepository<Herramienta, Integer> {
    List<Herramienta> findAllByOrderByTituloAsc();
}
