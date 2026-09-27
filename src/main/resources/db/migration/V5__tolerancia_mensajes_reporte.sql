-- ============================================================
-- Control NGR - Tolerancia de 10 minutos, mensaje de justificacion al marcar
-- y seleccion del personal que aparece en el reporte de asistencia.
-- ============================================================

-- Tolerancia inicial de 10 minutos (solo si aun tiene el valor de fabrica; se edita en el panel admin)
UPDATE parametros_sistema SET valor = '10'
 WHERE clave = 'TOLERANCIA_TARDANZA_MINUTOS' AND valor = '5';

-- Breve justificacion que el empleado escribe despues de marcar (la ve su supervisor)
ALTER TABLE asistencia
    ADD COLUMN mensaje_entrada VARCHAR(300) NULL COMMENT 'Justificacion del empleado al marcar entrada' AFTER observaciones,
    ADD COLUMN mensaje_salida  VARCHAR(300) NULL COMMENT 'Justificacion del empleado al marcar salida' AFTER mensaje_entrada;

-- Personal que se incluye en el reporte de asistencia (se marca desde el panel admin)
ALTER TABLE empleados
    ADD COLUMN en_reporte_asistencia TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Aparece en el reporte de asistencia' AFTER activo;
