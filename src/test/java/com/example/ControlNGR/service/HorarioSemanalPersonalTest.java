package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.HorarioSemanalRequestDTO;
import com.example.ControlNGR.dto.HorarioSemanalResponseDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Horario;
import com.example.ControlNGR.entity.HorarioSemanal;
import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.HorarioRepository;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import com.example.ControlNGR.repository.HorarioSemanalRepository;
import com.example.ControlNGR.repository.SolicitudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Al copiar una semana o con personal nuevo, nadie con horario queda fuera y no se arrastran ausencias puntuales. */
class HorarioSemanalPersonalTest {

    private static final LocalDate LUNES_ORIGEN = LocalDate.of(2026, 10, 19);
    private static final LocalDate LUNES_DESTINO = LocalDate.of(2026, 10, 26);

    private HorarioSemanalRepository semanaRepo;
    private HorarioSemanalDetalleRepository detalleRepo;
    private HorarioRepository horarioRepo;
    private EmpleadoRepository empleadoRepo;
    private SolicitudRepository solicitudRepo;
    private HorarioSemanalService servicio;

    /** Días guardados durante la prueba. */
    private final List<HorarioSemanalDetalle> guardados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        semanaRepo = mock(HorarioSemanalRepository.class);
        detalleRepo = mock(HorarioSemanalDetalleRepository.class);
        horarioRepo = mock(HorarioRepository.class);
        empleadoRepo = mock(EmpleadoRepository.class);
        solicitudRepo = mock(SolicitudRepository.class);
        servicio = new HorarioSemanalService();
        ReflectionTestUtils.setField(servicio, "horarioSemanalRepository", semanaRepo);
        ReflectionTestUtils.setField(servicio, "detalleRepository", detalleRepo);
        ReflectionTestUtils.setField(servicio, "horarioRepository", horarioRepo);
        ReflectionTestUtils.setField(servicio, "empleadoRepository", empleadoRepo);
        ReflectionTestUtils.setField(servicio, "solicitudRepository", solicitudRepo);

