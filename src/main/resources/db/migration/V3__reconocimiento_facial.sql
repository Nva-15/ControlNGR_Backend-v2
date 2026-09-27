-- ============================================================
-- Control NGR v3 - Marcación con reconocimiento facial
-- ============================================================

-- Rostro registrado de cada empleado: varias muestras (descriptores de 128 valores de face-api.js)
CREATE TABLE rostros_empleado (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    empleado_id     INT      NOT NULL,
    descriptor      TEXT     NOT NULL COMMENT 'Arreglo JSON de 128 números',
    registrado_por  INT      NULL COMMENT 'Usuario que hizo el registro (el mismo empleado, su jefatura o el admin)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rostros_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id) ON DELETE CASCADE,
    CONSTRAINT fk_rostros_usuario  FOREIGN KEY (registrado_por) REFERENCES usuarios (id) ON DELETE SET NULL,
    INDEX idx_rostros_empleado (empleado_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Consentimiento para el tratamiento del dato biométrico (Ley 29733)
ALTER TABLE empleados
    ADD COLUMN consentimiento_facial_at DATETIME NULL AFTER email;

-- Cómo se verificó cada marcación
ALTER TABLE asistencia
    ADD COLUMN metodo_entrada   VARCHAR(20)  NULL COMMENT 'facial o manual' AFTER ip_salida,
    ADD COLUMN metodo_salida    VARCHAR(20)  NULL COMMENT 'facial o manual' AFTER metodo_entrada,
    ADD COLUMN distancia_entrada DECIMAL(6,4) NULL COMMENT 'Distancia facial obtenida (menor = más parecido)' AFTER metodo_salida,
    ADD COLUMN distancia_salida  DECIMAL(6,4) NULL AFTER distancia_entrada;

INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('MARCACION_FACIAL_OBLIGATORIA', 'true', 'BOOLEANO',
     'Si está activo, toda marcación de entrada o salida exige reconocimiento facial'),
 ('UMBRAL_FACIAL', '0.5', 'NUMERO',
     'Distancia máxima para aceptar el rostro (0.4 estricto - 0.6 permisivo)'),
 ('MUESTRAS_FACIALES', '5', 'NUMERO',
     'Cantidad de capturas que se toman al registrar un rostro');
