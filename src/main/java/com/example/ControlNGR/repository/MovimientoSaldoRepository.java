package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.MovimientoSaldo;
import com.example.ControlNGR.entity.TipoSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovimientoSaldoRepository extends JpaRepository<MovimientoSaldo, Integer> {
    List<MovimientoSaldo> findByEmpleadoIdOrderByCreatedAtDescIdDesc(Integer empleadoId);
    List<MovimientoSaldo> findByEmpleadoIdAndTipoSaldoOrderByCreatedAtDescIdDesc(Integer empleadoId, TipoSaldo tipoSaldo);
}
