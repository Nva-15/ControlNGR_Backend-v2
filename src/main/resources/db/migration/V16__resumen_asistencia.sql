-- ============================================================
-- Control NGR - Resumen diario de asistencia (dashboard y récord del colaborador)
-- Un registro por persona y día con el resultado frente a su horario programado. Lo calcula el sistema
-- (al iniciar, cada noche y durante el día); permite analizar años de historia en menos de un segundo.
-- ============================================================
CREATE TABLE asistencia_resumen_diario (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id          INT          NOT NULL,
    fecha                DATE         NOT NULL,
    departamento_id      INT          NULL COMMENT 'Equipo de la persona al calcular',
    rol                  VARCHAR(30)  NULL,
    estado               VARCHAR(20)  NOT NULL COMMENT 'a_tiempo, tardanza, falta, pendiente, permiso, asistio (sin horario) o el tipo de día no laborable',
    tipo_dia             VARCHAR(20)  NULL,
    turno                VARCHAR(20)  NULL,
    funciones            VARCHAR(100) NULL,
    entrada_programada   TIME         NULL,
    salida_programada    TIME         NULL,
    entrada_real         TIME         NULL,
    salida_real          TIME         NULL,
    minutos_diferencia   INT          NULL COMMENT 'Entrada real menos programada (negativo = llegó antes)',
    minutos_retraso      INT          NULL,
    minutos_programados  INT          NULL,
    minutos_trabajados   INT          NULL COMMENT 'Solo con salida marcada (no automática)',
    salida_automatica    TINYINT(1)   NOT NULL DEFAULT 0,
    metodo_entrada       VARCHAR(20)  NULL,
    calculado_en         DATETIME     NOT NULL,
    CONSTRAINT uk_resumen_empleado_fecha UNIQUE (empleado_id, fecha),
    CONSTRAINT fk_resumen_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    INDEX idx_resumen_fecha (fecha),
    INDEX idx_resumen_depto_fecha (departamento_id, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('MI_RECORD_HABILITADO', 'false', 'BOOLEANO',
     'Cada colaborador puede ver su propio récord de asistencia (solo sus datos, solo lectura)'),
 ('MI_RECORD_DESDE', 'INGRESO', 'TEXTO',
     'Desde cuándo ve su historial: INGRESO (desde su fecha de ingreso) o una fecha AAAA-MM-DD');
