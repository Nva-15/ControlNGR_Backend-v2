-- ============================================================
-- Control NGR - Respaldos de la base de datos
-- Programación (una sola fila) e historial de respaldos generados.
-- Los archivos se guardan en la carpeta "respaldos" junto al docker-compose.yml.
-- ============================================================
CREATE TABLE respaldo_programacion (
    id               INT         NOT NULL PRIMARY KEY,
    frecuencia       VARCHAR(10) NOT NULL DEFAULT 'NINGUNA' COMMENT 'NINGUNA, DIARIA, SEMANAL o MENSUAL',
    hora             TIME        NOT NULL DEFAULT '02:00:00',
    dia_semana       INT         NOT NULL DEFAULT 1 COMMENT '1 = lunes ... 7 = domingo (frecuencia SEMANAL)',
    dia_mes          INT         NOT NULL DEFAULT 1 COMMENT '1 a 31; si el mes es más corto se usa su último día (MENSUAL)',
    conservar        INT         NOT NULL DEFAULT 10 COMMENT 'Respaldos automáticos que se conservan',
    incluir_archivos BOOLEAN     NOT NULL DEFAULT TRUE COMMENT 'Incluir fotos y evidencias en el respaldo',
    actualizado_en   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por  VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO respaldo_programacion (id) VALUES (1);

CREATE TABLE respaldos (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    archivo          VARCHAR(120) NOT NULL,
    tipo             VARCHAR(12)  NOT NULL COMMENT 'MANUAL o PROGRAMADO',
    estado           VARCHAR(12)  NOT NULL COMMENT 'EN_CURSO, COMPLETADO o FALLIDO',
    incluye_archivos BOOLEAN      NOT NULL DEFAULT FALSE,
    tamano_bytes     BIGINT       NULL,
    iniciado_en      DATETIME     NOT NULL,
    finalizado_en    DATETIME     NULL,
    mensaje          VARCHAR(500) NULL,
    creado_por       VARCHAR(100) NULL,
    INDEX idx_respaldos_iniciado (iniciado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
