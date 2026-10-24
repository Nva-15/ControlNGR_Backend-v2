-- ============================================================
-- Control NGR - Índices para las listas paginadas (Empleados, Saldos, Reconocimiento facial)
-- Filtran por estado y departamento y ordenan por nombre; con estos índices cada página
-- se resuelve sin recorrer toda la tabla aunque crezca el personal.
-- ============================================================
CREATE INDEX idx_empleados_activo_nombre ON empleados (activo, nombre);
CREATE INDEX idx_empleados_depto_activo_nombre ON empleados (departamento_id, activo, nombre);
