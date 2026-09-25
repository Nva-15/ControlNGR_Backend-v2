package com.example.ControlNGR.repository;

import com.example.ControlNGR.entity.ReglaAprobacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReglaAprobacionRepository extends JpaRepository<ReglaAprobacion, Integer> {

    @Query("SELECT COUNT(r) > 0 FROM ReglaAprobacion r WHERE r.activo = true " +
           "AND r.solicitante.codigo = :solicitante AND r.aprobador.codigo = :aprobador")
    boolean puedeAprobar(@Param("solicitante") String rolSolicitante, @Param("aprobador") String rolAprobador);

    @Query("SELECT r.aprobador.codigo FROM ReglaAprobacion r WHERE r.activo = true AND r.solicitante.codigo = :solicitante")
    List<String> rolesAprobadores(@Param("solicitante") String rolSolicitante);

    @Query("SELECT r.solicitante.codigo FROM ReglaAprobacion r WHERE r.activo = true AND r.aprobador.codigo = :aprobador")
    List<String> rolesQueAprueba(@Param("aprobador") String rolAprobador);

    boolean existsBySolicitanteIdAndAprobadorId(Integer solicitanteId, Integer aprobadorId);
}
