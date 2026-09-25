package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.SolicitudRequestDTO;
import com.example.ControlNGR.dto.SolicitudResponseDTO;
import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.entity.MovimientoSaldo.Origen;
import com.example.ControlNGR.entity.MovimientoSaldo.TipoMovimiento;
import com.example.ControlNGR.repository.*;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Solicitudes de vacaciones, compensacion por feriado, descanso medico y licencias.
 * <ul>
 *   <li>Al crear: se valida que haya saldo disponible (saldo - pendientes) y la evidencia si el tipo la exige.</li>
 *   <li>Al aprobar: se descuenta el saldo (movimiento CARGO).</li>
 *   <li>Al corregir de aprobado a rechazado: se devuelven los dias (movimiento REVERSION).</li>
 *   <li>Quien aprueba a quien se define en la tabla reglas_aprobacion.</li>
 * </ul>
 */
@Service
public class SolicitudService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitudRepository solicitudRepository;
    private final EmpleadoRepository empleadoRepository;
    private final TipoSolicitudRepository tipoSolicitudRepository;
    private final MotivoLicenciaRepository motivoLicenciaRepository;
    private final SolicitudEvidenciaRepository evidenciaRepository;
    private final SolicitudHistorialRepository historialRepository;
    private final ReglaAprobacionRepository reglaRepository;
    private final HorarioSemanalService horarioSemanalService;
    private final SaldoService saldoService;
    private final EvidenciaService evidenciaService;
    private final EmailService emailService;
    private final UsuarioActual usuarioActual;
    private final TransactionTemplate nuevaTransaccion;

    public SolicitudService(SolicitudRepository solicitudRepository,
                            EmpleadoRepository empleadoRepository,
                            TipoSolicitudRepository tipoSolicitudRepository,
                            MotivoLicenciaRepository motivoLicenciaRepository,
                            SolicitudEvidenciaRepository evidenciaRepository,
                            SolicitudHistorialRepository historialRepository,
                            ReglaAprobacionRepository reglaRepository,
                            HorarioSemanalService horarioSemanalService,
                            SaldoService saldoService,
                            EvidenciaService evidenciaService,
                            EmailService emailService,
                            UsuarioActual usuarioActual,
                            PlatformTransactionManager transactionManager) {
        this.solicitudRepository = solicitudRepository;
        this.empleadoRepository = empleadoRepository;
        this.tipoSolicitudRepository = tipoSolicitudRepository;
        this.motivoLicenciaRepository = motivoLicenciaRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.historialRepository = historialRepository;
        this.reglaRepository = reglaRepository;
        this.horarioSemanalService = horarioSemanalService;
        this.saldoService = saldoService;
        this.evidenciaService = evidenciaService;
        this.emailService = emailService;
        this.usuarioActual = usuarioActual;
        this.nuevaTransaccion = new TransactionTemplate(transactionManager);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ==================== PERMISOS ====================

    /** Si el empleado aprobador puede aprobar/rechazar solicitudes del solicitante (segun reglas_aprobacion). */
    public boolean puedeAprobar(Empleado aprobador, Empleado solicitante) {
        if (aprobador == null || solicitante == null || aprobador.getId().equals(solicitante.getId())) {
            return false;
        }
        String rolAprobador = aprobador.getRol();
        String rolSolicitante = solicitante.getRol();
        return rolAprobador != null && rolSolicitante != null
                && Boolean.TRUE.equals(aprobador.getUsuario().getActivo())
                && reglaRepository.puedeAprobar(rolSolicitante, rolAprobador);
    }

    /** Verifica si el empleado puede editar la solicitud (propia y pendiente). */
    public boolean puedeEditarSolicitud(Integer solicitudId, Integer empleadoId, String rolEmpleado) {
        return solicitudRepository.findById(solicitudId)
                .map(s -> s.getEmpleado().getId().equals(empleadoId) && Solicitud.PENDIENTE.equals(s.getEstado()))
                .orElse(false);
    }

    /** Verifica si el empleado puede gestionar (aprobar/rechazar) la solicitud. */
    public boolean puedeGestionarSolicitud(Integer solicitudId, Integer empleadoId, String rolEmpleado, String accion) {
        Optional<Solicitud> s = solicitudRepository.findById(solicitudId);
        Optional<Empleado> e = empleadoRepository.findById(empleadoId);
        return s.isPresent() && e.isPresent() && puedeAprobar(e.get(), s.get().getEmpleado());
    }

    // ==================== CREAR ====================

    @Transactional
    public SolicitudResponseDTO crearSolicitud(SolicitudRequestDTO request, MultipartFile archivo) {
        Empleado empleado = usuarioActual.empleadoRequerido();
        if (request.getEmpleadoId() != null && !request.getEmpleadoId().equals(empleado.getId())) {
            throw new AccesoDenegadoException("Solo puede registrar solicitudes a su nombre");
        }
        if (!Boolean.TRUE.equals(empleado.getUsuario().getTipoUsuario().getPuedeSolicitar())) {
            throw new AccesoDenegadoException("Su rol no registra solicitudes");
        }

        TipoSolicitud tipo = obtenerTipo(request.getTipo());
        validarFechas(request.getFechaInicio(), request.getFechaFin());
        BigDecimal dias = calcularDias(request.getFechaInicio(), request.getFechaFin());

        MotivoLicencia motivoLicencia = null;
        if (Boolean.TRUE.equals(tipo.getRequiereMotivoLicencia())) {
            if (request.getMotivoLicenciaId() == null) {
                throw new IllegalArgumentException("Debe indicar el motivo de la licencia");
            }
            motivoLicencia = motivoLicenciaRepository.findById(request.getMotivoLicenciaId())
                    .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                    .orElseThrow(() -> new IllegalArgumentException("Motivo de licencia no valido"));
        }
        if (Boolean.TRUE.equals(tipo.getRequiereEvidencia()) && (archivo == null || archivo.isEmpty())) {
            throw new IllegalArgumentException("Para " + tipo.getNombre().toLowerCase()
                    + " debe adjuntar la evidencia (foto o PDF)");
        }

        validarSinSolapamiento(empleado.getId(), request.getFechaInicio(), request.getFechaFin(), null);
        validarSaldoDisponible(empleado, tipo, dias, null);

        Solicitud solicitud = new Solicitud();
        solicitud.setEmpleado(empleado);
        solicitud.setTipoSolicitud(tipo);
        solicitud.setMotivoLicencia(motivoLicencia);
        solicitud.setFechaInicio(request.getFechaInicio());
        solicitud.setFechaFin(request.getFechaFin());
        solicitud.setDiasSolicitados(dias);
        solicitud.setMotivo(agregarNotaConflictos(empleado, request));
        solicitud.setEstado(Solicitud.PENDIENTE);
        solicitud = solicitudRepository.save(solicitud);

        if (archivo != null && !archivo.isEmpty()) {
            SolicitudEvidencia evidencia = evidenciaService.guardar(archivo, solicitud);
            limpiarArchivoSiFalla(evidencia.getNombreArchivo());
            solicitud.getEvidencias().add(evidenciaRepository.save(evidencia));
        }

        historialRepository.save(new SolicitudHistorial(solicitud, null, Solicitud.PENDIENTE,
                empleado.getUsuario(), "Solicitud registrada"));

        notificarNuevaSolicitud(empleado, solicitud);
        return new SolicitudResponseDTO(solicitud);
    }

    // ==================== GESTIONAR ====================

    /** Aprueba o rechaza (tambien corrige una decision previa). El aprobador es el usuario autenticado. */
    @Transactional
    public SolicitudResponseDTO gestionarSolicitud(Integer id, String estado, String comentarios) {
        Empleado aprobador = usuarioActual.empleadoRequerido();
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        if (solicitud.getEmpleado().getId().equals(aprobador.getId())) {
            throw new AccesoDenegadoException("No puede aprobar/rechazar su propia solicitud");
        }
        if (!puedeAprobar(aprobador, solicitud.getEmpleado())) {
            throw new AccesoDenegadoException("No tiene permisos para gestionar las solicitudes de este colaborador");
        }

        String nuevo = estado == null ? "" : estado.trim().toLowerCase();
        if (!Solicitud.APROBADO.equals(nuevo) && !Solicitud.RECHAZADO.equals(nuevo)) {
            throw new IllegalArgumentException("Estado inválido. Solo se permite 'aprobado' o 'rechazado'");
        }
        String anterior = solicitud.getEstado();
        if (nuevo.equals(anterior)) {
            throw new IllegalStateException("La solicitud ya se encuentra en estado " + nuevo);
        }

        TipoSaldo tipoSaldo = solicitud.getTipoSolicitud().getTipoSaldo();
        Usuario usuario = aprobador.getUsuario();
        if (tipoSaldo != null) {
            if (Solicitud.APROBADO.equals(nuevo)) {
                saldoService.registrar(solicitud.getEmpleado(), tipoSaldo, TipoMovimiento.CARGO,
                        solicitud.getDiasSolicitados().negate(), Origen.SOLICITUD,
                        "Solicitud #" + solicitud.getId() + " aprobada (" + rango(solicitud) + ")",
                        usuario, solicitud, null, null, false);
            } else if (Solicitud.APROBADO.equals(anterior)) {
                saldoService.registrar(solicitud.getEmpleado(), tipoSaldo, TipoMovimiento.REVERSION,
                        solicitud.getDiasSolicitados(), Origen.SOLICITUD,
                        "Solicitud #" + solicitud.getId() + " cambiada de aprobada a rechazada",
                        usuario, solicitud, null, null, true);
            }
        }

        solicitud.setEstado(nuevo);
        solicitud.setAprobadoPor(aprobador);
        solicitud.setFechaAprobacion(LocalDateTime.now());
        if (comentarios != null && !comentarios.isBlank()) {
            solicitud.setComentarioGestion(comentarios.trim());
        }
        Solicitud guardada = solicitudRepository.save(solicitud);
        historialRepository.save(new SolicitudHistorial(guardada, anterior, nuevo, usuario, comentarios));

        // El horario semanal se actualiza cuando la transaccion se confirma
        Integer solicitudId = guardada.getId();
        boolean aplicar = Solicitud.APROBADO.equals(nuevo);
        boolean revertir = Solicitud.APROBADO.equals(anterior);
        despuesDeConfirmar(() -> {
            try {
                nuevaTransaccion.executeWithoutResult(status -> {
                    if (aplicar) {
                        solicitudRepository.findById(solicitudId).ifPresent(horarioSemanalService::aplicarSolicitudAprobada);
                    } else if (revertir) {
                        horarioSemanalService.revertirSolicitud(solicitudId);
                    }
                });
            } catch (Exception e) {
                logger.error("Error al actualizar horarios semanales: {}", e.getMessage());
            }
        });

        notificarGestionSolicitud(guardada, aprobador, comentarios);
        return new SolicitudResponseDTO(guardada);
    }

    // ==================== EDITAR / ELIMINAR ====================

    /** Edita fechas o motivo de una solicitud propia y pendiente. */
    @Transactional
    public SolicitudResponseDTO editarSolicitud(Integer id, Map<String, Object> payload) {
        Empleado editor = usuarioActual.empleadoRequerido();
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (!solicitud.getEmpleado().getId().equals(editor.getId()) || !Solicitud.PENDIENTE.equals(solicitud.getEstado())) {
            throw new AccesoDenegadoException("Solo puede editar sus solicitudes pendientes");
        }
        if (payload.containsKey("tipo") && payload.get("tipo") != null
                && !String.valueOf(payload.get("tipo")).equalsIgnoreCase(solicitud.getTipo())) {
            throw new IllegalArgumentException("No se puede cambiar el tipo. Elimine la solicitud y registre una nueva");
        }

        LocalDate inicio = payload.containsKey("fechaInicio")
                ? LocalDate.parse(String.valueOf(payload.get("fechaInicio"))) : solicitud.getFechaInicio();
        LocalDate fin = payload.containsKey("fechaFin")
                ? LocalDate.parse(String.valueOf(payload.get("fechaFin"))) : solicitud.getFechaFin();
        validarFechas(inicio, fin);
        BigDecimal dias = calcularDias(inicio, fin);
        validarSinSolapamiento(editor.getId(), inicio, fin, solicitud.getId());
        validarSaldoDisponible(editor, solicitud.getTipoSolicitud(), dias, solicitud.getId());

        solicitud.setFechaInicio(inicio);
        solicitud.setFechaFin(fin);
        solicitud.setDiasSolicitados(dias);
        if (payload.containsKey("motivo")) {
            solicitud.setMotivo(payload.get("motivo") != null ? String.valueOf(payload.get("motivo")) : null);
        }
        Solicitud guardada = solicitudRepository.save(solicitud);
        historialRepository.save(new SolicitudHistorial(guardada, Solicitud.PENDIENTE, Solicitud.PENDIENTE,
                editor.getUsuario(), "Solicitud editada"));
        return new SolicitudResponseDTO(guardada);
    }

    /** Elimina una solicitud propia y pendiente (con sus archivos). */
    @Transactional
    public void eliminarSolicitud(Integer id) {
        Empleado empleado = usuarioActual.empleadoRequerido();
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (!solicitud.getEmpleado().getId().equals(empleado.getId())) {
            throw new AccesoDenegadoException("No tiene permisos para eliminar esta solicitud");
        }
        if (!Solicitud.PENDIENTE.equals(solicitud.getEstado())) {
            throw new IllegalStateException("Solo se pueden eliminar solicitudes pendientes");
        }
        List<String> archivos = solicitud.getEvidencias().stream().map(SolicitudEvidencia::getNombreArchivo).toList();
        solicitudRepository.delete(solicitud);
        despuesDeConfirmar(() -> archivos.forEach(evidenciaService::eliminarArchivo));
        logger.info("Solicitud {} eliminada por empleado {}", id, empleado.getId());
    }

    // ==================== EVIDENCIAS E HISTORIAL ====================

    /** Evidencia visible para el solicitante, sus aprobadores, gerencia y admin. */
    @Transactional(readOnly = true)
    public Map.Entry<SolicitudEvidencia, Resource> obtenerEvidencia(Integer solicitudId, Integer evidenciaId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        validarPuedeVer(solicitud);
        SolicitudEvidencia ev = evidenciaRepository.findByIdAndSolicitudId(evidenciaId, solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Evidencia no encontrada"));
        return Map.entry(ev, evidenciaService.cargar(ev));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(Integer solicitudId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        validarPuedeVer(solicitud);
        return historialRepository.findBySolicitudIdOrderByFechaAsc(solicitudId).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", h.getFecha());
            m.put("estadoAnterior", h.getEstadoAnterior());
            m.put("estadoNuevo", h.getEstadoNuevo());
            m.put("usuario", h.getUsuario() == null ? null
                    : h.getUsuario().getEmpleado() != null ? h.getUsuario().getEmpleado().getNombre() : h.getUsuario().getUsername());
            m.put("comentario", h.getComentario());
            return m;
        }).toList();
    }

    private void validarPuedeVer(Solicitud solicitud) {
        Usuario usuario = usuarioActual.requerido();
        if (Roles.esAdmin(usuario.getRol()) || Roles.esGerencia(usuario.getRol())) return;
        Empleado actual = usuario.getEmpleado();
        if (actual != null && (actual.getId().equals(solicitud.getEmpleado().getId())
                || puedeAprobar(actual, solicitud.getEmpleado()))) return;
        throw new AccesoDenegadoException("No tiene permisos para ver esta solicitud");
    }

    // ==================== CATALOGOS Y CONSULTAS ====================

    @Transactional(readOnly = true)
    public List<TipoSolicitud> tiposActivos() {
        return tipoSolicitudRepository.findByActivoTrue();
    }

    @Transactional(readOnly = true)
    public List<MotivoLicencia> motivosLicencia() {
        return motivoLicenciaRepository.findByActivoTrueOrderByNombreAsc();
    }

    /** Pendientes que el usuario autenticado puede aprobar. */
    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> obtenerPendientesPorAprobar() {
        Empleado aprobador = usuarioActual.empleadoRequerido();
        List<String> roles = reglaRepository.rolesQueAprueba(aprobador.getRol());
        if (roles.isEmpty()) return List.of();
        return solicitudRepository.findPendientesDeRoles(roles).stream()
                .filter(s -> !s.getEmpleado().getId().equals(aprobador.getId()))
                .map(SolicitudResponseDTO::new)
                .collect(Collectors.toList());
    }

    /** Verifica conflictos de fecha para un empleado. */
    public List<Solicitud> verificarConflictosFecha(Integer empleadoId, LocalDate fechaInicio, LocalDate fechaFin) {
        validarFechas(fechaInicio, fechaFin);
        return solicitudRepository.findConflictosPorRangoFechas(empleadoId, fechaInicio, fechaFin);
    }

    /** Verifica conflictos de fecha por rol. */
    public List<Solicitud> verificarConflictosPorRolYFechas(Integer empleadoId, String rolEmpleado, LocalDate fechaInicio, LocalDate fechaFin) {
        validarFechas(fechaInicio, fechaFin);
        return solicitudRepository.findConflictosPorRolYRangoFechas(empleadoId, rolEmpleado, fechaInicio, fechaFin);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> obtenerMisSolicitudes(Integer empleadoId) {
        return solicitudRepository.findByEmpleadoIdOrderByFechaSolicitudDesc(empleadoId)
                .stream().map(SolicitudResponseDTO::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> obtenerPendientes() {
        return solicitudRepository.findSolicitudesPendientes()
                .stream().map(SolicitudResponseDTO::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> obtenerTodas() {
        return solicitudRepository.findAll()
                .stream().map(SolicitudResponseDTO::new).collect(Collectors.toList());
    }

    /** Exporta solicitudes según el tipo de reporte. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportarSolicitudes(String tipoReporte, Integer empleadoId) {
        Map<String, Object> reporte = new HashMap<>();
        List<SolicitudResponseDTO> solicitudes;
        if ("mis-solicitudes".equals(tipoReporte) && empleadoId != null) {
            solicitudes = obtenerMisSolicitudes(empleadoId);
            reporte.put("titulo", "Mis Solicitudes");
        } else if ("pendientes".equals(tipoReporte)) {
            solicitudes = obtenerPendientes();
            reporte.put("titulo", "Solicitudes Pendientes");
        } else if ("historial".equals(tipoReporte)) {
            solicitudes = obtenerTodas().stream().filter(s -> !Solicitud.PENDIENTE.equals(s.getEstado())).toList();
            reporte.put("titulo", "Historial de Solicitudes");
        } else {
            solicitudes = obtenerTodas();
            reporte.put("titulo", "Todas las Solicitudes");
        }
        reporte.put("total", solicitudes.size());
        reporte.put("solicitudes", solicitudes);
        reporte.put("fecha_generacion", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return reporte;
    }

    // ==================== VALIDACIONES ====================

    private TipoSolicitud obtenerTipo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El tipo de solicitud es requerido");
        }
        return tipoSolicitudRepository.findByCodigo(codigo.trim().toLowerCase())
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new IllegalArgumentException("Tipo de solicitud no valido: " + codigo + ". Use uno de: "
                        + tipoSolicitudRepository.findByActivoTrue().stream().map(TipoSolicitud::getCodigo)
                                .collect(Collectors.joining(", "))));
    }

    private void validarFechas(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("Fechas requeridas");
        }
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }

    /** Dias calendario entre ambas fechas, incluidas. */
    public static BigDecimal calcularDias(LocalDate inicio, LocalDate fin) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(inicio, fin) + 1);
    }

    private void validarSinSolapamiento(Integer empleadoId, LocalDate inicio, LocalDate fin, Integer excluirId) {
        List<Solicitud> propias = solicitudRepository.findConflictosPorRangoFechas(empleadoId, inicio, fin).stream()
                .filter(s -> excluirId == null || !s.getId().equals(excluirId))
                .toList();
        if (!propias.isEmpty()) {
            Solicitud s = propias.get(0);
            throw new IllegalStateException("Ya tiene una solicitud " + s.getEstado() + " de " + s.getTipo()
                    + " en ese rango (" + rango(s) + ")");
        }
    }

    /** Bloquea la solicitud si no hay dias suficientes (saldo menos lo pendiente). */
    private void validarSaldoDisponible(Empleado empleado, TipoSolicitud tipo, BigDecimal dias, Integer excluirId) {
        TipoSaldo tipoSaldo = tipo.getTipoSaldo();
        if (tipoSaldo == null) return;
        BigDecimal disponible = saldoService.disponible(empleado.getId(), tipoSaldo, excluirId);
        if (disponible.compareTo(dias) < 0) {
            throw new IllegalStateException("No tiene " + SaldoService.nombre(tipoSaldo) + " suficientes. Disponible: "
                    + disponible.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + " dia(s), solicitados: " + dias.stripTrailingZeros().toPlainString() + " dia(s).");
        }
    }

    private String agregarNotaConflictos(Empleado empleado, SolicitudRequestDTO request) {
        String motivo = request.getMotivo();
        if (motivo != null && motivo.contains("CONFLICTO DE FECHAS:")) {
            motivo = motivo.split("CONFLICTO DE FECHAS:")[0].trim();
        }
        List<Solicitud> conflictos = verificarConflictosPorRolYFechas(
                empleado.getId(), empleado.getRol(), request.getFechaInicio(), request.getFechaFin());
        if (conflictos.isEmpty()) return motivo;

        StringBuilder sb = new StringBuilder(motivo == null ? "" : motivo);
        sb.append("\n\nCONFLICTO DE FECHAS: Existe(n) ").append(conflictos.size())
          .append(" solicitud(es) de compañeros del mismo rol en este período:\n");
        for (Solicitud c : conflictos) {
            sb.append("- ").append(c.getEmpleado().getNombre()).append(" (").append(c.getTipo()).append(": ")
              .append(rango(c)).append(")\n");
        }
        return sb.toString();
    }

    private static String rango(Solicitud s) {
        return s.getFechaInicio().format(DATE_FORMATTER) + " - " + s.getFechaFin().format(DATE_FORMATTER);
    }

    private void despuesDeConfirmar(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    /** Si la transaccion no se confirma, borra el archivo ya escrito en disco. */
    private void limpiarArchivoSiFalla(String nombreArchivo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        evidenciaService.eliminarArchivo(nombreArchivo);
                    }
                }
            });
        }
    }

    // ==================== NOTIFICACIONES ====================

    private void notificarNuevaSolicitud(Empleado empleado, Solicitud solicitud) {
        try {
            List<String> rolesAprobadores = reglaRepository.rolesAprobadores(empleado.getRol());
            if (rolesAprobadores.isEmpty()) return;
            String inicio = solicitud.getFechaInicio().format(DATE_FORMATTER);
            String fin = solicitud.getFechaFin().format(DATE_FORMATTER);
            for (Empleado aprobador : empleadoRepository.findActivosPorRoles(rolesAprobadores)) {
                if (aprobador.getEmail() != null && !aprobador.getEmail().isEmpty()) {
                    emailService.enviarNotificacionNuevaSolicitud(aprobador.getEmail(), aprobador.getNombre(),
                            empleado.getNombre(), solicitud.getTipo(), inicio, fin);
                }
            }
        } catch (Exception e) {
            logger.error("Error enviando notificaciones de nueva solicitud: {}", e.getMessage());
        }
    }

    private void notificarGestionSolicitud(Solicitud solicitud, Empleado aprobador, String comentarios) {
        try {
            Empleado empleado = solicitud.getEmpleado();
            if (empleado.getEmail() != null && !empleado.getEmail().isEmpty()) {
                emailService.enviarNotificacionSolicitud(empleado.getEmail(), empleado.getNombre(),
                        solicitud.getTipo(), solicitud.getEstado(), comentarios,
                        solicitud.getFechaInicio().format(DATE_FORMATTER), solicitud.getFechaFin().format(DATE_FORMATTER),
                        aprobador != null ? aprobador.getNombre() : "Sistema");
            }
        } catch (Exception e) {
            logger.error("Error enviando notificación de gestión: {}", e.getMessage());
        }
    }
}
