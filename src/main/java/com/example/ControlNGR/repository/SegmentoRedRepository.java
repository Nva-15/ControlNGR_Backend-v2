package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.SegmentoRed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SegmentoRedRepository extends JpaRepository<SegmentoRed, Integer> {
    List<SegmentoRed> findByActivoTrue();
    boolean existsByPatron(String patron);
}
