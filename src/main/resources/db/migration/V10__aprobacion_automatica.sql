-- ============================================================
-- Control NGR - Aprobación automática de compensaciones por equipo
-- Cada departamento define su cupo semanal, el máximo por empleado, la cobertura mínima
-- por día y la antelación. Lo que no cumple las reglas queda en la sala de espera
-- (pendiente) para que lo decida el supervisor.
-- ============================================================
CREATE TABLE aprobacion_automatica (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    departamento_id      INT          NOT NULL,
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    cupo_semanal         INT          NOT NULL DEFAULT 4 COMMENT 'Días de compensación por semana (lunes a domingo) para todo el equipo',
    maximo_por_empleado  INT          NOT NULL DEFAULT 1 COMMENT 'Días de compensación por semana para cada empleado',
    minimo_en_turno      INT          NOT NULL DEFAULT 1 COMMENT 'Personas que deben quedar en turno cada día',
    antelacion_horas     INT          NOT NULL DEFAULT 48 COMMENT 'Con menos antelación se trata como urgencia (va al supervisor)',
    al_llenarse          VARCHAR(10)  NOT NULL DEFAULT 'ESPERA' COMMENT 'ESPERA o RECHAZO cuando el cupo del equipo está completo',
    promover_espera      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'Aprobar sola la primera en espera si se libera un cupo',
    actualizado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por      VARCHAR(100) NULL,
    CONSTRAINT uk_aprobacion_automatica_depto UNIQUE (departamento_id),
    CONSTRAINT fk_aprobacion_automatica_depto FOREIGN KEY (departamento_id) REFERENCES departamentos (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Valores acordados para empezar (se cambian desde el panel del administrador)
INSERT INTO aprobacion_automatica (departamento_id, cupo_semanal, maximo_por_empleado, minimo_en_turno, antelacion_horas, promover_espera)
SELECT id, 4, 1, 4, 48, TRUE FROM departamentos WHERE nombre = 'Soporte Técnico';
INSERT INTO aprobacion_automatica (departamento_id, cupo_semanal, maximo_por_empleado, minimo_en_turno, antelacion_horas, promover_espera)
SELECT id, 3, 1, 3, 48, TRUE FROM departamentos WHERE nombre = 'HD';
INSERT INTO aprobacion_automatica (departamento_id, cupo_semanal, maximo_por_empleado, minimo_en_turno, antelacion_horas, promover_espera)
SELECT id, 1, 1, 2, 72, FALSE FROM departamentos WHERE nombre = 'NOC';

-- Solicitudes: urgencia, motivo de espera y aprobación automática
ALTER TABLE solicitudes
    ADD COLUMN urgente               BOOLEAN      NOT NULL DEFAULT FALSE AFTER comentario_gestion,
    ADD COLUMN motivo_urgencia       VARCHAR(500) NULL AFTER urgente,
    ADD COLUMN motivo_espera         VARCHAR(300) NULL COMMENT 'Por qué no se aprobó sola (sala de espera)' AFTER motivo_urgencia,
    ADD COLUMN aprobacion_automatica BOOLEAN      NOT NULL DEFAULT FALSE AFTER motivo_espera;
