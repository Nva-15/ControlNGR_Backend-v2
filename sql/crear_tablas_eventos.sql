-- =====================================================
-- SCRIPT: Sistema de Eventos Dinámicos - ControlNGR
-- Ejecutar en la base de datos: controlngr
-- =====================================================

-- =====================================================
-- TABLA: eventos
-- Almacena los eventos/encuestas/anuncios
-- =====================================================
CREATE TABLE IF NOT EXISTS eventos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    tipo_evento VARCHAR(20) NOT NULL COMMENT 'ENCUESTA, SI_NO, ASISTENCIA, INFORMATIVO',
    fecha_inicio DATETIME NOT NULL,
    fecha_fin DATETIME,
    estado VARCHAR(20) DEFAULT 'BORRADOR' COMMENT 'BORRADOR, ACTIVO, FINALIZADO, CANCELADO',
    roles_visibles VARCHAR(255) DEFAULT 'admin,supervisor,tecnico,hd,noc' COMMENT 'Roles separados por coma',
    permite_comentarios BOOLEAN DEFAULT TRUE,
    requiere_respuesta BOOLEAN DEFAULT FALSE,
    creado_por_id INT NOT NULL,
    fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (creado_por_id) REFERENCES empleados(id) ON DELETE RESTRICT,
    INDEX idx_estado (estado),
    INDEX idx_fecha_inicio (fecha_inicio),
    INDEX idx_tipo_evento (tipo_evento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLA: opciones_evento
-- Opciones de respuesta para encuestas de opción múltiple
-- =====================================================
CREATE TABLE IF NOT EXISTS opciones_evento (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evento_id INT NOT NULL,
    texto_opcion VARCHAR(500) NOT NULL,
    orden INT DEFAULT 0,
    FOREIGN KEY (evento_id) REFERENCES eventos(id) ON DELETE CASCADE,
    INDEX idx_evento (evento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLA: respuestas_evento
-- Respuestas de los empleados a los eventos
-- =====================================================
CREATE TABLE IF NOT EXISTS respuestas_evento (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evento_id INT NOT NULL,
    empleado_id INT NOT NULL,
    opcion_id INT NULL COMMENT 'Para encuestas de opción múltiple',
    respuesta_si_no BOOLEAN NULL COMMENT 'Para preguntas SI/NO',
    confirmacion_asistencia VARCHAR(20) NULL COMMENT 'CONFIRMADO, NO_ASISTIRE, PENDIENTE',
    comentario TEXT,
    fecha_respuesta DATETIME DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (evento_id) REFERENCES eventos(id) ON DELETE CASCADE,
    FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE CASCADE,
    FOREIGN KEY (opcion_id) REFERENCES opciones_evento(id) ON DELETE SET NULL,
    UNIQUE KEY uk_evento_empleado (evento_id, empleado_id),
    INDEX idx_evento (evento_id),
    INDEX idx_empleado (empleado_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLA: comentarios_evento
-- Comentarios adicionales en eventos
-- =====================================================
CREATE TABLE IF NOT EXISTS comentarios_evento (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evento_id INT NOT NULL,
    empleado_id INT NOT NULL,
    comentario TEXT NOT NULL,
    fecha_comentario DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (evento_id) REFERENCES eventos(id) ON DELETE CASCADE,
    FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE CASCADE,
    INDEX idx_evento (evento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- DATOS DE EJEMPLO - Ejecutar para probar el sistema
-- =====================================================

-- Evento tipo ENCUESTA
INSERT INTO eventos (titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, estado, roles_visibles, permite_comentarios, requiere_respuesta, creado_por_id)
VALUES ('Encuesta: Mejor dia para reunion', 'Selecciona el dia que prefieres para las reuniones semanales', 'ENCUESTA', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVO', 'admin,supervisor,tecnico,hd,noc', TRUE, TRUE, 1);

SET @encuesta_id = LAST_INSERT_ID();

INSERT INTO opciones_evento (evento_id, texto_opcion, orden) VALUES
(@encuesta_id, 'Lunes', 1),
(@encuesta_id, 'Miercoles', 2),
(@encuesta_id, 'Viernes', 3);

-- Evento tipo SI/NO
INSERT INTO eventos (titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, estado, roles_visibles, permite_comentarios, requiere_respuesta, creado_por_id)
VALUES ('Capacitacion de Seguridad', 'Estas de acuerdo con asistir a la capacitacion de seguridad informatica?', 'SI_NO', NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'ACTIVO', 'admin,supervisor,tecnico,hd,noc', TRUE, TRUE, 1);

-- Evento tipo ASISTENCIA
INSERT INTO eventos (titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, estado, roles_visibles, permite_comentarios, requiere_respuesta, creado_por_id)
VALUES ('Reunion de Fin de Mes', 'Confirma tu asistencia a la reunion de cierre mensual', 'ASISTENCIA', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'ACTIVO', 'admin,supervisor,tecnico,hd,noc', TRUE, TRUE, 1);

-- Evento tipo INFORMATIVO
INSERT INTO eventos (titulo, descripcion, tipo_evento, fecha_inicio, fecha_fin, estado, roles_visibles, permite_comentarios, requiere_respuesta, creado_por_id)
VALUES ('Mantenimiento del Sistema', 'Se realizara mantenimiento del sistema el proximo sabado de 2am a 6am', 'INFORMATIVO', NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), 'ACTIVO', 'admin,supervisor,tecnico,hd,noc', FALSE, FALSE, 1);
