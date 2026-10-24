-- ============================================================
-- Control NGR - Modo de marcación por equipo
-- FACIAL: entrada y salida con reconocimiento facial (rostro registrado obligatorio).
-- BOTON:  entrada y salida con el botón y una confirmación, sin cámara. Los rostros
--         registrados se conservan para cuando el equipo pase a FACIAL.
-- Los equipos sin fila usan el parámetro MARCACION_FACIAL_OBLIGATORIA.
-- ============================================================
CREATE TABLE marcacion_equipo (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    departamento_id  INT          NOT NULL,
    modo             VARCHAR(10)  NOT NULL DEFAULT 'BOTON' COMMENT 'FACIAL o BOTON',
    actualizado_en   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por  VARCHAR(100) NULL,
    CONSTRAINT uk_marcacion_equipo_depto UNIQUE (departamento_id),
    CONSTRAINT fk_marcacion_equipo_depto FOREIGN KEY (departamento_id) REFERENCES departamentos (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Piloto: NOC con reconocimiento facial, el resto con botón (se cambia desde el panel del administrador)
INSERT INTO marcacion_equipo (departamento_id, modo)
SELECT id, CASE WHEN nombre = 'NOC' THEN 'FACIAL' ELSE 'BOTON' END FROM departamentos;

-- El parámetro pasa a ser el modo de quienes no tienen equipo configurado
UPDATE parametros_sistema
   SET valor = 'false',
       descripcion = 'Modo de marcación para colaboradores sin equipo configurado: Sí = reconocimiento facial, No = botón'
 WHERE clave = 'MARCACION_FACIAL_OBLIGATORIA';
