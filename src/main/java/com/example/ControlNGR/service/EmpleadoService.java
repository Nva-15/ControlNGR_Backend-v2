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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ControlNGR.dto.EmpleadoRequestDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Saldo;
import com.example.ControlNGR.entity.TipoSaldo;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.DepartamentoRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.SaldoRepository;
import com.example.ControlNGR.repository.UsuarioRepository;

@Service
@Transactional
public class EmpleadoService {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoService.class);

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private SaldoRepository saldoRepository;

    @Autowired
    private EmailService emailService;

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
            perfil.put("departamentoId", empleado.getDepartamentoId());
            perfil.put("departamentoNombre", empleado.getDepartamentoNombre());
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

    /** Restablece la contraseña (admin/gerencia). El usuario debera cambiarla al ingresar. */
    public boolean cambiarPasswordAdmin(Integer empleadoId, String passwordNueva) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmpleadoId(empleadoId);
        if (usuarioOpt.isEmpty()) {
            return false;
        }
        usuarioService.restablecerPassword(usuarioOpt.get(), passwordNueva);
        Empleado empleado = usuarioOpt.get().getEmpleado();
        if (empleado.getEmail() != null && !empleado.getEmail().trim().isEmpty()) {
            try {
                emailService.enviarNotificacionCambioPassword(empleado.getEmail(), empleado.getNombre());
            } catch (Exception e) {
                logger.error("Error enviando email de notificación: {}", e.getMessage());
            }
        }
        return true;
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

    /**
     * Actualiza un empleado existente.
     * @param puedeGestionarAcceso true si el editor puede cambiar rol/contraseña/usuario (admin o gerencia)
     */
    public Empleado actualizarEmpleado(Integer id, EmpleadoRequestDTO datos, boolean puedeGestionarAcceso) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (datos.getNombre() != null && !datos.getNombre().trim().isEmpty()) {
            empleado.setNombre(datos.getNombre().trim());
        }
        if (datos.getCargo() != null && !datos.getCargo().trim().isEmpty()) {
            empleado.setCargo(datos.getCargo().trim());
        }
        if (datos.getNivel() != null && !datos.getNivel().trim().isEmpty()) {
            empleado.setNivel(datos.getNivel().trim());
        }
        if (datos.getDepartamentoId() != null) {
            empleado.setDepartamento(departamentoRepository.findById(datos.getDepartamentoId())
                    .orElseThrow(() -> new RuntimeException("Departamento no encontrado")));
        }
        if (datos.getEmail() != null && !datos.getEmail().trim().isEmpty()) {
            String emailLimpio = datos.getEmail().trim().toLowerCase();
            if (!emailLimpio.contains("@") || !emailLimpio.contains(".")) {
                throw new RuntimeException("Formato de email inválido");
            }
            Optional<Empleado> conEmail = empleadoRepository.findFirstByEmail(emailLimpio);
            if (conEmail.isPresent() && !conEmail.get().getId().equals(id)) {
                throw new RuntimeException("El email ya está registrado por otro empleado");
            }
            empleado.setEmail(emailLimpio);
        }
        if (datos.getDescripcion() != null) {
            String d = datos.getDescripcion().trim();
            empleado.setDescripcion(d.isEmpty() ? null : d);
        }
        if (datos.getHobby() != null) {
            String h = datos.getHobby().trim();
            empleado.setHobby(h.isEmpty() ? null : h);
        }
        if (datos.getCumpleanos() != null) {
            empleado.setCumpleanos(datos.getCumpleanos());
        }
        if (datos.getIngreso() != null) {
            empleado.setIngreso(datos.getIngreso());
        }
        if (datos.getFoto() != null && !datos.getFoto().trim().isEmpty() && !datos.getFoto().equals("img/perfil.png")) {
            empleado.setFoto(datos.getFoto());
        }
        if (datos.getActivo() != null) {
            empleado.setActivo(datos.getActivo());
        }

        Usuario usuario = empleado.getUsuario();
        if (usuario != null) {
            if (datos.getUsuarioActivo() != null) {
                usuario.setActivo(datos.getUsuarioActivo());
            }
            if (puedeGestionarAcceso) {
                if (datos.getRol() != null && !datos.getRol().trim().isEmpty()
                        && !datos.getRol().trim().equalsIgnoreCase(usuario.getRol())) {
                    usuario.setTipoUsuario(usuarioService.tipoPersonal(datos.getRol()));
                }
                if (datos.getUsername() != null && !datos.getUsername().trim().isEmpty()
                        && !datos.getUsername().trim().equals(usuario.getUsername())) {
                    if (usuarioRepository.existsByUsername(datos.getUsername().trim())) {
                        throw new RuntimeException("El usuario '" + datos.getUsername().trim() + "' ya existe");
                    }
                    usuario.setUsername(datos.getUsername().trim());
                }
                String password = datos.getPassword();
                if (password != null && !password.trim().isEmpty() && !password.startsWith("$2")) {
                    usuarioService.restablecerPassword(usuario, password.trim());
                }
            }
            usuarioRepository.save(usuario);
        }
        return empleadoRepository.save(empleado);
    }

    /** Crea un empleado nuevo junto con su usuario de acceso (usuario y contraseña inicial: DNI). */
    public Empleado crearEmpleado(EmpleadoRequestDTO datos) {
        if (datos.getDni() == null || datos.getDni().trim().isEmpty()) {
            throw new RuntimeException("El DNI es requerido");
        }
        if (datos.getNombre() == null || datos.getNombre().trim().isEmpty()) {
            throw new RuntimeException("El nombre es requerido");
        }
        if (empleadoRepository.existsByDni(datos.getDni().trim())) {
            throw new RuntimeException("Ya existe un empleado con ese DNI");
        }

        Empleado empleado = new Empleado();
        empleado.setDni(datos.getDni().trim());
        empleado.setNombre(datos.getNombre().trim());
        empleado.setCargo(datos.getCargo());
        empleado.setNivel(datos.getNivel());
        empleado.setDescripcion(datos.getDescripcion());
        empleado.setHobby(datos.getHobby());
        empleado.setCumpleanos(datos.getCumpleanos());
        empleado.setIngreso(datos.getIngreso());
        if (datos.getFoto() != null && !datos.getFoto().isBlank()) {
            empleado.setFoto(datos.getFoto());
        }
        empleado.setActivo(datos.getActivo() == null || datos.getActivo());
        if (datos.getDepartamentoId() != null) {
            empleado.setDepartamento(departamentoRepository.findById(datos.getDepartamentoId())
                    .orElseThrow(() -> new RuntimeException("Departamento no encontrado")));
        }
        String identificador = datos.getIdentificador();
        if (identificador == null || identificador.trim().isEmpty()) {
            identificador = generarIdentificador(empleado.getNombre());
        }
        if (empleadoRepository.findByIdentificador(identificador).isPresent()) {
            identificador = identificador + "-" + empleado.getDni();
        }
        empleado.setIdentificador(identificador);
        String email = datos.getEmail();
        empleado.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());

        empleado = empleadoRepository.save(empleado);
        usuarioService.crearParaEmpleado(empleado, datos.getUsername(), datos.getPassword(), datos.getRol());

        for (TipoSaldo tipo : TipoSaldo.values()) {
            saldoRepository.save(new Saldo(empleado, tipo));
        }
        return empleado;
    }

    /** Activa/desactiva el empleado y/o su usuario. */
    public Empleado cambiarEstado(Empleado empleado, Boolean activo, Boolean usuarioActivo) {
        if (activo != null) {
            empleado.setActivo(activo);
        }
        if (usuarioActivo != null && empleado.getUsuario() != null) {
            empleado.getUsuario().setActivo(usuarioActivo);
            usuarioRepository.save(empleado.getUsuario());
        }
        return empleadoRepository.save(empleado);
    }

    /** Guarda cambios simples del empleado (por ejemplo, la foto). */
    public Empleado guardar(Empleado empleado) {
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

    /** Verifica si existe un usuario con el username. */
    public boolean existsByUsername(String username) {
        return usuarioRepository.existsByUsername(username);
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
