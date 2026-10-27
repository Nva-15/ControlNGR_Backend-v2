-- ============================================================
-- Control NGR - Funciones de turno y entrada sugerida por turno
-- Cada día de un horario puede llevar una o más funciones (rol que cumple en ese turno),
-- por ejemplo "08:30 - 17:30 (SP) (PRE)". El catálogo se administra en el panel.
-- ============================================================
CREATE TABLE funciones_turno (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    codigo  VARCHAR(10)  NOT NULL,
    nombre  VARCHAR(60)  NOT NULL,
    activo  TINYINT(1)   NOT NULL DEFAULT 1,
    orden   INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_funciones_turno_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO funciones_turno (codigo, nombre, orden) VALUES
 ('LT',  'Líder técnico',          1),
 ('CM',  'Comunal',                2),
 ('SP',  'Soporte presencial',     3),
 ('PRE', 'Preparador de equipos',  4),
 ('BO',  'Back office',            5);

-- Códigos separados por coma (p. ej. "SP,PRE")
ALTER TABLE horarios_semanales_detalle ADD COLUMN funciones VARCHAR(100) NULL AFTER turno;
ALTER TABLE horarios ADD COLUMN funciones VARCHAR(100) NULL AFTER turno;

INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('ENTRADA_MANANA_SUGERIDA', '08:30', 'HORA', 'Hora de entrada que se propone al elegir turno mañana (se puede cambiar)'),
 ('ENTRADA_TARDE_SUGERIDA',  '17:00', 'HORA', 'Hora de entrada que se propone al elegir turno tarde (se puede cambiar)');
