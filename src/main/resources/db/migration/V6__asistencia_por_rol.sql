-- ============================================================
-- Control NGR - Asistencia y horarios configurables por rol (panel admin)
--   marca_asistencia : el rol marca entrada y salida.
--   con_horario      : el rol trabaja con horario: aparece en Horarios y en el reporte
--                      de asistencia, y se le calculan tardanzas. Si marca sin horario,
--                      tiene horario flexible (marca cualquier dia).
-- Reemplaza la seleccion de personal por empleado (V5) y la regla fija de
-- director/gerente/jefe sin horario.
-- ============================================================
ALTER TABLE tipos_usuario
    ADD COLUMN con_horario TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Tiene horario y aparece en el reporte de asistencia' AFTER marca_asistencia;

-- Valores iniciales: igual que hasta ahora (director, gerente y jefe con horario flexible)
UPDATE tipos_usuario SET con_horario = 0
 WHERE codigo IN ('admin', 'director', 'gerente', 'jefe') OR marca_asistencia = 0;

ALTER TABLE empleados DROP COLUMN en_reporte_asistencia;
