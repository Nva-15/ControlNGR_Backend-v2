-- ============================================================
-- Control NGR - Salida automática
-- Si un colaborador no marca su salida, el sistema la registra sola N horas después de
-- la entrada (12 por defecto: entrada 17:00 → salida 05:00 del día siguiente).
-- ============================================================
INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion)
SELECT 'SALIDA_AUTOMATICA_HORAS', '12', 'NUMERO',
       'Horas después de la entrada en que se registra la salida automática si no se marcó (1 a 23)'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM parametros_sistema WHERE clave = 'SALIDA_AUTOMATICA_HORAS');
