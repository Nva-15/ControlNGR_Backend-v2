package com.example.ControlNGR.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Empleado;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {

    Optional<Empleado> findByIdentificador(String identificador);

    Optional<Empleado> findByDni(String dni);

    @Query("SELECT e FROM Empleado e WHERE e.usuario.username = :username")
    Optional<Empleado> findByUsername(@Param("username") String username);

    Optional<Empleado> findFirstByEmail(String email);

    boolean existsByEmail(String email);

    Boolean existsByDni(String dni);

    List<Empleado> findByActivo(Boolean activo);

    List<Empleado> findByNivel(String nivel);

    @Query("SELECT e FROM Empleado e WHERE LOWER(e.usuario.tipoUsuario.codigo) = LOWER(:rol)")
    List<Empleado> findByRol(@Param("rol") String rol);

    @Query("SELECT e FROM Empleado e WHERE e.usuario.activo = :activo")
    List<Empleado> findByUsuarioActivo(@Param("activo") Boolean usuarioActivo);

    @Query("SELECT e FROM Empleado e WHERE LOWER(e.usuario.tipoUsuario.codigo) IN :roles " +
           "AND e.activo = true AND e.usuario.activo = true")
    List<Empleado> findActivosPorRoles(@Param("roles") List<String> roles);

    /** Personal activo cuyo rol marca asistencia con horario (configurable por rol en el panel admin):
     *  aparece en Horarios y en el reporte de asistencia. */
    @Query("SELECT e FROM Empleado e LEFT JOIN e.usuario u LEFT JOIN u.tipoUsuario t " +
           "WHERE e.activo = true AND (t IS NULL OR (t.marcaAsistencia = true AND t.conHorario = true))")
    List<Empleado> findEmpleadosConHorario();

    @Query("SELECT e FROM Empleado e WHERE LOWER(e.usuario.tipoUsuario.codigo) = LOWER(:rol) " +
           "AND e.activo = true AND e.usuario.tipoUsuario.marcaAsistencia = true " +
           "AND e.usuario.tipoUsuario.conHorario = true")
    List<Empleado> findEmpleadosConHorarioPorRol(@Param("rol") String rol);

    @Query("SELECT e FROM Empleado e WHERE e.nombre LIKE %:nombre%")
    List<Empleado> buscarPorNombre(@Param("nombre") String nombre);

    // Contar empleados activos por lista de roles
    @Query("SELECT COUNT(e) FROM Empleado e WHERE e.activo = true AND LOWER(e.usuario.tipoUsuario.codigo) IN :roles")
    Long countByRolInAndActivoTrue(@Param("roles") List<String> roles);

    // ==================== Listas paginadas (el servidor filtra) ====================

    String DESDE = " FROM Empleado e LEFT JOIN e.usuario u LEFT JOIN u.tipoUsuario t LEFT JOIN e.departamento d ";
    String DESDE_FETCH = " FROM Empleado e LEFT JOIN FETCH e.usuario u LEFT JOIN FETCH u.tipoUsuario t LEFT JOIN FETCH e.departamento d ";
    /** El DNI solo se busca entre quienes el lector puede ver con DNI (todos, o los roles que administra). */
    String FILTRO = "WHERE (:activo IS NULL OR e.activo = :activo) " +
            "AND (:departamentoId IS NULL OR d.id = :departamentoId) " +
            "AND (:rol IS NULL OR t.codigo = :rol) " +
            "AND (:soloSolicitan = false OR t.puedeSolicitar = true) " +
            "AND (:q IS NULL OR LOWER(e.nombre) LIKE :q OR LOWER(e.cargo) LIKE :q " +
            "     OR (e.dni LIKE :q AND (:dniTodos = true OR t.codigo IN :rolesDni)))";

    @Query(value = "SELECT e" + DESDE_FETCH + FILTRO, countQuery = "SELECT COUNT(e)" + DESDE + FILTRO)
    Page<Empleado> buscarPagina(@Param("q") String q, @Param("activo") Boolean activo,
                                @Param("departamentoId") Integer departamentoId, @Param("rol") String rol,
                                @Param("dniTodos") boolean dniTodos, @Param("rolesDni") Collection<String> rolesDni,
                                @Param("soloSolicitan") boolean soloSolicitan, Pageable pageable);

    /**
     * Quienes marcan asistencia, para el panel de reconocimiento facial. vista: todos, registrados,
     * sin_registrar, facial (su equipo marca con camara) o pendientes (equipo con camara y sin rostro).
     */
    String FILTRO_FACIAL = "WHERE e.activo = true AND t.marcaAsistencia = true " +
            "AND (:departamentoId IS NULL OR d.id = :departamentoId) " +
            "AND (:q IS NULL OR LOWER(e.nombre) LIKE :q OR e.dni LIKE :q) " +
            "AND (:vista = 'todos' " +
            "  OR (:vista = 'registrados' AND EXISTS (SELECT 1 FROM RostroEmpleado r WHERE r.empleado = e)) " +
            "  OR (:vista = 'sin_registrar' AND NOT EXISTS (SELECT 1 FROM RostroEmpleado r WHERE r.empleado = e)) " +
            "  OR ((:vista = 'facial' OR (:vista = 'pendientes' AND NOT EXISTS (SELECT 1 FROM RostroEmpleado r WHERE r.empleado = e))) " +
            "      AND (EXISTS (SELECT 1 FROM MarcacionEquipo m WHERE m.departamento = d AND m.modo = 'FACIAL') " +
            "           OR (:facialPorDefecto = true AND NOT EXISTS (SELECT 1 FROM MarcacionEquipo m2 WHERE m2.departamento = d)))))";

    @Query(value = "SELECT e" + DESDE_FETCH + FILTRO_FACIAL, countQuery = "SELECT COUNT(e)" + DESDE + FILTRO_FACIAL)
    Page<Empleado> buscarParaFacial(@Param("vista") String vista, @Param("q") String q,
                                    @Param("departamentoId") Integer departamentoId,
                                    @Param("facialPorDefecto") boolean facialPorDefecto, Pageable pageable);

    /** Quienes marcan asistencia por departamento: [departamentoId, total, conRostro]. */
    @Query("SELECT d.id, COUNT(e), SUM(CASE WHEN EXISTS (SELECT 1 FROM RostroEmpleado r WHERE r.empleado = e) THEN 1 ELSE 0 END)" +
           DESDE + "WHERE e.activo = true AND t.marcaAsistencia = true GROUP BY d.id")
    List<Object[]> contarMarcanPorDepartamento();
}
