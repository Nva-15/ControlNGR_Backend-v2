package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.FuncionTurno;
import com.example.ControlNGR.repository.FuncionTurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Catálogo de funciones de turno y conversión de la lista de códigos que se guarda en cada día. */
@Service
public class FuncionTurnoService {

    private final FuncionTurnoRepository repository;

    public FuncionTurnoService(FuncionTurnoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FuncionTurno> listar() {
        return repository.findAllByOrderByOrdenAscCodigoAsc();
    }

    @Transactional
    public FuncionTurno guardar(Integer id, Map<String, Object> d) {
        FuncionTurno f = id == null ? new FuncionTurno()
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Función no encontrada"));
        if (d.get("codigo") != null) {
            String codigo = d.get("codigo").toString().trim().toUpperCase();
            if (!codigo.matches("[A-Z0-9]{1,10}")) {
                throw new IllegalArgumentException("El código debe tener de 1 a 10 letras o números, sin espacios (p. ej. SP)");
            }
            if (!codigo.equals(f.getCodigo()) && repository.existsByCodigoIgnoreCase(codigo)) {
                throw new IllegalArgumentException("Ya existe la función " + codigo);
            }
            f.setCodigo(codigo);
        }
        if (d.get("nombre") != null) f.setNombre(d.get("nombre").toString().trim());
        if (d.get("activo") != null) f.setActivo(Boolean.parseBoolean(d.get("activo").toString()));
        if (d.get("orden") != null) f.setOrden(Integer.parseInt(d.get("orden").toString()));
        if (f.getCodigo() == null || f.getNombre() == null || f.getNombre().isBlank()) {
            throw new IllegalArgumentException("El código y el nombre son requeridos");
        }
        if (id == null && d.get("orden") == null) {
            f.setOrden(listar().stream().mapToInt(FuncionTurno::getOrden).max().orElse(0) + 1);
        }
        return repository.save(f);
    }

    /**
     * Valida los códigos contra el catálogo y los deja en el orden del catálogo, sin repetir:
     * ["pre", "SP"] → "SP,PRE". Vacío → null. Se aceptan los ya guardados aunque la función esté inactiva.
     */
    @Transactional(readOnly = true)
    public String normalizar(Collection<String> codigos, String actuales) {
        if (codigos == null || codigos.isEmpty()) return null;
        Set<String> pedidos = new HashSet<>();
        for (String c : codigos) {
            if (c != null && !c.isBlank()) pedidos.add(c.trim().toUpperCase());
        }
        if (pedidos.isEmpty()) return null;
        Set<String> yaTenia = new HashSet<>(aLista(actuales));
        List<String> resultado = new ArrayList<>();
        for (FuncionTurno f : listar()) {
            if (pedidos.remove(f.getCodigo()) && (Boolean.TRUE.equals(f.getActivo()) || yaTenia.contains(f.getCodigo()))) {
                resultado.add(f.getCodigo());
            }
        }
        if (!pedidos.isEmpty()) {
            throw new IllegalArgumentException("Función de turno no válida: " + String.join(", ", pedidos));
        }
        return resultado.isEmpty() ? null : String.join(",", resultado);
    }

    /** "SP,PRE" → [SP, PRE]. */
    public static List<String> aLista(String funciones) {
        if (funciones == null || funciones.isBlank()) return List.of();
        return Arrays.stream(funciones.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
