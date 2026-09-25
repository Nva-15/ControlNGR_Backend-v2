package com.example.ControlNGR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.ControlNGR.entity.Empleado;
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

    /** Empleados activos que tienen horario y marcan asistencia. */
    @Query("SELECT e FROM Empleado e LEFT JOIN e.usuario u LEFT JOIN u.tipoUsuario t " +
           "WHERE e.activo = true AND (t IS NULL OR t.marcaAsistencia = true)")
    List<Empleado> findEmpleadosConHorario();

    @Query("SELECT e FROM Empleado e WHERE LOWER(e.usuario.tipoUsuario.codigo) = LOWER(:rol) " +
           "AND e.activo = true AND e.usuario.tipoUsuario.marcaAsistencia = true")
    List<Empleado> findEmpleadosConHorarioPorRol(@Param("rol") String rol);

    @Query("SELECT e FROM Empleado e WHERE e.nombre LIKE %:nombre%")
    List<Empleado> buscarPorNombre(@Param("nombre") String nombre);

    // Contar empleados activos por lista de roles
    @Query("SELECT COUNT(e) FROM Empleado e WHERE e.activo = true AND LOWER(e.usuario.tipoUsuario.codigo) IN :roles")
    Long countByRolInAndActivoTrue(@Param("roles") List<String> roles);
}
