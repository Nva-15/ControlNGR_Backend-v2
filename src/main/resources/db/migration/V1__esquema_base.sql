-- ============================================================
-- Control NGR - V1: esquema base
-- Flyway ejecuta este script una sola vez al iniciar el sistema
-- sobre una base de datos vacia.
-- ============================================================

-- ------------------------------------------------------------
-- SEGURIDAD: tipos de usuario (roles) y reglas de aprobacion
-- ------------------------------------------------------------
CREATE TABLE tipos_usuario (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL,
    nombre           VARCHAR(60)  NOT NULL,
    descripcion      VARCHAR(255) NULL,
    nivel_jerarquia  INT          NOT NULL DEFAULT 0 COMMENT 'Mayor numero = mayor rango',
    puede_solicitar  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'Puede registrar solicitudes',
    marca_asistencia TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'Tiene horario y marca asistencia',
    es_sistema       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'Rol tecnico (admin), no es personal',
    activo           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_tipos_usuario_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reglas_aprobacion (
    id                          INT AUTO_INCREMENT PRIMARY KEY,
    tipo_usuario_solicitante_id INT        NOT NULL,
    tipo_usuario_aprobador_id   INT        NOT NULL,
    activo                      TINYINT(1) NOT NULL DEFAULT 1,
    created_at                  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_regla_aprobacion UNIQUE (tipo_usuario_solicitante_id, tipo_usuario_aprobador_id),
    CONSTRAINT fk_regla_solicitante FOREIGN KEY (tipo_usuario_solicitante_id) REFERENCES tipos_usuario (id),
    CONSTRAINT fk_regla_aprobador   FOREIGN KEY (tipo_usuario_aprobador_id)   REFERENCES tipos_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- ORGANIZACION: departamentos y empleados
-- ------------------------------------------------------------
CREATE TABLE departamentos (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    descripcion    VARCHAR(255) NULL,
    responsable_id INT          NULL COMMENT 'Empleado a cargo del area',
    activo         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_departamentos_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE empleados (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    dni             VARCHAR(20)  NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    cargo           VARCHAR(100) NULL,
    departamento_id INT          NULL,
    nivel           VARCHAR(20)  NULL,
    descripcion     TEXT         NULL,
    hobby           TEXT         NULL,
    cumpleanos      DATE         NULL,
    ingreso         DATE         NULL,
    foto            VARCHAR(255) NULL DEFAULT 'img/perfil.png',
    activo          TINYINT(1)   NOT NULL DEFAULT 1,
    identificador   VARCHAR(50)  NULL,
    email           VARCHAR(100) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_empleados_dni UNIQUE (dni),
    CONSTRAINT uk_empleados_identificador UNIQUE (identificador),
    CONSTRAINT fk_empleados_departamento FOREIGN KEY (departamento_id) REFERENCES departamentos (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE departamentos
    ADD CONSTRAINT fk_departamentos_responsable FOREIGN KEY (responsable_id) REFERENCES empleados (id) ON DELETE SET NULL;

-- Credenciales de acceso. Un empleado tiene un usuario; el admin no es empleado.
CREATE TABLE usuarios (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id           INT          NULL,
    username              VARCHAR(50)  NOT NULL,
    password              VARCHAR(255) NOT NULL COMMENT 'Hash BCrypt',
    tipo_usuario_id       INT          NOT NULL,
    activo                TINYINT(1)   NOT NULL DEFAULT 1,
    debe_cambiar_password TINYINT(1)   NOT NULL DEFAULT 1,
    ultimo_acceso         DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuarios_username UNIQUE (username),
    CONSTRAINT uk_usuarios_empleado UNIQUE (empleado_id),
    CONSTRAINT fk_usuarios_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuarios_tipo     FOREIGN KEY (tipo_usuario_id) REFERENCES tipos_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- CONFIGURACION DEL SISTEMA (panel admin)
-- ------------------------------------------------------------
CREATE TABLE parametros_sistema (
    clave       VARCHAR(60)  NOT NULL PRIMARY KEY,
    valor       VARCHAR(255) NOT NULL,
    tipo_dato   VARCHAR(20)  NOT NULL DEFAULT 'TEXTO' COMMENT 'TEXTO, NUMERO, BOOLEANO, FECHA',
    descripcion VARCHAR(255) NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Segmentos de red desde los que se permite marcar asistencia
CREATE TABLE segmentos_red (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    patron      VARCHAR(50)  NOT NULL COMMENT 'Ej: 10.92.104.% , 10.92.104.0/24 o IP exacta',
    descripcion VARCHAR(255) NULL,
    activo      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_segmentos_red_patron UNIQUE (patron)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- HORARIOS
-- ------------------------------------------------------------
CREATE TABLE horarios (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id          INT         NOT NULL,
    dia_semana           VARCHAR(20) NULL,
    hora_entrada         TIME        NULL,
    hora_salida          TIME        NULL,
    hora_almuerzo_inicio TIME        NULL,
    hora_almuerzo_fin    TIME        NULL,
    tipo_dia             VARCHAR(20) NULL,
    turno                VARCHAR(20) NULL,
    CONSTRAINT fk_horarios_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    INDEX idx_horarios_empleado_dia (empleado_id, dia_semana)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE horarios_semanales (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    fecha_inicio   DATE         NOT NULL,
    fecha_fin      DATE         NOT NULL,
    nombre         VARCHAR(100) NULL,
    estado         VARCHAR(20)  NULL COMMENT 'borrador, activo, historico',
    creado_por_id  INT          NULL,
    fecha_creacion DATETIME     NULL,
    CONSTRAINT fk_horarios_semanales_creador FOREIGN KEY (creado_por_id) REFERENCES empleados (id) ON DELETE SET NULL,
    INDEX idx_horarios_semanales_fechas (fecha_inicio, fecha_fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- SOLICITUDES
-- ------------------------------------------------------------
CREATE TABLE tipos_solicitud (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    codigo               VARCHAR(30)  NOT NULL,
    nombre               VARCHAR(80)  NOT NULL,
    descripcion          VARCHAR(255) NULL,
    descuenta_de         VARCHAR(20)  NOT NULL DEFAULT 'NINGUNO' COMMENT 'VACACIONES, COMPENSACION, NINGUNO',
    requiere_evidencia   TINYINT(1)   NOT NULL DEFAULT 0,
    requiere_motivo_licencia TINYINT(1) NOT NULL DEFAULT 0,
    tipo_dia_horario     VARCHAR(20)  NOT NULL COMMENT 'Tipo de dia que se aplica en el horario semanal',
    activo               TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uk_tipos_solicitud_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE motivos_licencia (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo      TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uk_motivos_licencia_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE solicitudes (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id        INT          NOT NULL,
    tipo_solicitud_id  INT          NOT NULL,
    motivo_licencia_id INT          NULL,
    fecha_solicitud    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_inicio       DATE         NOT NULL,
    fecha_fin          DATE         NOT NULL,
    dias_solicitados   DECIMAL(5,1) NOT NULL DEFAULT 0,
    motivo             TEXT         NULL,
    estado             VARCHAR(20)  NOT NULL DEFAULT 'pendiente' COMMENT 'pendiente, aprobado, rechazado',
    aprobado_por       INT          NULL,
    fecha_aprobacion   DATETIME     NULL,
    comentario_gestion TEXT         NULL,
    CONSTRAINT fk_solicitudes_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitudes_tipo     FOREIGN KEY (tipo_solicitud_id) REFERENCES tipos_solicitud (id),
    CONSTRAINT fk_solicitudes_motivo   FOREIGN KEY (motivo_licencia_id) REFERENCES motivos_licencia (id),
    CONSTRAINT fk_solicitudes_aprobador FOREIGN KEY (aprobado_por) REFERENCES empleados (id) ON DELETE SET NULL,
    INDEX idx_solicitudes_estado (estado),
    INDEX idx_solicitudes_empleado_fechas (empleado_id, fecha_inicio, fecha_fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Archivo de sustento (foto o PDF) de descansos medicos y licencias
CREATE TABLE solicitud_evidencias (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    solicitud_id    INT          NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    nombre_archivo  VARCHAR(255) NOT NULL COMMENT 'Nombre interno en el almacenamiento',
    content_type    VARCHAR(100) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    fecha_subida    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_evidencias_archivo UNIQUE (nombre_archivo),
    CONSTRAINT fk_evidencias_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Auditoria de cambios de estado (reemplaza las notas en el texto del motivo)
CREATE TABLE solicitud_historial (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    solicitud_id    INT         NOT NULL,
    estado_anterior VARCHAR(20) NULL,
    estado_nuevo    VARCHAR(20) NOT NULL,
    usuario_id      INT         NULL,
    comentario      TEXT        NULL,
    fecha           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_usuario   FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE horarios_semanales_detalle (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    horario_semanal_id   INT         NOT NULL,
    empleado_id          INT         NOT NULL,
    fecha                DATE        NOT NULL,
    dia_semana           VARCHAR(20) NULL,
    hora_entrada         TIME        NULL,
    hora_salida          TIME        NULL,
    hora_almuerzo_inicio TIME        NULL,
    hora_almuerzo_fin    TIME        NULL,
    tipo_dia             VARCHAR(20) NULL,
    turno                VARCHAR(20) NULL,
    origen_tipo_dia      VARCHAR(30) NULL,
    solicitud_ref_id     INT         NULL,
    CONSTRAINT fk_hsd_semana    FOREIGN KEY (horario_semanal_id) REFERENCES horarios_semanales (id) ON DELETE CASCADE,
    CONSTRAINT fk_hsd_empleado  FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_hsd_solicitud FOREIGN KEY (solicitud_ref_id) REFERENCES solicitudes (id) ON DELETE SET NULL,
    INDEX idx_hsd_empleado_fecha (empleado_id, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- ASISTENCIA
-- ------------------------------------------------------------
CREATE TABLE asistencia (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id       INT         NOT NULL,
    fecha             DATE        NOT NULL,
    hora_entrada      TIME        NULL,
    hora_salida       TIME        NULL,
    estado            VARCHAR(20) NULL,
    observaciones     TEXT        NULL,
    salida_automatica TINYINT(1)  NOT NULL DEFAULT 0,
    ip_entrada        VARCHAR(45) NULL,
    ip_salida         VARCHAR(45) NULL,
    CONSTRAINT uk_asistencia_empleado_fecha UNIQUE (empleado_id, fecha),
    CONSTRAINT fk_asistencia_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    INDEX idx_asistencia_fecha (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- FERIADOS, VACACIONES Y SALDOS
-- ------------------------------------------------------------
CREATE TABLE dias_feriados (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    fecha       DATE         NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    tipo        VARCHAR(20)  NOT NULL DEFAULT 'nacional',
    activo      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_dias_feriados_fecha UNIQUE (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Feriado trabajado por un empleado (se genera al marcar asistencia)
CREATE TABLE feriados_laborados (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id    INT          NOT NULL,
    feriado_id     INT          NOT NULL,
    asistencia_id  INT          NULL,
    fecha          DATE         NOT NULL,
    dias_otorgados DECIMAL(5,1) NOT NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'ABONADO' COMMENT 'ABONADO, REVERTIDO',
    observacion    VARCHAR(255) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feriado_laborado UNIQUE (empleado_id, feriado_id),
    CONSTRAINT fk_fl_empleado   FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_fl_feriado    FOREIGN KEY (feriado_id) REFERENCES dias_feriados (id),
    CONSTRAINT fk_fl_asistencia FOREIGN KEY (asistencia_id) REFERENCES asistencia (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un registro por cada año cumplido (30 dias de vacaciones)
CREATE TABLE periodos_vacacionales (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id       INT          NOT NULL,
    periodo_inicio    DATE         NOT NULL,
    periodo_fin       DATE         NOT NULL,
    fecha_adquisicion DATE         NOT NULL COMMENT 'Fecha en que cumple el año',
    dias_ganados      DECIMAL(5,1) NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_periodo_vacacional UNIQUE (empleado_id, periodo_inicio),
    CONSTRAINT fk_pv_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Saldo actual por empleado y tipo (se actualiza junto con cada movimiento)
CREATE TABLE saldos (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id INT          NOT NULL,
    tipo_saldo  VARCHAR(20)  NOT NULL COMMENT 'VACACIONES, COMPENSACION',
    saldo       DECIMAL(6,1) NOT NULL DEFAULT 0,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_saldos_empleado_tipo UNIQUE (empleado_id, tipo_saldo),
    CONSTRAINT fk_saldos_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Libro de movimientos: historial inmutable de todo lo que suma o resta dias
CREATE TABLE movimientos_saldo (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id           INT          NOT NULL,
    tipo_saldo            VARCHAR(20)  NOT NULL COMMENT 'VACACIONES, COMPENSACION',
    tipo_movimiento       VARCHAR(20)  NOT NULL COMMENT 'ABONO, CARGO, REVERSION, AJUSTE, CARGA_INICIAL',
    dias                  DECIMAL(6,1) NOT NULL COMMENT 'Positivo suma, negativo resta',
    saldo_resultante      DECIMAL(6,1) NOT NULL,
    origen                VARCHAR(30)  NOT NULL COMMENT 'FERIADO_LABORADO, PERIODO_VACACIONAL, SOLICITUD, PANEL_ADMIN',
    feriado_laborado_id   INT          NULL,
    periodo_vacacional_id INT          NULL,
    solicitud_id          INT          NULL,
    observacion           VARCHAR(255) NULL,
    usuario_id            INT          NULL COMMENT 'Usuario que origino el movimiento',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mov_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_mov_feriado  FOREIGN KEY (feriado_laborado_id) REFERENCES feriados_laborados (id) ON DELETE SET NULL,
    CONSTRAINT fk_mov_periodo  FOREIGN KEY (periodo_vacacional_id) REFERENCES periodos_vacacionales (id) ON DELETE SET NULL,
    CONSTRAINT fk_mov_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id) ON DELETE SET NULL,
    CONSTRAINT fk_mov_usuario  FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL,
    INDEX idx_mov_empleado_tipo (empleado_id, tipo_saldo, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- EVENTOS Y ENCUESTAS
-- ------------------------------------------------------------
CREATE TABLE eventos (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(200) NOT NULL,
    descripcion         TEXT         NULL,
    tipo_evento         VARCHAR(20)  NOT NULL COMMENT 'ENCUESTA, SI_NO, ASISTENCIA, INFORMATIVO',
    fecha_inicio        DATETIME     NOT NULL,
    fecha_fin           DATETIME     NULL,
    estado              VARCHAR(20)  NULL DEFAULT 'BORRADOR' COMMENT 'BORRADOR, ACTIVO, FINALIZADO, CANCELADO',
    roles_visibles      VARCHAR(255) NULL COMMENT 'Codigos de rol separados por coma',
    permite_comentarios TINYINT(1)   NULL DEFAULT 1,
    requiere_respuesta  TINYINT(1)   NULL DEFAULT 0,
    creado_por_id       INT          NOT NULL,
    fecha_creacion      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_eventos_creador FOREIGN KEY (creado_por_id) REFERENCES empleados (id),
    INDEX idx_eventos_estado (estado),
    INDEX idx_eventos_fecha_inicio (fecha_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opciones_evento (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    evento_id   INT          NOT NULL,
    texto_opcion VARCHAR(500) NOT NULL,
    orden       INT          NULL DEFAULT 0,
    CONSTRAINT fk_opciones_evento FOREIGN KEY (evento_id) REFERENCES eventos (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE respuestas_evento (
    id                      INT AUTO_INCREMENT PRIMARY KEY,
    evento_id               INT         NOT NULL,
    empleado_id             INT         NOT NULL,
    opcion_id               INT         NULL,
    respuesta_si_no         TINYINT(1)  NULL,
    confirmacion_asistencia VARCHAR(20) NULL,
    comentario              TEXT        NULL,
    fecha_respuesta         DATETIME    NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion     DATETIME    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_respuesta_evento_empleado UNIQUE (evento_id, empleado_id),
    CONSTRAINT fk_respuestas_evento   FOREIGN KEY (evento_id) REFERENCES eventos (id) ON DELETE CASCADE,
    CONSTRAINT fk_respuestas_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_respuestas_opcion   FOREIGN KEY (opcion_id) REFERENCES opciones_evento (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comentarios_evento (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    evento_id        INT      NOT NULL,
    empleado_id      INT      NOT NULL,
    comentario       TEXT     NOT NULL,
    fecha_comentario DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentarios_evento   FOREIGN KEY (evento_id) REFERENCES eventos (id) ON DELETE CASCADE,
    CONSTRAINT fk_comentarios_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
