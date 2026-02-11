package com.example.ControlNGR.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.repository.EmpleadoRepository;

@Service
@Transactional
public class EmpleadoService {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoService.class);

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    /** Valida las credenciales del empleado. */
    public Optional<Empleado> validarCredenciales(String username, String password) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findByUsername(username);
        if (empleadoOpt.isPresent()) {
            Empleado empleado = empleadoOpt.get();

            if (Boolean.TRUE.equals(empleado.getActivo()) &&
                Boolean.TRUE.equals(empleado.getUsuarioActivo())) {

                if (passwordEncoder.matches(password, empleado.getPassword())) {
                    return Optional.of(empleado);
                }
            }
        }
        return Optional.empty();
    }

    /** Obtiene el perfil completo de un empleado. */
    public Map<String, Object> obtenerPerfilCompleto(Integer empleadoId) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);
        if (empleadoOpt.isPresent()) {
            Empleado empleado = empleadoOpt.get();
            Map<String, Object> perfil = new HashMap<>();

            perfil.put("id", empleado.getId());
            perfil.put("dni", empleado.getDni());
            perfil.put("nombre", empleado.getNombre());
            perfil.put("cargo", empleado.getCargo());
            perfil.put("nivel", empleado.getNivel());
            perfil.put("username", empleado.getUsername());
            perfil.put("email", empleado.getEmail());
            perfil.put("rol", empleado.getRol());
            perfil.put("descripcion", empleado.getDescripcion());
            perfil.put("hobby", empleado.getHobby());
            perfil.put("cumpleanos", empleado.getCumpleanos());
            perfil.put("ingreso", empleado.getIngreso());
            perfil.put("foto", empleado.getFoto());
            perfil.put("activo", empleado.getActivo());
            perfil.put("usuarioActivo", empleado.getUsuarioActivo());

            return perfil;
        }
        return null;
    }

    /** Actualiza la información personal del empleado. */
    public Map<String, Object> actualizarInformacionPersonal(Integer empleadoId, Map<String, Object> datos) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);
        if (!empleadoOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }

        Empleado empleado = empleadoOpt.get();
        Map<String, Object> cambios = new HashMap<>();
        boolean cambiosRealizados = false;

        // Actualizar nombre
        if (datos.containsKey("nombre") && datos.get("nombre") != null) {
            String nuevoNombre = ((String) datos.get("nombre")).trim();
            if (!nuevoNombre.isEmpty() && !nuevoNombre.equals(empleado.getNombre())) {
                empleado.setNombre(nuevoNombre);
                cambios.put("nombre", nuevoNombre);
                cambiosRealizados = true;
            }
        }

        // Actualizar descripción
        if (datos.containsKey("descripcion")) {
            Object descObj = datos.get("descripcion");
            String nuevaDescripcion = descObj != null ? ((String) descObj).trim() : "";
            String actualDescripcion = empleado.getDescripcion() != null ? empleado.getDescripcion() : "";

            if (!nuevaDescripcion.equals(actualDescripcion)) {
                empleado.setDescripcion(nuevaDescripcion.isEmpty() ? null : nuevaDescripcion);
                cambios.put("descripcion", nuevaDescripcion.isEmpty() ? "(eliminado)" : nuevaDescripcion);
                cambiosRealizados = true;
            }
        }

        // Actualizar hobby
        if (datos.containsKey("hobby")) {
            Object hobbyObj = datos.get("hobby");
            String nuevoHobby = hobbyObj != null ? ((String) hobbyObj).trim() : "";
            String actualHobby = empleado.getHobby() != null ? empleado.getHobby() : "";

            if (!nuevoHobby.equals(actualHobby)) {
                empleado.setHobby(nuevoHobby.isEmpty() ? null : nuevoHobby);
                cambios.put("hobby", nuevoHobby.isEmpty() ? "(eliminado)" : nuevoHobby);
                cambiosRealizados = true;
            }
        }

        // Actualizar cumpleaños
        if (datos.containsKey("cumpleanos")) {
            try {
                String fechaStr = (String) datos.get("cumpleanos");
                if (fechaStr != null && !fechaStr.isEmpty()) {
                    java.time.LocalDate nuevaFecha = java.time.LocalDate.parse(fechaStr);
                    if (!nuevaFecha.equals(empleado.getCumpleanos())) {
                        empleado.setCumpleanos(nuevaFecha);
                        cambios.put("cumpleanos", fechaStr);
                        cambiosRealizados = true;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Formato de fecha inválido. Use formato YYYY-MM-DD");
            }
        }

        // Actualizar email
        if (datos.containsKey("email")) {
            String nuevoEmail = (String) datos.get("email");
            if (nuevoEmail != null && !nuevoEmail.trim().isEmpty()) {
                String emailLimpio = nuevoEmail.trim().toLowerCase();

                if (!emailLimpio.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    throw new RuntimeException("Formato de email inválido");
                }

                Optional<Empleado> empleadoExistente = empleadoRepository.findFirstByEmail(emailLimpio);
                if (empleadoExistente.isPresent() && !empleadoExistente.get().getId().equals(empleadoId)) {
                    throw new RuntimeException("El email ya está registrado por otro usuario");
                }

                if (!emailLimpio.equals(empleado.getEmail())) {
                    empleado.setEmail(emailLimpio);
                    cambios.put("email", emailLimpio);
                    cambiosRealizados = true;
                }
            }
        }

        if (cambiosRealizados) {
            empleadoRepository.save(empleado);

            // Enviar notificación
            if (empleado.getEmail() != null && !empleado.getEmail().trim().isEmpty()) {
                try {
                    emailService.enviarNotificacionActualizacionPerfil(empleado.getEmail(), empleado.getNombre());
                } catch (Exception e) {
                    logger.error("Error enviando email de notificación: {}", e.getMessage());
                }
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("success", cambiosRealizados);
        resultado.put("message", cambiosRealizados ? "Información actualizada correctamente" : "No se realizaron cambios");
        resultado.put("cambios", cambios);
        resultado.put("empleadoId", empleadoId);

        return resultado;
    }

    /** Cambia la contraseña del usuario. */
    public Map<String, Object> cambiarPasswordUsuario(Integer empleadoId, String passwordActual, String passwordNueva) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);
        if (!empleadoOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }

        Empleado empleado = empleadoOpt.get();
        Map<String, Object> resultado = new HashMap<>();

        if (!passwordEncoder.matches(passwordActual, empleado.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }

        if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
            throw new RuntimeException("La nueva contraseña es requerida");
        }

        if (passwordNueva.length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
        }

        if (passwordEncoder.matches(passwordNueva, empleado.getPassword())) {
            throw new RuntimeException("La nueva contraseña no puede ser igual a la actual");
        }

        empleado.setPassword(passwordEncoder.encode(passwordNueva));
        empleadoRepository.save(empleado);

        // Enviar notificación
        if (empleado.getEmail() != null && !empleado.getEmail().trim().isEmpty()) {
            try {
                emailService.enviarNotificacionCambioPassword(empleado.getEmail(), empleado.getNombre());
            } catch (Exception e) {
                logger.error("Error enviando email de notificación: {}", e.getMessage());
            }
        }

        resultado.put("success", true);
        resultado.put("message", "Contraseña cambiada exitosamente");
        resultado.put("empleadoId", empleadoId);
        resultado.put("fechaCambio", java.time.LocalDateTime.now());

        return resultado;
    }

    /** Cambia la contraseña (retorna boolean). */
    public boolean cambiarPassword(Integer empleadoId, String passwordActual, String passwordNueva) {
        try {
            Map<String, Object> resultado = cambiarPasswordUsuario(empleadoId, passwordActual, passwordNueva);
            return (Boolean) resultado.get("success");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Cambia la contraseña como administrador. */
    public boolean cambiarPasswordAdmin(Integer empleadoId, String passwordNueva) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);
        if (empleadoOpt.isPresent()) {
            Empleado empleado = empleadoOpt.get();

            if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
                throw new RuntimeException("La nueva contraseña es requerida");
            }

            if (passwordNueva.length() < 6) {
                throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
            }

            empleado.setPassword(passwordEncoder.encode(passwordNueva));
            empleadoRepository.save(empleado);

            // Enviar notificación
            if (empleado.getEmail() != null && !empleado.getEmail().trim().isEmpty()) {
                try {
                    emailService.enviarNotificacionCambioPassword(empleado.getEmail(), empleado.getNombre());
                } catch (Exception e) {
                    logger.error("Error enviando email de notificación: {}", e.getMessage());
                }
            }

            return true;
        }
        return false;
    }

    /** Actualiza el perfil del empleado. */
    public boolean actualizarPerfil(Integer empleadoId, Map<String, Object> datos) {
        try {
            Map<String, Object> resultado = actualizarInformacionPersonal(empleadoId, datos);
            return (Boolean) resultado.get("success");
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /** Obtiene todos los empleados. */
    public List<Empleado> findAll() {
        return empleadoRepository.findAll();
    }

    /** Busca empleado por ID. */
    public Optional<Empleado> findById(Integer id) {
        return empleadoRepository.findById(id);
    }

    /** Busca empleado por DNI. */
    public Optional<Empleado> findByDni(String dni) {
        return empleadoRepository.findByDni(dni);
    }

    /** Busca empleado por username. */
    public Optional<Empleado> findByUsername(String username) {
        return empleadoRepository.findByUsername(username);
    }

    /** Busca empleado por email. */
    public Optional<Empleado> findFirstByEmail(String email) {
        return empleadoRepository.findFirstByEmail(email);
    }

    private String generarIdentificador(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }

        String[] partes = nombreCompleto.trim().toLowerCase().split("\\s+");
        if (partes.length >= 2) {
            String nombre = partes[0];
            String apellido = partes[partes.length - 1];

            nombre = nombre.replaceAll("[^a-záéíóúüñ]", "");
            apellido = apellido.replaceAll("[^a-záéíóúüñ]", "");

            return nombre + "-" + apellido;
        } else if (partes.length == 1) {
            return partes[0].toLowerCase().replaceAll("[^a-záéíóúüñ]", "");
        }

        return UUID.randomUUID().toString();
    }

    /** Actualiza un empleado existente. */
    public Empleado actualizarEmpleado(Integer id, Empleado empleadoActualizado) {
        Optional<Empleado> empleadoExistenteOpt = empleadoRepository.findById(id);
        if (!empleadoExistenteOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }

        Empleado empleadoExistente = empleadoExistenteOpt.get();

        // Actualizar nombre
        if (empleadoActualizado.getNombre() != null &&
            !empleadoActualizado.getNombre().trim().isEmpty() &&
            !empleadoActualizado.getNombre().equals(empleadoExistente.getNombre())) {
            empleadoExistente.setNombre(empleadoActualizado.getNombre().trim());
        }

        // Actualizar cargo
        if (empleadoActualizado.getCargo() != null &&
            !empleadoActualizado.getCargo().trim().isEmpty() &&
            !empleadoActualizado.getCargo().equals(empleadoExistente.getCargo())) {
            empleadoExistente.setCargo(empleadoActualizado.getCargo().trim());
        }

        // Actualizar nivel
        if (empleadoActualizado.getNivel() != null &&
            !empleadoActualizado.getNivel().trim().isEmpty() &&
            !empleadoActualizado.getNivel().equals(empleadoExistente.getNivel())) {
            empleadoExistente.setNivel(empleadoActualizado.getNivel());
        }

        // Actualizar email
        if (empleadoActualizado.getEmail() != null &&
            !empleadoActualizado.getEmail().trim().isEmpty() &&
            !empleadoActualizado.getEmail().trim().toLowerCase().equals(empleadoExistente.getEmail())) {

            String emailLimpio = empleadoActualizado.getEmail().trim().toLowerCase();

            if (!emailLimpio.contains("@") || !emailLimpio.contains(".")) {
                throw new RuntimeException("Formato de email inválido");
            }

            Optional<Empleado> empleadoConEmail = empleadoRepository.findFirstByEmail(emailLimpio);
            if (empleadoConEmail.isPresent() && !empleadoConEmail.get().getId().equals(id)) {
                throw new RuntimeException("El email ya está registrado por otro empleado");
            }

            empleadoExistente.setEmail(emailLimpio);
        }

        // Actualizar descripción
        if (empleadoActualizado.getDescripcion() != null) {
            String nuevaDesc = empleadoActualizado.getDescripcion().trim();
            String actualDesc = empleadoExistente.getDescripcion() != null ? empleadoExistente.getDescripcion() : "";
            if (!nuevaDesc.equals(actualDesc)) {
                empleadoExistente.setDescripcion(nuevaDesc.isEmpty() ? null : nuevaDesc);
            }
        }

        // Actualizar hobby
        if (empleadoActualizado.getHobby() != null) {
            String nuevoHobby = empleadoActualizado.getHobby().trim();
            String actualHobby = empleadoExistente.getHobby() != null ? empleadoExistente.getHobby() : "";
            if (!nuevoHobby.equals(actualHobby)) {
                empleadoExistente.setHobby(nuevoHobby.isEmpty() ? null : nuevoHobby);
            }
        }

        // Actualizar fechas
        if (empleadoActualizado.getCumpleanos() != null &&
            !empleadoActualizado.getCumpleanos().equals(empleadoExistente.getCumpleanos())) {
            empleadoExistente.setCumpleanos(empleadoActualizado.getCumpleanos());
        }

        if (empleadoActualizado.getIngreso() != null &&
            !empleadoActualizado.getIngreso().equals(empleadoExistente.getIngreso())) {
            empleadoExistente.setIngreso(empleadoActualizado.getIngreso());
        }

        // Actualizar foto (no actualizar si es valor por defecto)
        if (empleadoActualizado.getFoto() != null &&
            !empleadoActualizado.getFoto().trim().isEmpty() &&
            !empleadoActualizado.getFoto().equals("img/perfil.png") &&
            !empleadoActualizado.getFoto().equals(empleadoExistente.getFoto())) {
            empleadoExistente.setFoto(empleadoActualizado.getFoto());
        }

        // Actualizar estados
        if (empleadoActualizado.getActivo() != null &&
            !empleadoActualizado.getActivo().equals(empleadoExistente.getActivo())) {
            empleadoExistente.setActivo(empleadoActualizado.getActivo());
        }

        if (empleadoActualizado.getUsuarioActivo() != null &&
            !empleadoActualizado.getUsuarioActivo().equals(empleadoExistente.getUsuarioActivo())) {
            empleadoExistente.setUsuarioActivo(empleadoActualizado.getUsuarioActivo());
        }

        // Actualizar rol (no actualizar si es valor por defecto "tecnico")
        if (empleadoActualizado.getRol() != null &&
            !empleadoActualizado.getRol().trim().isEmpty() &&
            !empleadoActualizado.getRol().equals("tecnico") &&
            !empleadoActualizado.getRol().equals(empleadoExistente.getRol())) {
            empleadoExistente.setRol(empleadoActualizado.getRol());
        }

        // Actualizar password (solo si no está encriptado)
        if (empleadoActualizado.getPassword() != null &&
            !empleadoActualizado.getPassword().trim().isEmpty()) {

            String password = empleadoActualizado.getPassword().trim();

            if (!password.startsWith("$2a$") &&
                !password.startsWith("$2b$") &&
                !password.startsWith("$2y$")) {

                if (!passwordEncoder.matches(password, empleadoExistente.getPassword())) {
                    empleadoExistente.setPassword(passwordEncoder.encode(password));
                }
            }
        }

        return empleadoRepository.save(empleadoExistente);
    }

    /** Guarda un empleado (nuevo o existente). */
    public Empleado save(Empleado empleado) {
        boolean esNuevo = empleado.getId() == null;

        if (esNuevo) {
            // Generar identificador
            if (empleado.getIdentificador() == null || empleado.getIdentificador().trim().isEmpty()) {
                empleado.setIdentificador(generarIdentificador(empleado.getNombre()));
            }

            // Asignar username por defecto
            if (empleado.getUsername() == null || empleado.getUsername().trim().isEmpty()) {
                empleado.setUsername(empleado.getDni());
            }

            // Generar email por defecto
            if (empleado.getEmail() == null || empleado.getEmail().trim().isEmpty()) {
                String emailGenerado = empleado.getUsername() + "@ngr.com.pe";
                empleado.setEmail(emailGenerado);
            }

            // Asignar rol según nivel
            if (empleado.getRol() == null || empleado.getRol().trim().isEmpty()) {
                if (empleado.getNivel() != null) {
                    switch (empleado.getNivel().toLowerCase()) {
                        case "gerente":
                        case "jefe":
                            empleado.setRol("admin");
                            break;
                        case "supervisor":
                            empleado.setRol("supervisor");
                            break;
                        default:
                            empleado.setRol("tecnico");
                    }
                } else {
                    empleado.setRol("tecnico");
                }
            }

            // Encriptar password
            if (empleado.getPassword() != null && !empleado.getPassword().trim().isEmpty()) {
                String password = empleado.getPassword().trim();
                if (!password.startsWith("$2a$") && !password.startsWith("$2b$") && !password.startsWith("$2y$")) {
                    empleado.setPassword(passwordEncoder.encode(password));
                }
            } else {
                empleado.setPassword(passwordEncoder.encode("password"));
            }

        } else {
            return actualizarEmpleado(empleado.getId(), empleado);
        }

        return empleadoRepository.save(empleado);
    }

    /** Elimina un empleado por ID. */
    public void deleteById(Integer id) {
        empleadoRepository.deleteById(id);
    }

    /** Verifica si existe un empleado con el DNI. */
    public boolean existsByDni(String dni) {
        return empleadoRepository.existsByDni(dni);
    }

    /** Verifica si existe un empleado con el username. */
    public boolean existsByUsername(String username) {
        return empleadoRepository.existsByUsername(username);
    }

    /** Busca empleados por rol. */
    public List<Empleado> findByRol(String rol) {
        return empleadoRepository.findByRol(rol);
    }

    /** Busca empleados por estado de usuario. */
    public List<Empleado> findByUsuarioActivo(Boolean activo) {
        return empleadoRepository.findByUsuarioActivo(activo);
    }

    /** Exporta todos los empleados. */
    public List<Map<String, Object>> exportarEmpleados() {
        return empleadoRepository.findAll().stream()
                .map(e -> {
                    Map<String, Object> datos = new HashMap<>();
                    datos.put("ID", e.getId());
                    datos.put("DNI", e.getDni());
                    datos.put("Nombre", e.getNombre());
                    datos.put("Cargo", e.getCargo());
                    datos.put("Nivel", e.getNivel());
                    datos.put("Email", e.getEmail());
                    datos.put("Usuario", e.getUsername());
                    datos.put("Rol", e.getRol());
                    datos.put("Activo", e.getActivo() != null && e.getActivo() ? "Sí" : "No");
                    datos.put("Usuario_Activo", e.getUsuarioActivo() != null && e.getUsuarioActivo() ? "Sí" : "No");
                    datos.put("Fecha_Ingreso", e.getIngreso());
                    datos.put("Cumpleaños", e.getCumpleanos());
                    datos.put("Descripción", e.getDescripcion());
                    datos.put("Hobby", e.getHobby());
                    return datos;
                })
                .collect(Collectors.toList());
    }

    /** Actualiza el email de un empleado. */
    public boolean actualizarEmail(Integer empleadoId, String email) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);
        if (empleadoOpt.isPresent()) {
            Empleado empleado = empleadoOpt.get();

            Optional<Empleado> empleadoExistente = empleadoRepository.findFirstByEmail(email);
            if (empleadoExistente.isPresent() && !empleadoExistente.get().getId().equals(empleadoId)) {
                throw new RuntimeException("El email ya está registrado por otro usuario");
            }

            empleado.setEmail(email);
            empleadoRepository.save(empleado);
            return true;
        }
        return false;
    }
}
