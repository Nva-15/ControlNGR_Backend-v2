package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.RostroEmpleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.RostroEmpleadoRepository;
import com.example.ControlNGR.security.RostroException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reconocimiento facial. El navegador calcula el descriptor (128 valores, face-api.js) y el servidor
 * guarda las muestras y hace la comparacion; los descriptores registrados nunca salen del servidor.
 */
@Service
public class FacialService {

    private static final Logger logger = LoggerFactory.getLogger(FacialService.class);

    public static final String MARCACION_FACIAL_OBLIGATORIA = "MARCACION_FACIAL_OBLIGATORIA";
    public static final String UMBRAL_FACIAL = "UMBRAL_FACIAL";
    public static final String MUESTRAS_FACIALES = "MUESTRAS_FACIALES";

    static final int DIMENSION = 128;
    private static final int MIN_MUESTRAS = 3;
    private static final int MAX_MUESTRAS = 10;
    /** Las muestras de un mismo registro no pueden diferir mas que esto entre si. */
    private static final double MAX_DISTANCIA_ENTRE_MUESTRAS = 0.6;

    private final RostroEmpleadoRepository rostroRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ParametroService parametroService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FacialService(RostroEmpleadoRepository rostroRepository, EmpleadoRepository empleadoRepository,
                         ParametroService parametroService) {
        this.rostroRepository = rostroRepository;
        this.empleadoRepository = empleadoRepository;
        this.parametroService = parametroService;
    }

    /** Resultado de verificar un rostro al marcar. */
    public record Verificacion(String metodo, BigDecimal distancia) {
        static Verificacion manual() { return new Verificacion("manual", null); }
    }

    // ------------------------------------------------------------------ estado

    public boolean obligatorio() {
        return parametroService.booleano(MARCACION_FACIAL_OBLIGATORIA, true);
    }

    public double umbral() {
        return parametroService.decimal(UMBRAL_FACIAL, new BigDecimal("0.5")).doubleValue();
    }

    public int muestrasRequeridas() {
        int n = parametroService.entero(MUESTRAS_FACIALES, 5);
        return Math.max(MIN_MUESTRAS, Math.min(MAX_MUESTRAS, n));
    }

