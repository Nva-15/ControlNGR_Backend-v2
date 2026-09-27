-- ============================================================
-- Control NGR - Enlace opcional en eventos (reunión virtual, formulario, documento, etc.)
-- Solo se muestra a los asignados cuando tiene valor.
-- ============================================================
ALTER TABLE eventos
    ADD COLUMN enlace VARCHAR(500) NULL COMMENT 'URL http(s) para ingresar con un clic' AFTER descripcion;
