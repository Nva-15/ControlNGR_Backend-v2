package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Departamento;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.MarcacionEquipo;
import com.example.ControlNGR.repository.DepartamentoRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.MarcacionEquipoRepository;
import com.example.ControlNGR.repository.RostroEmpleadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Modo de marcación por equipo: FACIAL (cámara y rostro registrado) o BOTON (confirmación, sin cámara).
 * Lo decide el equipo, no si la persona ya registró su rostro. Quien no tiene equipo configurado usa
 * el parámetro MARCACION_FACIAL_OBLIGATORIA.
 */
@Service
public class ModoMarcacionService {

    private static final Logger logger = LoggerFactory.getLogger(ModoMarcacionService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MarcacionEquipoRepository repository;
    private final DepartamentoRepository departamentoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final RostroEmpleadoRepository rostroRepository;
    private final ParametroService parametroService;

    public ModoMarcacionService(MarcacionEquipoRepository repository, DepartamentoRepository departamentoRepository,
                                EmpleadoRepository empleadoRepository, RostroEmpleadoRepository rostroRepository,
                                ParametroService parametroService) {
        this.repository = repository;
        this.departamentoRepository = departamentoRepository;
        this.empleadoRepository = empleadoRepository;
        this.rostroRepository = rostroRepository;
        this.parametroService = parametroService;
    }

    /** Modo de quienes no tienen equipo configurado. */
    public String modoPorDefecto() {
        return parametroService.booleano(FacialService.MARCACION_FACIAL_OBLIGATORIA, false)
                ? MarcacionEquipo.FACIAL : MarcacionEquipo.BOTON;
    }

    @Transactional(readOnly = true)
    public String modo(Empleado empleado) {
        Integer depto = empleado.getDepartamentoId();
        return Optional.ofNullable(depto).flatMap(repository::findByDepartamentoId)
                .map(MarcacionEquipo::getModo).orElseGet(this::modoPorDefecto);
    }

    /** Modo configurado de cada departamento (tabla pequeña: una consulta para toda una página). */
    @Transactional(readOnly = true)
    public Map<Integer, String> modosPorDepartamento() {
        Map<Integer, String> m = new HashMap<>();
        repository.findAll().forEach(x -> m.put(x.getDepartamento().getId(), x.getModo()));
        return m;
    }

    public boolean esFacial(Empleado empleado) {
        return MarcacionEquipo.FACIAL.equals(modo(empleado));
    }

    // ==================== PANEL ADMIN ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        Map<Integer, MarcacionEquipo> porDepto = new HashMap<>();
        repository.findAll().forEach(m -> porDepto.put(m.getDepartamento().getId(), m));
        // Conteos agrupados en la base (no se cargan los empleados)
        Map<Integer, int[]> conteo = new HashMap<>();
        for (Object[] f : empleadoRepository.contarMarcanPorDepartamento()) {
            if (f[0] == null) continue;
            conteo.put((Integer) f[0], new int[]{((Number) f[1]).intValue(), f[2] == null ? 0 : ((Number) f[2]).intValue()});
        }
        String porDefecto = modoPorDefecto();
        List<Map<String, Object>> lista = new ArrayList<>();
        departamentoRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()) || porDepto.containsKey(d.getId()))
                .sorted(Comparator.comparing(Departamento::getNombre))
                .forEach(d -> {
                    MarcacionEquipo m = porDepto.get(d.getId());
                    int[] c = conteo.getOrDefault(d.getId(), new int[2]);
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("departamentoId", d.getId());
                    fila.put("departamento", d.getNombre());
                    fila.put("configurado", m != null);
                    fila.put("modo", m != null ? m.getModo() : porDefecto);
                    fila.put("colaboradores", c[0]);
                    fila.put("conRostro", c[1]);
                    fila.put("actualizadoEn", m != null ? m.getActualizadoEn() : null);
                    fila.put("actualizadoPor", m != null ? m.getActualizadoPor() : null);
                    lista.add(fila);
                });
        return lista;
    }

    @Transactional
    public Map<String, Object> guardar(Integer departamentoId, String modo, String usuario) {
        String valor = modo == null ? "" : modo.trim().toUpperCase();
        if (!MarcacionEquipo.FACIAL.equals(valor) && !MarcacionEquipo.BOTON.equals(valor)) {
            throw new IllegalArgumentException("Modo de marcación: use FACIAL o BOTON");
        }
        Departamento depto = departamentoRepository.findById(departamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        MarcacionEquipo m = repository.findByDepartamentoId(departamentoId).orElseGet(() -> {
            MarcacionEquipo n = new MarcacionEquipo();
            n.setDepartamento(depto);
            return n;
        });
        String anterior = m.getId() != null ? m.getModo() : null;
        m.setModo(valor);
        m.setActualizadoEn(LocalDateTime.now(LIMA));
        m.setActualizadoPor(usuario);
        repository.save(m);
        logger.info("Modo de marcación de {}: {} -> {} (por {})", depto.getNombre(), anterior, valor, usuario);
        return listar().stream().filter(f -> departamentoId.equals(f.get("departamentoId"))).findFirst().orElseThrow();
    }
}
