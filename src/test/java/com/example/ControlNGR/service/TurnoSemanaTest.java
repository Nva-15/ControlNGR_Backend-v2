package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.TurnoSemanaDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.HorarioSemanal;
import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import com.example.ControlNGR.repository.HorarioSemanalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Armar la semana de una persona en un paso: días laborales con el turno, descansos libres. */
class TurnoSemanaTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    private static TurnoSemanaDTO turno(String turno, LocalTime entrada, LocalTime salida,
                                        LocalTime almIni, LocalTime almFin, Map<String, String> dias) {
        return new TurnoSemanaDTO(turno, entrada, salida, almIni, almFin, dias, false);
    }

    @Test
    void validaDiasYHoras() {
        Map<String, String> dias = Map.of("lunes", "normal");
        assertThrows(IllegalArgumentException.class, () -> turno("manana", null, null, null, null, dias).validar());
        assertThrows(IllegalArgumentException.class,
                () -> turno("manana", LocalTime.of(8, 0), LocalTime.of(8, 0), null, null, dias).validar());
        assertThrows(IllegalArgumentException.class,
                () -> turno("manana", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(13, 0), LocalTime.of(12, 0), dias).validar());
        assertThrows(IllegalArgumentException.class,
                () -> turno("manana", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, Map.of("lunes", "vacaciones")).validar());
        assertThrows(IllegalArgumentException.class,
                () -> turno("manana", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, Map.of()).validar());
        // Turno tarde que pasa la medianoche y sin refrigerio
        assertDoesNotThrow(() -> turno("tarde", LocalTime.of(17, 0), LocalTime.of(1, 0), LocalTime.of(20, 0), null, dias).validar());
        // Solo descansos: no hacen falta horas
        assertDoesNotThrow(() -> turno("manana", null, null, null, null, Map.of("domingo", "descanso")).validar());
        TurnoSemanaDTO tarde = turno("tarde", LocalTime.of(14, 0), LocalTime.of(22, 0), LocalTime.of(18, 0), LocalTime.of(19, 0), dias);
        assertNull(tarde.almuerzoInicio(), "el turno tarde no tiene refrigerio");
    }

    @Test
    void armaLaSemanaSinTocarLosDiasDeSolicitud() {
        HorarioSemanalRepository semanaRepo = mock(HorarioSemanalRepository.class);
        HorarioSemanalDetalleRepository detalleRepo = mock(HorarioSemanalDetalleRepository.class);
        HorarioSemanalService servicio = new HorarioSemanalService();
        ReflectionTestUtils.setField(servicio, "horarioSemanalRepository", semanaRepo);
        ReflectionTestUtils.setField(servicio, "detalleRepository", detalleRepo);

        HorarioSemanal semana = new HorarioSemanal(LUNES, LUNES.plusDays(6), null);
        semana.setId(5);
        Empleado ana = new Empleado();
        ana.setId(10);
        ana.setNombre("Ana");
        ana.setActivo(true);
        List<HorarioSemanalDetalle> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dias.add(new HorarioSemanalDetalle(semana, ana, LUNES.plusDays(i)));
        }
        HorarioSemanalDetalle vacaciones = dias.get(2);
        vacaciones.setTipoDia("vacaciones");
        vacaciones.setOrigenTipoDia("solicitud_aprobada");
        when(semanaRepo.findById(5)).thenReturn(Optional.of(semana));
        when(detalleRepo.findByHorarioSemanalIdAndEmpleadoId(5, 10)).thenReturn(dias);
        when(detalleRepo.findByHorarioSemanalIdOrdenado(5)).thenReturn(dias);

        Map<String, String> plan = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            plan.put(LUNES.plusDays(i).toString(), i == 5 ? "descanso" : "normal");
        }
        servicio.actualizarSemanaEmpleado(5, 10, turno("manana", LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0), plan));

        HorarioSemanalDetalle lunes = dias.get(0);
        assertEquals("normal", lunes.getTipoDia());
        assertEquals(LocalTime.of(8, 0), lunes.getHoraEntrada());
        assertEquals(LocalTime.of(17, 0), lunes.getHoraSalida());
        assertEquals(LocalTime.of(12, 0), lunes.getHoraAlmuerzoInicio());
        assertEquals("manana", lunes.getTurno());
        assertEquals("manual", lunes.getOrigenTipoDia());
        HorarioSemanalDetalle sabado = dias.get(5);
        assertEquals("descanso", sabado.getTipoDia());
        assertNull(sabado.getHoraEntrada());
        assertEquals("vacaciones", vacaciones.getTipoDia(), "el día por solicitud aprobada no cambia");
        assertNull(vacaciones.getHoraEntrada());
        verify(detalleRepo, times(6)).save(any());
    }

    @Test
    void rechazaFechasQueNoSonDeLaSemana() {
        HorarioSemanalRepository semanaRepo = mock(HorarioSemanalRepository.class);
        HorarioSemanalDetalleRepository detalleRepo = mock(HorarioSemanalDetalleRepository.class);
        HorarioSemanalService servicio = new HorarioSemanalService();
        ReflectionTestUtils.setField(servicio, "horarioSemanalRepository", semanaRepo);
        ReflectionTestUtils.setField(servicio, "detalleRepository", detalleRepo);
        HorarioSemanal semana = new HorarioSemanal(LUNES, LUNES.plusDays(6), null);
        semana.setId(5);
        when(semanaRepo.findById(5)).thenReturn(Optional.of(semana));
        when(detalleRepo.findByHorarioSemanalIdAndEmpleadoId(5, 10)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> servicio.actualizarSemanaEmpleado(5, 10,
                turno("manana", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, Map.of("2026-12-01", "normal"))));
    }

    @Test
    void validaLaJornadaComoParametro() {
        var repo = mock(com.example.ControlNGR.repository.ParametroSistemaRepository.class);
        var p = new com.example.ControlNGR.entity.ParametroSistema();
        p.setClave(ParametroService.JORNADA_MANANA_HORAS);
        p.setTipoDato("NUMERO");
        p.setValor("9");
        when(repo.findById(ParametroService.JORNADA_MANANA_HORAS)).thenReturn(Optional.of(p));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ParametroService servicio = new ParametroService(repo);

        assertDoesNotThrow(() -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "8.5"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "8.3"));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(ParametroService.JORNADA_MANANA_HORAS, "20"));
    }
}
