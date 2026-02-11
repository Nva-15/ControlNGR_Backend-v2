package com.example.ControlNGR.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ControlNGR.dto.HorarioRequestDTO;
import com.example.ControlNGR.dto.HorarioResponseDTO;
import com.example.ControlNGR.dto.HorarioSemanalDTO;
import com.example.ControlNGR.dto.HorarioSemanalDTO.HorarioDiaDTO;
import com.example.ControlNGR.entity.Horario;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.repository.HorarioRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class HorarioService {

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    private static final List<String> DIAS_VALIDOS = Arrays.asList(
        "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"
    );

    private static final List<String> TIPOS_DIA_VALIDOS = Arrays.asList(
        "normal", "descanso", "compensado", "vacaciones"
    );

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public List<HorarioResponseDTO> obtenerTodos() {
        return horarioRepository.findAll().stream()
                .map(HorarioResponseDTO::new)
                .collect(Collectors.toList());
    }

    public HorarioResponseDTO obtenerPorId(Integer id) {
        Horario horario = horarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado con ID: " + id));
        return new HorarioResponseDTO(horario);
    }

    public List<HorarioResponseDTO> obtenerPorEmpleado(Integer empleadoId) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        return horarioRepository.findByEmpleado(empleado).stream()
                .map(HorarioResponseDTO::new)
                .collect(Collectors.toList());
    }

    // Vista consolidada de todos los empleados con sus horarios semanales
    // Los admin NO tienen horarios, solo supervisor, tecnico, hd, noc
    public List<HorarioSemanalDTO> obtenerVistaConsolidada(String filtroRol) {
        List<Empleado> empleados;

        // Admin no tiene horarios, se excluye automaticamente
        if (filtroRol != null && !filtroRol.isEmpty()) {
            // Si filtran por admin, retornar lista vacia
            if (filtroRol.equalsIgnoreCase("admin")) {
                return new ArrayList<>();
            }
            empleados = empleadoRepository.findEmpleadosConHorarioPorRol(filtroRol);
        } else {
            empleados = empleadoRepository.findEmpleadosConHorario();
        }

        List<HorarioSemanalDTO> resultado = new ArrayList<>();

        for (Empleado empleado : empleados) {
            HorarioSemanalDTO dto = new HorarioSemanalDTO(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getRol(),
                empleado.getCargo()
            );

            List<Horario> horarios = horarioRepository.findByEmpleadoId(empleado.getId());
            Map<String, HorarioDiaDTO> horariosSemana = new HashMap<>();

            // Inicializar todos los dias de lunes a domingo con null
            for (String dia : DIAS_VALIDOS) {
                horariosSemana.put(dia, null);
            }

            // Llenar con los horarios existentes
            for (Horario h : horarios) {
                String dia = h.getDiaSemana().toLowerCase();
                if (horariosSemana.containsKey(dia)) {
                    horariosSemana.put(dia, new HorarioDiaDTO(
                        h.getId(),
                        formatTime(h.getHoraEntrada()),
                        formatTime(h.getHoraSalida()),
                        formatTime(h.getHoraAlmuerzoInicio()),
                        formatTime(h.getHoraAlmuerzoFin()),
                        h.getTipoDia(),
                        h.getTurno()
                    ));
                }
            }

            dto.setHorariosSemana(horariosSemana);
            resultado.add(dto);
        }

        // Ordenar por nombre
        resultado.sort((a, b) -> a.getEmpleadoNombre().compareToIgnoreCase(b.getEmpleadoNombre()));

        return resultado;
    }

    // Vista semanal de un empleado individual
    public HorarioSemanalDTO obtenerHorarioSemanalEmpleado(Integer empleadoId) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        HorarioSemanalDTO dto = new HorarioSemanalDTO(
            empleado.getId(),
            empleado.getNombre(),
            empleado.getRol(),
            empleado.getCargo()
        );

        List<Horario> horarios = horarioRepository.findByEmpleadoId(empleado.getId());
        Map<String, HorarioDiaDTO> horariosSemana = new HashMap<>();

        for (String dia : DIAS_VALIDOS) {
            horariosSemana.put(dia, null);
        }

        for (Horario h : horarios) {
            String dia = h.getDiaSemana().toLowerCase();
            if (horariosSemana.containsKey(dia)) {
                horariosSemana.put(dia, new HorarioDiaDTO(
                    h.getId(),
                    formatTime(h.getHoraEntrada()),
                    formatTime(h.getHoraSalida()),
                    formatTime(h.getHoraAlmuerzoInicio()),
                    formatTime(h.getHoraAlmuerzoFin()),
                    h.getTipoDia(),
                    h.getTurno()
                ));
            }
        }

        dto.setHorariosSemana(horariosSemana);
        return dto;
    }

    // Aplicar el mismo horario a multiples dias
    @Transactional
    public List<HorarioResponseDTO> aplicarHorarioMultiplesDias(Integer empleadoId, List<String> dias, HorarioRequestDTO plantilla) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        if ("admin".equalsIgnoreCase(empleado.getRol())) {
            throw new RuntimeException("Los administradores no tienen horarios asignados");
        }

        List<HorarioResponseDTO> resultados = new ArrayList<>();

        for (String dia : dias) {
            String diaLower = dia.toLowerCase();
            validarDiaSemana(diaLower);

            Horario horario = horarioRepository.findByEmpleadoAndDiaSemana(empleado, diaLower)
                    .orElse(new Horario());

            horario.setEmpleado(empleado);
            horario.setDiaSemana(diaLower);
            horario.setHoraEntrada(plantilla.getHoraEntrada());
            horario.setHoraSalida(plantilla.getHoraSalida());
            horario.setTurno(plantilla.getTurno() != null ? plantilla.getTurno().toLowerCase() : "manana");
            if ("tarde".equalsIgnoreCase(plantilla.getTurno())) {
                horario.setHoraAlmuerzoInicio(null);
                horario.setHoraAlmuerzoFin(null);
            } else {
                horario.setHoraAlmuerzoInicio(plantilla.getHoraAlmuerzoInicio());
                horario.setHoraAlmuerzoFin(plantilla.getHoraAlmuerzoFin());
            }
            horario.setTipoDia(plantilla.getTipoDia() != null ? plantilla.getTipoDia().toLowerCase() : "normal");

            Horario guardado = horarioRepository.save(horario);
            resultados.add(new HorarioResponseDTO(guardado));
        }

        return resultados;
    }

    @Transactional
    public HorarioResponseDTO crearHorario(HorarioRequestDTO request) {
        validarDiaSemana(request.getDiaSemana());
        validarTipoDia(request.getTipoDia());

        Empleado empleado = empleadoRepository.findById(request.getEmpleadoId())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + request.getEmpleadoId()));

        // Validar que no sea admin (admin no tiene horarios)
        if ("admin".equalsIgnoreCase(empleado.getRol())) {
            throw new RuntimeException("Los administradores no tienen horarios asignados");
        }

        // Verificar que no exista un horario para el mismo dia
        horarioRepository.findByEmpleadoAndDiaSemana(empleado, request.getDiaSemana().toLowerCase())
                .ifPresent(h -> {
                    throw new RuntimeException("Ya existe un horario para " + empleado.getNombre() +
                            " el dia " + request.getDiaSemana());
                });

        Horario horario = new Horario();
        horario.setEmpleado(empleado);
        horario.setDiaSemana(request.getDiaSemana().toLowerCase());
        horario.setHoraEntrada(request.getHoraEntrada());
        horario.setHoraSalida(request.getHoraSalida());
        horario.setTurno(request.getTurno() != null ? request.getTurno().toLowerCase() : "manana");
        if ("tarde".equalsIgnoreCase(request.getTurno())) {
            horario.setHoraAlmuerzoInicio(null);
            horario.setHoraAlmuerzoFin(null);
        } else {
            horario.setHoraAlmuerzoInicio(request.getHoraAlmuerzoInicio());
            horario.setHoraAlmuerzoFin(request.getHoraAlmuerzoFin());
        }
        horario.setTipoDia(request.getTipoDia() != null ? request.getTipoDia().toLowerCase() : "normal");

        Horario guardado = horarioRepository.save(horario);
        return new HorarioResponseDTO(guardado);
    }

    @Transactional
    public HorarioResponseDTO crearOActualizarHorarioDia(Integer empleadoId, String diaSemana, HorarioRequestDTO request) {
        validarDiaSemana(diaSemana);
        validarTipoDia(request.getTipoDia());

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        // Validar que no sea admin (admin no tiene horarios)
        if ("admin".equalsIgnoreCase(empleado.getRol())) {
            throw new RuntimeException("Los administradores no tienen horarios asignados");
        }

        // Buscar si ya existe horario para ese dia
        Horario horario = horarioRepository.findByEmpleadoAndDiaSemana(empleado, diaSemana.toLowerCase())
                .orElse(new Horario());

        horario.setEmpleado(empleado);
        horario.setDiaSemana(diaSemana.toLowerCase());
        horario.setHoraEntrada(request.getHoraEntrada());
        horario.setHoraSalida(request.getHoraSalida());
        horario.setTurno(request.getTurno() != null ? request.getTurno().toLowerCase() : "manana");
        if ("tarde".equalsIgnoreCase(request.getTurno())) {
            horario.setHoraAlmuerzoInicio(null);
            horario.setHoraAlmuerzoFin(null);
        } else {
            horario.setHoraAlmuerzoInicio(request.getHoraAlmuerzoInicio());
            horario.setHoraAlmuerzoFin(request.getHoraAlmuerzoFin());
        }
        horario.setTipoDia(request.getTipoDia() != null ? request.getTipoDia().toLowerCase() : "normal");

        Horario guardado = horarioRepository.save(horario);
        return new HorarioResponseDTO(guardado);
    }

    @Transactional
    public List<HorarioResponseDTO> crearHorariosSemana(Integer empleadoId, HorarioRequestDTO plantilla) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        // Validar que no sea admin (admin no tiene horarios)
        if ("admin".equalsIgnoreCase(empleado.getRol())) {
            throw new RuntimeException("Los administradores no tienen horarios asignados");
        }

        // Eliminar horarios existentes del empleado
        horarioRepository.deleteByEmpleadoId(empleadoId);

        // Crear horarios para los 7 dias de la semana
        return DIAS_VALIDOS.stream().map(dia -> {
            Horario horario = new Horario();
            horario.setEmpleado(empleado);
            horario.setDiaSemana(dia);
            horario.setHoraEntrada(plantilla.getHoraEntrada());
            horario.setHoraSalida(plantilla.getHoraSalida());
            horario.setTurno(plantilla.getTurno() != null ? plantilla.getTurno().toLowerCase() : "manana");
            if ("tarde".equalsIgnoreCase(plantilla.getTurno())) {
                horario.setHoraAlmuerzoInicio(null);
                horario.setHoraAlmuerzoFin(null);
            } else {
                horario.setHoraAlmuerzoInicio(plantilla.getHoraAlmuerzoInicio());
                horario.setHoraAlmuerzoFin(plantilla.getHoraAlmuerzoFin());
            }
            horario.setTipoDia("normal");
            return new HorarioResponseDTO(horarioRepository.save(horario));
        }).collect(Collectors.toList());
    }

    @Transactional
    public HorarioResponseDTO actualizarHorario(Integer id, HorarioRequestDTO request) {
        Horario horario = horarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado con ID: " + id));

        if (request.getDiaSemana() != null) {
            validarDiaSemana(request.getDiaSemana());

            // Verificar que no exista otro horario para el mismo dia del mismo empleado
            horarioRepository.findByEmpleadoAndDiaSemana(horario.getEmpleado(), request.getDiaSemana().toLowerCase())
                    .ifPresent(h -> {
                        if (!h.getId().equals(id)) {
                            throw new RuntimeException("Ya existe un horario para el dia " + request.getDiaSemana());
                        }
                    });
            horario.setDiaSemana(request.getDiaSemana().toLowerCase());
        }

        if (request.getHoraEntrada() != null) {
            horario.setHoraEntrada(request.getHoraEntrada());
        }
        if (request.getHoraSalida() != null) {
            horario.setHoraSalida(request.getHoraSalida());
        }
        if (request.getHoraAlmuerzoInicio() != null) {
            horario.setHoraAlmuerzoInicio(request.getHoraAlmuerzoInicio());
        }
        if (request.getHoraAlmuerzoFin() != null) {
            horario.setHoraAlmuerzoFin(request.getHoraAlmuerzoFin());
        }
        if (request.getTipoDia() != null) {
            validarTipoDia(request.getTipoDia());
            horario.setTipoDia(request.getTipoDia().toLowerCase());
        }
        if (request.getTurno() != null) {
            horario.setTurno(request.getTurno().toLowerCase());
            if ("tarde".equalsIgnoreCase(request.getTurno())) {
                horario.setHoraAlmuerzoInicio(null);
                horario.setHoraAlmuerzoFin(null);
            }
        }

        Horario actualizado = horarioRepository.save(horario);
        return new HorarioResponseDTO(actualizado);
    }

    @Transactional
    public void eliminarHorario(Integer id) {
        if (!horarioRepository.existsById(id)) {
            throw new RuntimeException("Horario no encontrado con ID: " + id);
        }
        horarioRepository.deleteById(id);
    }

    @Transactional
    public void eliminarHorariosPorEmpleado(Integer empleadoId) {
        if (!empleadoRepository.existsById(empleadoId)) {
            throw new RuntimeException("Empleado no encontrado con ID: " + empleadoId);
        }
        horarioRepository.deleteByEmpleadoId(empleadoId);
    }

    private void validarDiaSemana(String diaSemana) {
        if (diaSemana == null || diaSemana.trim().isEmpty()) {
            throw new RuntimeException("El dia de la semana es requerido");
        }
        if (!DIAS_VALIDOS.contains(diaSemana.toLowerCase())) {
            throw new RuntimeException("Dia de semana invalido: " + diaSemana +
                    ". Valores validos: " + String.join(", ", DIAS_VALIDOS));
        }
    }

    private void validarTipoDia(String tipoDia) {
        if (tipoDia != null && !tipoDia.isEmpty() && !TIPOS_DIA_VALIDOS.contains(tipoDia.toLowerCase())) {
            throw new RuntimeException("Tipo de dia invalido: " + tipoDia +
                    ". Valores validos: " + String.join(", ", TIPOS_DIA_VALIDOS));
        }
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : null;
    }
}
