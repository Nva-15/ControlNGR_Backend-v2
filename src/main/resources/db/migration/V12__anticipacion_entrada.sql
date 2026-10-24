-- ============================================================
-- Control NGR - Anticipación máxima para marcar entrada
-- Nadie con horario puede marcar entrada antes de N minutos del inicio de su turno.
-- ============================================================
INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('ANTICIPACION_ENTRADA_MINUTOS', '5', 'NUMERO',
     'Minutos antes del inicio del turno desde los que se puede marcar la entrada (0 = desde la hora exacta)');