        when(detalleRepo.save(any())).thenAnswer(inv -> {
            HorarioSemanalDetalle d = inv.getArgument(0);
            if (!guardados.contains(d)) guardados.add(d);
            return d;
        });
        when(horarioRepo.findByEmpleadoIdAndDiaSemana(anyInt(), anyString())).thenReturn(Optional.empty());
        when(solicitudRepo.findSolicitudesAprobadasEnRango(any(), any())).thenReturn(List.of());
    }

    private static Empleado empleado(int id, String nombre) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setNombre(nombre);
        e.setActivo(true);
        return e;
    }

    private static HorarioSemanal semana(int id, LocalDate lunes) {
        HorarioSemanal s = new HorarioSemanal(lunes, lunes.plusDays(6), null);
        s.setId(id);
        return s;
    }

    private static HorarioSemanalDetalle dia(HorarioSemanal s, Empleado e, LocalDate fecha, String tipo, String origen,
                                             LocalTime entrada) {
        HorarioSemanalDetalle d = new HorarioSemanalDetalle(s, e, fecha);
        d.setTipoDia(tipo);
        d.setOrigenTipoDia(origen);
        d.setHoraEntrada(entrada);
        d.setHoraSalida(entrada == null ? null : entrada.plusHours(9));
        return d;
    }

    private void horarioBase(Empleado e, String diaSemana, LocalTime entrada) {
        Horario h = new Horario();
        h.setEmpleado(e);
        h.setDiaSemana(diaSemana);
        h.setHoraEntrada(entrada);
        h.setHoraSalida(entrada.plusHours(9));
        h.setTipoDia("normal");
        when(horarioRepo.findByEmpleadoIdAndDiaSemana(e.getId(), diaSemana)).thenReturn(Optional.of(h));
    }

    private HorarioSemanalDetalle guardado(Empleado e, LocalDate fecha) {
        return guardados.stream()
                .filter(d -> d.getEmpleado().getId().equals(e.getId()) && d.getFecha().equals(fecha))
                .findFirst().orElse(null);
    }

    /** Crea la semana destino copiando la semana origen con los días indicados. */
    private HorarioSemanalResponseDTO copiar(List<HorarioSemanalDetalle> diasOrigen, Empleado... personal) {
        HorarioSemanal origen = semana(1, LUNES_ORIGEN);
        when(semanaRepo.findById(1)).thenReturn(Optional.of(origen));
        when(semanaRepo.findByFechaInicioAndFechaFin(any(), any())).thenReturn(Optional.empty());
        when(semanaRepo.findSemanasSolapadas(any(), any())).thenReturn(List.of());
        when(semanaRepo.save(any())).thenAnswer(inv -> {
            HorarioSemanal s = inv.getArgument(0);
            s.setId(2);
            return s;
        });
        when(detalleRepo.findByHorarioSemanalIdOrdenado(1)).thenReturn(diasOrigen);
        when(detalleRepo.findByHorarioSemanalIdOrdenado(2)).thenAnswer(inv -> guardados);
        when(empleadoRepo.findEmpleadosConHorario()).thenReturn(List.of(personal));

        HorarioSemanalRequestDTO req = new HorarioSemanalRequestDTO();
        req.setFechaInicio(LUNES_DESTINO);
        req.setFechaFin(LUNES_DESTINO.plusDays(6));
        req.setCopiarDeId(1);
        return servicio.generarSemana(req);
    }

    @Test
    void alCopiarEntraElIngresoNuevoConSuHorarioBase() {
        Empleado ana = empleado(10, "Ana");
        Empleado nuevo = empleado(11, "Nuevo");
        HorarioSemanal origen = semana(1, LUNES_ORIGEN);
        List<HorarioSemanalDetalle> diasOrigen = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            diasOrigen.add(dia(origen, ana, LUNES_ORIGEN.plusDays(i), "normal", "manual", LocalTime.of(7, 0)));
        }
        horarioBase(nuevo, "lunes", LocalTime.of(8, 0));

        HorarioSemanalResponseDTO r = copiar(diasOrigen, ana, nuevo);

        assertEquals(1, r.getColaboradoresAgregados());
        assertEquals(14, guardados.size(), "7 días por cada uno de los 2 colaboradores");
        assertEquals(LocalTime.of(7, 0), guardado(ana, LUNES_DESTINO).getHoraEntrada(), "Ana conserva sus turnos");
        assertEquals(LocalTime.of(8, 0), guardado(nuevo, LUNES_DESTINO).getHoraEntrada(), "el nuevo usa su horario base");
        HorarioSemanalDetalle martesSinBase = guardado(nuevo, LUNES_DESTINO.plusDays(1));
        assertEquals(HorarioSemanalService.ORIGEN_SIN_HORARIO_BASE, martesSinBase.getOrigenTipoDia(),
                "sin horario base queda pendiente de completar");
        assertEquals(2, r.getEmpleados().size(), "ambos aparecen en la semana");
    }

    @Test
    void lasAusenciasPuntualesNoSeCopianPeroElDescansoSi() {
        Empleado ana = empleado(10, "Ana");
        HorarioSemanal origen = semana(1, LUNES_ORIGEN);
        List<HorarioSemanalDetalle> diasOrigen = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            diasOrigen.add(dia(origen, ana, LUNES_ORIGEN.plusDays(i), "normal", "manual", LocalTime.of(7, 0)));
        }
        diasOrigen.set(2, dia(origen, ana, LUNES_ORIGEN.plusDays(2), "vacaciones", "solicitud_aprobada", null));
        diasOrigen.set(3, dia(origen, ana, LUNES_ORIGEN.plusDays(3), "licencia", "manual", null)); // copia antigua
        diasOrigen.set(6, dia(origen, ana, LUNES_ORIGEN.plusDays(6), "descanso", "manual", null));
        horarioBase(ana, "miercoles", LocalTime.of(9, 0));
        horarioBase(ana, "jueves", LocalTime.of(9, 0));

        HorarioSemanalResponseDTO r = copiar(diasOrigen, ana);

        assertEquals(0, r.getColaboradoresAgregados());
        HorarioSemanalDetalle miercoles = guardado(ana, LUNES_DESTINO.plusDays(2));
        assertEquals("normal", miercoles.getTipoDia(), "las vacaciones de la semana pasada no se repiten");
        assertEquals(LocalTime.of(9, 0), miercoles.getHoraEntrada());
        assertNull(miercoles.getSolicitudRef());
        assertEquals("normal", guardado(ana, LUNES_DESTINO.plusDays(3)).getTipoDia());
        assertEquals("descanso", guardado(ana, LUNES_DESTINO.plusDays(6)).getTipoDia(), "el descanso semanal sí se copia");
    }

    @Test
    void seEmparejaPorDiaDeLaSemanaAunqueElOrigenEsteIncompleto() {
        Empleado ana = empleado(10, "Ana");
        HorarioSemanal origen = semana(1, LUNES_ORIGEN);
        // Ana entró el miércoles de la semana origen: solo tiene de miércoles a domingo
        List<HorarioSemanalDetalle> diasOrigen = new ArrayList<>();
        for (int i = 2; i < 7; i++) {
            diasOrigen.add(dia(origen, ana, LUNES_ORIGEN.plusDays(i), "normal", "manual", LocalTime.of(6 + i, 0)));
        }
        horarioBase(ana, "lunes", LocalTime.of(7, 30));

        copiar(diasOrigen, ana);

        assertEquals(LocalTime.of(7, 30), guardado(ana, LUNES_DESTINO).getHoraEntrada(), "lunes: horario base");
        assertEquals(LocalTime.of(8, 0), guardado(ana, LUNES_DESTINO.plusDays(2)).getHoraEntrada(), "miércoles con miércoles");
        assertEquals(LocalTime.of(12, 0), guardado(ana, LUNES_DESTINO.plusDays(6)).getHoraEntrada(), "domingo con domingo");
    }

    @Test
    void elIngresoNuevoEntraALaSemanaVigenteDesdeHoyYALasFuturas() {
        Empleado nuevo = empleado(11, "Nuevo");
        HorarioSemanal vigente = semana(2, LUNES_DESTINO);
        HorarioSemanal siguiente = semana(3, LUNES_DESTINO.plusDays(7));
        LocalDate miercoles = LUNES_DESTINO.plusDays(2);
        when(semanaRepo.findNoTerminadasDesde(miercoles)).thenReturn(List.of(vigente, siguiente));
        // Ya tenía el viernes de la semana vigente (no se duplica)
        List<Object[]> existentes = new ArrayList<>();
        existentes.add(new Object[]{11, LUNES_DESTINO.plusDays(4)});
        when(detalleRepo.findEmpleadoYFechaDesde(2, miercoles)).thenReturn(existentes);
        when(detalleRepo.findEmpleadoYFechaDesde(3, siguiente.getFechaInicio())).thenReturn(List.of());

        int agregados = servicio.incorporar(List.of(nuevo), miercoles);

        assertEquals(4 + 7, agregados, "vigente: mié, jue, sáb y dom; siguiente: los 7 días");
        assertNull(guardado(nuevo, LUNES_DESTINO), "los días pasados no se tocan");
        assertNull(guardado(nuevo, LUNES_DESTINO.plusDays(1)));
        assertNotNull(guardado(nuevo, miercoles));
        assertNull(guardado(nuevo, LUNES_DESTINO.plusDays(4)), "el día que ya existía no se duplica");
        assertNotNull(guardado(nuevo, siguiente.getFechaInicio()));
    }

    @Test
    void noSeIncorporaAInactivosNiARolesSinHorario() {
        Empleado inactivo = empleado(12, "Inactivo");
        inactivo.setActivo(false);
        assertEquals(0, servicio.incorporarEmpleado(inactivo));
        verifyNoInteractions(semanaRepo);
    }

    @Test
    void alRegistrarElHorarioBaseSeCompletanLosDiasPendientes() {
        Empleado nuevo = empleado(11, "Nuevo");
        HorarioSemanal vigente = semana(2, LUNES_DESTINO);
        HorarioSemanalDetalle pendiente = dia(vigente, nuevo, LUNES_DESTINO.plusDays(3), "normal",
                HorarioSemanalService.ORIGEN_SIN_HORARIO_BASE, null);
        when(detalleRepo.findByEmpleadoIdAndFechaGreaterThanEqualAndOrigenTipoDia(eq(11), any(),
                eq(HorarioSemanalService.ORIGEN_SIN_HORARIO_BASE))).thenReturn(List.of(pendiente));
        horarioBase(nuevo, "jueves", LocalTime.of(8, 0));

        servicio.completarDiasSinHorarioBase(11);

        assertEquals(LocalTime.of(8, 0), pendiente.getHoraEntrada());
        assertEquals("manual", pendiente.getOrigenTipoDia());
    }
}