    public boolean estaRegistrado(Integer empleadoId) {
        return rostroRepository.countByEmpleadoId(empleadoId) > 0;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estado(Empleado empleado) {
        List<RostroEmpleado> muestras = rostroRepository.findByEmpleadoId(empleado.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("registrado", !muestras.isEmpty());
        r.put("muestras", muestras.size());
        r.put("registradoEl", muestras.stream().map(RostroEmpleado::getCreatedAt)
                .min(Comparator.naturalOrder()).orElse(null));
        r.put("consentimientoEl", empleado.getConsentimientoFacialAt());
        r.put("obligatorio", obligatorio());
        r.put("muestrasRequeridas", muestrasRequeridas());
        return r;
    }

    public List<Integer> empleadosRegistrados() {
        return rostroRepository.findEmpleadosRegistrados();
    }

    // ------------------------------------------------------------------ registro

    /**
     * Registra (o reemplaza) el rostro de un empleado.
     * @param propio true si el propio empleado se registra: solo puede hacerlo una vez; para repetirlo,
     *               su jefatura o el admin debe restablecerlo.
     */
    @Transactional
    public Map<String, Object> registrar(Empleado empleado, List<double[]> descriptores, boolean consentimiento,
                                         Usuario registradoPor, boolean propio) {
        if (!consentimiento) {
            throw new RostroException("CONSENTIMIENTO",
                    "Debe aceptar el uso del rostro para la marcación de asistencia", 400);
        }
        if (propio && estaRegistrado(empleado.getId())) {
            throw new RostroException("YA_REGISTRADO",
                    "Su rostro ya está registrado. Para registrarlo de nuevo, pida a su jefatura o al administrador que lo restablezca", 409);
        }
        if (descriptores == null || descriptores.size() < MIN_MUESTRAS || descriptores.size() > MAX_MUESTRAS) {
            throw new RostroException("DESCRIPTOR_INVALIDO",
                    "Se requieren entre " + MIN_MUESTRAS + " y " + MAX_MUESTRAS + " capturas del rostro", 400);
        }
        descriptores.forEach(FacialService::validarDescriptor);

        // Todas las capturas deben ser de la misma persona
        for (int i = 0; i < descriptores.size(); i++) {
            for (int j = i + 1; j < descriptores.size(); j++) {
                if (distancia(descriptores.get(i), descriptores.get(j)) > MAX_DISTANCIA_ENTRE_MUESTRAS) {
                    throw new RostroException("MUESTRAS_INCONSISTENTES",
                            "Las capturas no parecen de la misma persona. Repita el registro mirando a la cámara", 400);
                }
            }
        }

        // El mismo rostro no puede estar registrado en otra cuenta
        double umbral = umbral();
        for (RostroEmpleado otro : rostroRepository.findDeOtrosEmpleados(empleado.getId())) {
            double[] guardado = leer(otro);
            for (double[] d : descriptores) {
                if (distancia(d, guardado) <= umbral) {
                    logger.warn("Registro facial rechazado: el rostro de {} coincide con el de {}",
                            empleado.getNombre(), otro.getEmpleado().getNombre());
                    throw new RostroException("ROSTRO_DE_OTRO",
                            "Este rostro ya está registrado para otro colaborador", 409);
                }
            }
        }

        rostroRepository.eliminarDeEmpleado(empleado.getId());
        for (double[] d : descriptores) {
            rostroRepository.save(new RostroEmpleado(empleado, escribir(d), registradoPor));
        }
        empleado.setConsentimientoFacialAt(LocalDateTime.now());
        empleadoRepository.save(empleado);
        logger.info("Rostro registrado para {} ({} muestras) por {}", empleado.getNombre(), descriptores.size(),
                registradoPor != null ? registradoPor.getUsername() : "-");
        return estado(empleado);
    }

    /** Borra el rostro registrado para que el empleado pueda registrarlo de nuevo. */
    @Transactional
    public void eliminar(Empleado empleado) {
        rostroRepository.eliminarDeEmpleado(empleado.getId());
        empleado.setConsentimientoFacialAt(null);
        empleadoRepository.save(empleado);
        logger.info("Rostro restablecido para {}", empleado.getNombre());
    }

    // ------------------------------------------------------------------ verificacion

    /**
     * Verifica el rostro capturado al marcar. Si la marcacion facial no es obligatoria y el empleado no
     * tiene rostro registrado, se permite marcar sin rostro (metodo "manual").
     */
    @Transactional(readOnly = true)
    public Verificacion verificar(Empleado empleado, double[] descriptor) {
        List<RostroEmpleado> muestras = rostroRepository.findByEmpleadoId(empleado.getId());
        if (muestras.isEmpty()) {
            if (!obligatorio()) {
                return Verificacion.manual();
            }
            throw new RostroException("NO_REGISTRADO",
                    "Debe registrar su rostro en Mi perfil antes de marcar asistencia", 403);
        }
        if (descriptor == null) {
            throw new RostroException("FALTA_ROSTRO", "La marcación requiere reconocimiento facial", 400);
        }
        validarDescriptor(descriptor);

        double mejor = muestras.stream().mapToDouble(m -> distancia(descriptor, leer(m))).min().orElse(Double.MAX_VALUE);
        if (mejor > umbral()) {
            logger.warn("Rostro no coincide para {} (distancia {})", empleado.getNombre(), String.format("%.4f", mejor));
            throw new RostroException("NO_COINCIDE",
                    "El rostro no coincide con el registrado. Intente de nuevo con buena iluminación", 403);
        }
        return new Verificacion("facial", BigDecimal.valueOf(mejor).setScale(4, java.math.RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------ pruebas (panel admin)

    /** Resumen para el panel admin: quienes marcan asistencia y si tienen rostro registrado. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Map<Integer, List<RostroEmpleado>> porEmpleado = new HashMap<>();
        for (RostroEmpleado r : rostroRepository.findTodasConEmpleado()) {
            porEmpleado.computeIfAbsent(r.getEmpleado().getId(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> empleados = new ArrayList<>();
        for (Empleado e : empleadoRepository.findByActivo(true)) {
            if (e.getUsuario() == null || !Boolean.TRUE.equals(e.getUsuario().getTipoUsuario().getMarcaAsistencia())) {
                continue;
            }
            List<RostroEmpleado> muestras = porEmpleado.getOrDefault(e.getId(), List.of());
            Map<String, Object> fila = datosEmpleado(e);
            fila.put("registrado", !muestras.isEmpty());
            fila.put("muestras", muestras.size());
            fila.put("registradoEl", muestras.stream().map(RostroEmpleado::getCreatedAt)
                    .min(Comparator.naturalOrder()).orElse(null));
            empleados.add(fila);
        }
        empleados.sort(Comparator.comparing(f -> String.valueOf(f.get("nombre"))));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", empleados.size());
        r.put("registrados", empleados.stream().filter(f -> Boolean.TRUE.equals(f.get("registrado"))).count());
        r.put("umbral", umbral());
        r.put("obligatorio", obligatorio());
        r.put("muestrasRequeridas", muestrasRequeridas());
        r.put("empleados", empleados);
        return r;
    }

    /**
     * Prueba de marcacion sin registrar asistencia: compara el rostro capturado con todos los registrados
     * y, si se indica, con un empleado en particular.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> probarMarcacion(double[] descriptor, Integer empleadoId) {
        if (descriptor == null) {
            throw new RostroException("FALTA_ROSTRO", "No se recibió la captura del rostro", 400);
        }
        validarDescriptor(descriptor);
        double umbral = umbral();

        Map<Integer, Double> mejorPorEmpleado = new HashMap<>();
        Map<Integer, Empleado> empleados = new HashMap<>();
        for (RostroEmpleado r : rostroRepository.findTodasConEmpleado()) {
            Integer id = r.getEmpleado().getId();
            empleados.put(id, r.getEmpleado());
            mejorPorEmpleado.merge(id, distancia(descriptor, leer(r)), Math::min);
        }
        List<Map<String, Object>> candidatos = mejorPorEmpleado.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(5)
                .map(en -> resultado(empleados.get(en.getKey()), en.getValue(), umbral))
                .toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("umbral", umbral);
        r.put("registrados", mejorPorEmpleado.size());
        r.put("candidatos", candidatos);
        Map<String, Object> reconocido = candidatos.isEmpty() || !Boolean.TRUE.equals(candidatos.get(0).get("coincide"))
                ? null : candidatos.get(0);
        r.put("reconocido", reconocido);
        if (empleadoId != null) {
            Empleado objetivo = empleadoRepository.findById(empleadoId)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
            Double d = mejorPorEmpleado.get(empleadoId);
            Map<String, Object> o = d == null ? datosEmpleado(objetivo) : resultado(objetivo, d, umbral);
            o.put("registrado", d != null);
            r.put("objetivo", o);
        }
        return r;
    }

    /**
     * Prueba de registro sin guardar: revisa que las capturas sean consistentes y que el rostro
     * no pertenezca ya a otro colaborador.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> probarRegistro(List<double[]> descriptores) {
        if (descriptores == null || descriptores.size() < MIN_MUESTRAS || descriptores.size() > MAX_MUESTRAS) {
            throw new RostroException("DESCRIPTOR_INVALIDO",
                    "Se requieren entre " + MIN_MUESTRAS + " y " + MAX_MUESTRAS + " capturas del rostro", 400);
        }
        descriptores.forEach(FacialService::validarDescriptor);
        double dispersion = 0;
        for (int i = 0; i < descriptores.size(); i++) {
            for (int j = i + 1; j < descriptores.size(); j++) {
                dispersion = Math.max(dispersion, distancia(descriptores.get(i), descriptores.get(j)));
            }
        }
        boolean consistente = dispersion <= MAX_DISTANCIA_ENTRE_MUESTRAS;
        double umbral = umbral();

        Empleado duenio = null;
        double mejor = Double.MAX_VALUE;
        for (RostroEmpleado r : rostroRepository.findTodasConEmpleado()) {
            double[] guardado = leer(r);
            for (double[] d : descriptores) {
                double dist = distancia(d, guardado);
                if (dist < mejor) {
                    mejor = dist;
                    duenio = r.getEmpleado();
                }
            }
        }
        boolean yaRegistrado = duenio != null && mejor <= umbral;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("muestras", descriptores.size());
        r.put("dispersion", redondear(dispersion));
        r.put("dispersionMaxima", MAX_DISTANCIA_ENTRE_MUESTRAS);
        r.put("consistente", consistente);
        r.put("umbral", umbral);
        r.put("yaRegistradoPara", yaRegistrado ? resultado(duenio, mejor, umbral) : null);
        r.put("aceptable", consistente && !yaRegistrado);
        return r;
    }

    private Map<String, Object> datosEmpleado(Empleado e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empleadoId", e.getId());
        m.put("nombre", e.getNombre());
        m.put("cargo", e.getCargo());
        m.put("foto", e.getFoto());
        m.put("rol", e.getRol());
        m.put("departamento", e.getDepartamentoNombre());
        return m;
    }

    private Map<String, Object> resultado(Empleado e, double distancia, double umbral) {
        Map<String, Object> m = datosEmpleado(Objects.requireNonNull(e));
        m.put("distancia", redondear(distancia));
        m.put("coincide", distancia <= umbral);
        return m;
    }

    private static double redondear(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    // ------------------------------------------------------------------ utilidades

    static void validarDescriptor(double[] d) {
        if (d == null || d.length != DIMENSION) {
            throw new RostroException("DESCRIPTOR_INVALIDO", "Captura de rostro no válida", 400);
        }
        for (double v : d) {
            if (!Double.isFinite(v) || Math.abs(v) > 2) {
                throw new RostroException("DESCRIPTOR_INVALIDO", "Captura de rostro no válida", 400);
            }
        }
    }

    static double distancia(double[] a, double[] b) {
        double suma = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            suma += diff * diff;
        }
        return Math.sqrt(suma);
    }

    private String escribir(double[] d) {
        try {
            return objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el rostro", e);
        }
    }

    private double[] leer(RostroEmpleado r) {
        try {
            return objectMapper.readValue(r.getDescriptor(), double[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Rostro guardado dañado (id " + r.getId() + ")", e);
        }
    }
}
