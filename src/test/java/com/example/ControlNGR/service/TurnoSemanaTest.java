package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.TurnoSemanaDTO;
import com.example.ControlNGR.dto.TurnoSemanaDTO.Dia;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.FuncionTurno;
import com.example.ControlNGR.entity.HorarioSemanal;
import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.example.ControlNGR.entity.DiaFeriado;
import com.example.ControlNGR.repository.DiaFeriadoRepository;
import com.example.ControlNGR.repository.FuncionTurnoRepository;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import com.example.ControlNGR.repository.HorarioSemanalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Armar la semana de una persona en un paso: cada día con su turno y funciones, máximo un descanso. */
class TurnoSemanaTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalTime H8 = LocalTime.of(8, 30);
    private static final LocalTime H17 = LocalTime.of(17, 30);

    private HorarioSemanalRepository semanaRepo;
    private HorarioSemanalDetalleRepository detalleRepo;
    private FuncionTurnoService funciones;
    private HorarioSemanalService servicio;
    private HorarioSemanal semana;
    private List<HorarioSemanalDetalle> dias;

    private static FuncionTurno funcion(String codigo, int orden, boolean activo) {
        FuncionTurno f = new FuncionTurno();
        f.setCodigo(codigo);
        f.setNombre(codigo);
        f.setOrden(orden);
        f.setActivo(activo);
        return f;
    }

    @BeforeEach
    void preparar() {
        semanaRepo = mock(HorarioSemanalRepository.class);
        detalleRepo = mock(HorarioSemanalDetalleRepository.class);
        FuncionTurnoRepository funcionRepo = mock(FuncionTurnoRepository.class);
        when(funcionRepo.findAllByOrderByOrdenAscCodigoAsc()).thenReturn(List.of(
                funcion("LT", 1, true), funcion("SP", 3, true), funcion("PRE", 4, true), funcion("XX", 9, false)));
        funciones = new FuncionTurnoService(funcionRepo);
        servicio = new HorarioSemanalService();
        ReflectionTestUtils.setField(servicio, "horarioSemanalRepository", semanaRepo);
        ReflectionTestUtils.setField(servicio, "detalleRepository", detalleRepo);
        ReflectionTestUtils.setField(servicio, "funcionTurnoService", funciones);
        DiaFeriadoRepository feriadoRepo = mock(DiaFeriadoRepository.class);
        DiaFeriado todosLosSantos = new DiaFeriado();
        todosLosSantos.setFecha(LUNES.plusDays(6));
        todosLosSantos.setDescripcion("Todos los Santos");
        todosLosSantos.setActivo(true);
        when(feriadoRepo.findByFechaAndActivoTrue(any())).thenReturn(Optional.empty());
        when(feriadoRepo.findByFechaAndActivoTrue(LUNES.plusDays(6))).thenReturn(Optional.of(todosLosSantos));
        when(feriadoRepo.findByFechaBetweenAndActivoTrueOrderByFechaAsc(any(), any())).thenReturn(List.of(todosLosSantos));
        ReflectionTestUtils.setField(servicio, "diaFeriadoRepository", feriadoRepo);

        semana = new HorarioSemanal(LUNES, LUNES.plusDays(6), null);
        semana.setId(5);
        Empleado jean = new Empleado();
        jean.setId(10);
        jean.setNombre("Jean Lopez");
        jean.setActivo(true);
        dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            HorarioSemanalDetalle d = new HorarioSemanalDetalle(semana, jean, LUNES.plusDays(i));
            d.setTipoDia("normal");
            dias.add(d);
        }
        when(semanaRepo.findById(5)).thenReturn(Optional.of(semana));
        when(detalleRepo.findByHorarioSemanalIdAndEmpleadoId(5, 10)).thenReturn(dias);
        when(detalleRepo.findByHorarioSemanalIdOrdenado(5)).thenReturn(dias);
    }

    private static Dia laboral(LocalDate f, String turno, LocalTime e, LocalTime s, List<String> funciones) {
        boolean manana = !"tarde".equals(turno);
        return new Dia(f.toString(), "normal", turno, e, s,
                manana ? e.plusHours(4) : null, manana ? e.plusHours(5) : null, funciones);
    }

    private static Dia descanso(LocalDate f) {
        return new Dia(f.toString(), "descanso", null, null, null, null, null, null);
    }

    @Test
    void validaCadaDia() {
        Dia sinHoras = new Dia("lunes", "normal", "manana", null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(sinHoras), false).validar());
        Dia igual = new Dia("lunes", "normal", "manana", H8, H8, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(igual), false).validar());
        Dia refrigerioAlReves = new Dia("lunes", "normal", "manana", H8, H17, LocalTime.of(13, 0), LocalTime.of(12, 0), null);
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(refrigerioAlReves), false).validar());
        Dia vacaciones = new Dia("lunes", "vacaciones", null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(vacaciones), false).validar());
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(), false).validar());
        Dia tarde = new Dia("lunes", "normal", "tarde", LocalTime.of(17, 0), LocalTime.of(1, 0), LocalTime.of(20, 0), null, null);
        assertDoesNotThrow(() -> new TurnoSemanaDTO(List.of(tarde), false).validar(), "tarde pasa la medianoche, sin refrigerio");
        assertNull(tarde.almuerzoInicio());
        assertThrows(IllegalArgumentException.class, () -> new TurnoSemanaDTO(List.of(tarde, tarde), false).validar(), "día repetido");
    }

    @Test
    void cadaDiaConSuTurnoYFunciones() {
        // Como Jean Lopez en el cuadro: 09:00-18:00 lun-mié (SP), descanso jue, tarde vie-sáb (LT), 08:00-17:00 dom (LT)
        LocalTime h9 = LocalTime.of(9, 0), h18 = LocalTime.of(18, 0), t17 = LocalTime.of(17, 0), t1 = LocalTime.of(1, 0);
        List<Dia> plan = List.of(
                laboral(LUNES, "manana", h9, h18, List.of("SP")),
                laboral(LUNES.plusDays(1), "manana", h9, h18, List.of("sp")),
                laboral(LUNES.plusDays(2), "manana", h9, h18, List.of("PRE", "SP")),
                descanso(LUNES.plusDays(3)),
                laboral(LUNES.plusDays(4), "tarde", t17, t1, List.of("LT")),
                laboral(LUNES.plusDays(5), "tarde", t17, t1, List.of("LT")),
                laboral(LUNES.plusDays(6), "manana", LocalTime.of(8, 0), LocalTime.of(17, 0), List.of("LT")));

        servicio.actualizarSemanaEmpleado(5, 10, new TurnoSemanaDTO(plan, false));

        assertEquals(h9, dias.get(0).getHoraEntrada());
        assertEquals("SP", dias.get(1).getFunciones(), "los códigos se normalizan en mayúsculas");
        assertEquals("SP,PRE", dias.get(2).getFunciones(), "en el orden del catálogo");
        assertEquals("descanso", dias.get(3).getTipoDia());
        assertNull(dias.get(3).getFunciones());
        assertEquals("tarde", dias.get(4).getTurno());
        assertEquals(t1, dias.get(4).getHoraSalida());
        assertNull(dias.get(4).getHoraAlmuerzoInicio(), "turno tarde sin refrigerio");
        assertEquals(LocalTime.of(8, 0), dias.get(6).getHoraEntrada());
    }

    @Test
    void soloUnDescansoPorSemana() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.actualizarSemanaEmpleado(5, 10,
                new TurnoSemanaDTO(List.of(descanso(LUNES.plusDays(3)), descanso(LUNES.plusDays(6))), false)));
        assertTrue(e.getMessage().contains("Solo se permite 1 día de descanso"));
        assertTrue(e.getMessage().contains("Jean Lopez"));

        // Ya tenía descanso el domingo: marcar el jueves también es el segundo
        dias.get(6).setTipoDia("descanso");
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizarSemanaEmpleado(5, 10,
                new TurnoSemanaDTO(List.of(descanso(LUNES.plusDays(3))), false)));

        // Vacaciones no cuentan como descanso
        dias.get(6).setTipoDia("vacaciones");
        assertDoesNotThrow(() -> servicio.actualizarSemanaEmpleado(5, 10,
                new TurnoSemanaDTO(List.of(descanso(LUNES.plusDays(3))), false)));
    }

    @Test
    void descansoFeriadoSoloEnFeriadosYNoCuentaComoDescanso() {
        Dia feriadoDomingo = new Dia(LUNES.plusDays(6).toString(), "descanso_feriado", null, null, null, null, null, null);
        // Descanso el jueves + descanso feriado el domingo (01/11 Todos los Santos): permitido
        var semanaDto = servicio.actualizarSemanaEmpleado(5, 10,
                new TurnoSemanaDTO(List.of(descanso(LUNES.plusDays(3)), feriadoDomingo), false));
        assertEquals("descanso_feriado", dias.get(6).getTipoDia());
        assertNull(dias.get(6).getHoraEntrada());
        assertEquals("Todos los Santos", semanaDto.getFeriados().get(LUNES.plusDays(6).toString()), "la semana informa sus feriados");

        // El lunes 26/10 no es feriado: se rechaza (eso es una compensación)
        Dia lunesNoFeriado = new Dia(LUNES.toString(), "descanso_feriado", null, null, null, null, null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizarSemanaEmpleado(5, 10, new TurnoSemanaDTO(List.of(lunesNoFeriado), false)));
        assertTrue(e.getMessage().contains("no es feriado"));
    }

    @Test
    void noTocaLosDiasDeSolicitudNiAceptaOtrasFechas() {
        HorarioSemanalDetalle vacaciones = dias.get(2);
        vacaciones.setTipoDia("vacaciones");
        vacaciones.setOrigenTipoDia("solicitud_aprobada");
        servicio.actualizarSemanaEmpleado(5, 10, new TurnoSemanaDTO(List.of(
                laboral(LUNES, "manana", H8, H17, null), laboral(LUNES.plusDays(2), "manana", H8, H17, null)), false));
        assertEquals(H8, dias.get(0).getHoraEntrada());
        assertEquals("vacaciones", vacaciones.getTipoDia());
        assertNull(vacaciones.getHoraEntrada());

        assertThrows(IllegalArgumentException.class, () -> servicio.actualizarSemanaEmpleado(5, 10,
                new TurnoSemanaDTO(List.of(laboral(LocalDate.of(2026, 12, 1), "manana", H8, H17, null)), false)));
    }

    @Test
    void funcionesDelCatalogo() {
        assertNull(funciones.normalizar(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> funciones.normalizar(List.of("ZZ"), null), "código inexistente");
        assertNull(funciones.normalizar(List.of("XX"), null), "una función inactiva no se puede asignar");
        assertEquals("XX", funciones.normalizar(List.of("XX"), "XX"), "pero se conserva si el día ya la tenía");
        assertEquals(List.of("SP", "PRE"), FuncionTurnoService.aLista("SP, PRE"));
    }

    @Test
    void validaJornadaYHoraSugeridaComoParametros() {
        var repo = mock(com.example.ControlNGR.repository.ParametroSistemaRepository.class);
        Map<String, String> tipos = Map.of(ParametroService.JORNADA_MANANA_HORAS, "NUMERO",
                ParametroService.ENTRADA_TARDE_SUGERIDA, "HORA");
        tipos.forEach((clave, tipo) -> {
            var p = new com.example.ControlNGR.entity.ParametroSistema();
            p.setClave(clave);
            p.setTipoDato(tipo);
            p.setValor("1");
            when(repo.findById(clave)).thenReturn(Optional.of(p));
        });
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ParametroService servicio = new ParametroService(repo);

        assertDoesNotThrow(() -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "8.5"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "8.3"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "20"));
        assertDoesNotThrow(() -> servicio.actualizar(ParametroService.ENTRADA_TARDE_SUGERIDA, "17:00"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.ENTRADA_TARDE_SUGERIDA, "25:00"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.ENTRADA_TARDE_SUGERIDA, "5pm"));
    }
}
