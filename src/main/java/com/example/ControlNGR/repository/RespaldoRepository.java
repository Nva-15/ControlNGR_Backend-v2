package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.Respaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface RespaldoRepository extends JpaRepository<Respaldo, Long> {

    List<Respaldo> findAllByOrderByIniciadoEnDescIdDesc();

    List<Respaldo> findByTipoAndEstadoOrderByIniciadoEnDescIdDesc(String tipo, String estado);

    boolean existsByTipoAndIniciadoEnGreaterThanEqual(String tipo, LocalDateTime desde);

    @Modifying
    @Query("UPDATE Respaldo r SET r.estado = 'FALLIDO', r.finalizadoEn = :ahora, r.mensaje = :mensaje WHERE r.estado = 'EN_CURSO'")
    int marcarInterrumpidos(LocalDateTime ahora, String mensaje);
}
