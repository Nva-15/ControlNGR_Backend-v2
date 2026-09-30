-- ============================================================
-- Control NGR - "+ Herramientas": accesos a las herramientas web del equipo
-- (Nagios, GLPI, etc.). Cada herramienta es visible para todos los roles o solo
-- para los roles elegidos. La crean y editan jefaturas, supervisores, gestor y admin.
-- ============================================================
CREATE TABLE herramientas (
    id               INT          AUTO_INCREMENT PRIMARY KEY,
    titulo           VARCHAR(100) NOT NULL,
    descripcion      VARCHAR(500) NULL,
    url              VARCHAR(500) NOT NULL,
    logo             MEDIUMTEXT   NULL COMMENT 'Logo PNG de hasta 128x128 en base64',
    todos_los_roles  BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por       VARCHAR(100) NULL,
    creado_en        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en   DATETIME     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE herramienta_roles (
    herramienta_id   INT         NOT NULL,
    rol              VARCHAR(20) NOT NULL COMMENT 'Codigo de tipos_usuario',
    PRIMARY KEY (herramienta_id, rol),
    CONSTRAINT fk_herramienta_roles FOREIGN KEY (herramienta_id) REFERENCES herramientas (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
