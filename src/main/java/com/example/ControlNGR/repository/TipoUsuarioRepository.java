package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TipoUsuarioRepository extends JpaRepository<TipoUsuario, Integer> {
    Optional<TipoUsuario> findByCodigo(String codigo);
    List<TipoUsuario> findAllByOrderByNivelJerarquiaDesc();
}
