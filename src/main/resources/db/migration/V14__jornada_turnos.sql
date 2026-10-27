-- ============================================================
-- Control NGR - Duración de la jornada por turno
-- Al armar un horario, la hora de salida se calcula sola: entrada + estas horas (se puede cambiar).
-- El turno mañana incluye 1 h de refrigerio (empieza 4 h después de la entrada); el turno tarde no tiene.
-- ============================================================
INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('JORNADA_MANANA_HORAS', '9', 'NUMERO',
     'Horas entre la entrada y la salida del turno mañana, con 1 h de refrigerio incluida (9 = 8 h de trabajo)'),
 ('JORNADA_TARDE_HORAS', '8', 'NUMERO',
     'Horas entre la entrada y la salida del turno tarde (sin refrigerio)');
