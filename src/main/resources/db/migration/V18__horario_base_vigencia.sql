-- ============================================================
-- Control NGR - Vigencia del horario base
-- El horario base solo programa días desde su fecha de vigencia: así un horario registrado hoy no genera
-- faltas en los días anteriores. Los horarios base que ya existen quedan vigentes desde hoy.
-- ============================================================
ALTER TABLE horarios
    ADD COLUMN vigente_desde DATE NULL COMMENT 'Desde qué fecha programa los días (NULL = siempre)';

UPDATE horarios SET vigente_desde = CURDATE();
