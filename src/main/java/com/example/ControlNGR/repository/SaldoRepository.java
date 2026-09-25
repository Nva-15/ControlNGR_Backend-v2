package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.Saldo;
import com.example.ControlNGR.entity.TipoSaldo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaldoRepository extends JpaRepository<Saldo, Integer> {

    Optional<Saldo> findByEmpleadoIdAndTipoSaldo(Integer empleadoId, TipoSaldo tipoSaldo);

    /** Bloquea la fila del saldo hasta el fin de la transaccion (evita dobles descuentos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Saldo s WHERE s.empleado.id = :empleadoId AND s.tipoSaldo = :tipo")
    Optional<Saldo> bloquear(@Param("empleadoId") Integer empleadoId, @Param("tipo") TipoSaldo tipo);

    List<Saldo> findByEmpleadoId(Integer empleadoId);
}
