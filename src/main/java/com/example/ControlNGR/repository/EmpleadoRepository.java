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

    Optional<Empleado> findByUsername(String username);

    Optional<Empleado> findFirstByEmail(String email);

    boolean existsByEmail(String email);

    Boolean existsByDni(String dni);

    Boolean existsByUsername(String username);

    List<Empleado> findByActivo(Boolean activo);

    List<Empleado> findByNivel(String nivel);

    List<Empleado> findByRol(String rol);

    List<Empleado> findByUsuarioActivo(Boolean usuarioActivo);

    List<Empleado> findByRolAndActivo(String rol, Boolean activo);

    @Query("SELECT e FROM Empleado e WHERE e.activo = true AND LOWER(e.rol) <> 'admin'")
    List<Empleado> findEmpleadosConHorario();

    @Query("SELECT e FROM Empleado e WHERE LOWER(e.rol) = LOWER(:rol) AND e.activo = true AND LOWER(e.rol) <> 'admin'")
    List<Empleado> findEmpleadosConHorarioPorRol(@Param("rol") String rol);

    @Query("SELECT e FROM Empleado e WHERE e.nombre LIKE %:nombre%")
    List<Empleado> buscarPorNombre(@Param("nombre") String nombre);

    // Contar empleados activos por lista de roles
    @Query("SELECT COUNT(e) FROM Empleado e WHERE e.activo = true AND LOWER(e.rol) IN :roles")
    Long countByRolInAndActivoTrue(@Param("roles") List<String> roles);
}
