-- ============================================================
-- Control NGR - Alertas de asistencia para jefaturas (indicadores, no sanciones)
-- Se configuran en Panel admin → Reportes de asistencia y se calculan sobre el resumen diario.
-- ============================================================
CREATE TABLE alerta_asistencia_config (
    tipo            VARCHAR(30) PRIMARY KEY,
    activa          TINYINT(1)  NOT NULL DEFAULT 1,
    umbral          INT         NOT NULL,
    periodo         VARCHAR(10) NOT NULL COMMENT 'MES (calendario) o SEMANA (lunes a domingo)',
    actualizado_en  DATETIME    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO alerta_asistencia_config (tipo, activa, umbral, periodo) VALUES
 ('FALTAS',              1, 2,  'MES'),
 ('FALTAS_SEGUIDAS',     1, 2,  'MES'),
 ('TARDANZAS',           1, 3,  'MES'),
 ('MINUTOS_TARDE',       1, 60, 'MES'),
 ('SALIDAS_AUTOMATICAS', 1, 3,  'SEMANA');

-- Alertas detectadas: una por tipo, persona y período (se actualiza mientras el período sigue abierto)
CREATE TABLE alerta_asistencia (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo             VARCHAR(30)  NOT NULL,
    empleado_id      INT          NOT NULL,
    departamento_id  INT          NULL,
    periodo_clave    VARCHAR(10)  NOT NULL COMMENT 'AAAA-MM o AAAA-Wss',
    periodo_desde    DATE         NOT NULL,
    periodo_hasta    DATE         NOT NULL,
    valor            INT          NOT NULL,
    umbral           INT          NOT NULL,
    detalle          VARCHAR(255) NULL,
    creado_en        DATETIME     NOT NULL,
    actualizado_en   DATETIME     NOT NULL,
    CONSTRAINT uk_alerta UNIQUE (tipo, empleado_id, periodo_clave),
    CONSTRAINT fk_alerta_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    INDEX idx_alerta_periodo (periodo_clave),
    INDEX idx_alerta_depto (departamento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Hasta cuándo vio cada jefatura sus alertas (las posteriores cuentan como nuevas en el menú)
CREATE TABLE alerta_asistencia_lectura (
    usuario_id  INT      PRIMARY KEY,
    visto_en    DATETIME NOT NULL,
    CONSTRAINT fk_alerta_lectura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
