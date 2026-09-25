-- ============================================================
-- Control NGR - V2: datos iniciales
-- Todo lo cargado aqui se puede modificar desde el panel admin.
-- ============================================================

-- ------------------------------------------------------------
-- Tipos de usuario (roles) con su jerarquia
-- ------------------------------------------------------------
INSERT INTO tipos_usuario (id, codigo, nombre, descripcion, nivel_jerarquia, puede_solicitar, marca_asistencia, es_sistema) VALUES
 (1,  'admin',      'Administrador del sistema', 'Panel maestro. No es personal: no marca asistencia ni gestiona solicitudes', 0, 0, 0, 1),
 (2,  'director',   'Director',                  'Director de Sistemas',                    100, 0, 1, 0),
 (3,  'gerente',    'Gerente',                   'Gerente de Infraestructura y Soporte',    90,  1, 1, 0),
 (4,  'jefe',       'Jefe',                      'Jefe de Soporte',                          80,  1, 1, 0),
 (5,  'supervisor', 'Supervisor',                'Supervisores de SOP, HD y NOC',            60,  1, 1, 0),
 (6,  'gestor',     'Gestor',                    'Gestor de Tiendas y Almacén de Sistemas',  60,  1, 1, 0),
 (7,  'tecnico',    'Técnico de Soporte',        'Personal de soporte técnico',              10,  1, 1, 0),
 (8,  'hd',         'Help Desk',                 'Personal de Help Desk',                    10,  1, 1, 0),
 (9,  'noc',        'NOC',                       'Personal de monitoreo NOC',                10,  1, 1, 0),
 (10, 'bo',         'Back Office',               'Personal de Back Office',                  10,  1, 1, 0),
 (11, 'asistente',  'Asistente de Almacén',      'Asistentes de almacén de sistemas',        10,  1, 1, 0);

-- ------------------------------------------------------------
-- Quien aprueba a quien
-- ------------------------------------------------------------
INSERT INTO reglas_aprobacion (tipo_usuario_solicitante_id, tipo_usuario_aprobador_id) VALUES
 (7, 5),   -- tecnico    -> supervisor
 (8, 5),   -- hd         -> supervisor
 (9, 5),   -- noc        -> supervisor
 (10, 5),  -- bo         -> supervisor
 (5, 4),   -- supervisor -> jefe
 (4, 3),   -- jefe       -> gerente
 (3, 2),   -- gerente    -> director
 (6, 3),   -- gestor     -> gerente
 (11, 6);  -- asistente  -> gestor

-- ------------------------------------------------------------
-- Departamentos
-- ------------------------------------------------------------
INSERT INTO departamentos (id, nombre, descripcion) VALUES
 (1, 'Infraestructura y Soporte',      'Gerencia de TI y soporte técnico'),
 (2, 'Soporte Técnico',                'Atención a usuarios y sistemas'),
 (3, 'NOC',                            'Monitoreo de redes y operaciones'),
 (4, 'HD',                             'Help Desk y atencion al cliente'),
 (5, 'Back Office',                    'Procesos administrativos y gestión'),
 (6, 'Tiendas y Almacén de Sistemas',  'Gestión de tiendas y almacén de sistemas');

-- ------------------------------------------------------------
-- Empleados iniciales (se mantienen los IDs del sistema anterior)
-- Correo NULL en Alfredo y Jose Carlos: en la base anterior tenian
-- el correo de otro empleado. Completar desde el panel admin.
-- ------------------------------------------------------------
INSERT INTO empleados (id, dni, nombre, cargo, departamento_id, nivel, descripcion, hobby, cumpleanos, ingreso, foto, activo, identificador, email) VALUES
 (33, '07817648', 'Rafael Augusto Alegria Galarreta', 'Director de Sistemas',                 1, 'jefe',       NULL, NULL, '1964-05-03', '2017-02-13', 'img/rafael_augusto_alegria_galarreta.png', 1, 'rafael-alegria', 'rafael.augustos@ngr.com.pe'),
 (1,  '40434364', 'Alfredo Novoa Linares',            'Gerente de Infraestructura y Soporte', 1, 'jefe',       NULL, NULL, '1979-08-16', '2021-09-01', 'img/alfredo_linares_1_1768953751058.png',  1, 'alfredo-novoa',  NULL),
 (2,  '25740747', 'Jose Carlos Ruiz Sanchez',         'Jefe de Soporte',                      1, 'jefe',       NULL, NULL, '1980-11-10', '2014-08-01', 'img/perfil.png',                           1, 'jose-carlos',    NULL),
 (3,  '72636913', 'Joel Angel Sanchez Sanchez',       'Supervisor de SP-HD-NOC',              2, 'supervisor', NULL, NULL, '1994-05-16', '2018-03-01', 'img/Joel.png',                             1, 'joel-sanchez',   'joel.sanchez@ngr.com.pe'),
 (4,  '47963094', 'Andres Saul Ramos Sanchez',        'Supervisor de HD',                     4, 'supervisor', NULL, 'ESCUCHAR MUSICA', '1993-06-21', '2020-09-01', 'img/andres_saul_ramos_sanchez_1.png', 1, 'andres-ramos', 'andres.ramos@ngr.com.pe'),
 (5,  '46398243', 'Sergio Augusto Acosta Huapaya',    'Supervisor de SOP',                    2, 'supervisor', NULL, NULL, '1990-06-01', '2014-12-01', 'img/sergio_augusto_acosta_huapaya_1.png',   1, 'sergio-acosta',  'sergio.acosta@ngr.com.pe'),
 (6,  '48332308', 'Leo Marcelino Cordova Cucho',      'Supervisor de SOP',                    2, 'supervisor', NULL, NULL, '1994-08-15', '2020-08-01', 'img/leo.png',                              1, 'leo-cordova',    'leo.cordova@ngr.com.pe'),
 (7,  '76723804', 'Geam Franco Alexis Pari Pinedo',   'Supervisor de NOC',                    3, 'supervisor', NULL, NULL, '1994-09-10', '2020-09-01', 'img/geam.png',                             1, 'geam-pari',      'geam.pari@ngr.com.pe'),
 (8,  '75833543', 'Eli Neiser Vasquez Alarcon',       'Líder Técnico (LT)',                   2, 'tecnico',    'Soporte TI', 'Fútbol, Música, Nadar', '1996-05-15', '2020-12-01', 'img/eli_neiser_vasquez_alarcon_1.png', 1, 'eli-neiser', 'eli.vasquez@ngr.com.pe');

UPDATE departamentos SET responsable_id = 1 WHERE id = 1;
UPDATE departamentos SET responsable_id = 2 WHERE id = 2;
UPDATE departamentos SET responsable_id = 7 WHERE id = 3;
UPDATE departamentos SET responsable_id = 4 WHERE id = 4;

-- ------------------------------------------------------------
-- Usuarios. Todos deben cambiar su contraseña en el primer ingreso.
-- Los empleados conservan su usuario (DNI) y su contraseña anterior.
-- ------------------------------------------------------------
INSERT INTO usuarios (empleado_id, username, password, tipo_usuario_id, debe_cambiar_password) VALUES
 (NULL, 'admin',    '$2a$10$zT.Yl8FggGB9Fi0RdeiwZOn7wMASLFM2xLuteEjIl05f7Pp3Fp6.K', 1, 1),
 (33,   '07817648', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 2, 1),
 (1,    '40434364', '$2a$10$46gdfASTyg3na/3RIsyRYe1w7yTJDKtlukuI3HoOEeqz6yUjtqVVu', 3, 1),
 (2,    '25740747', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 4, 1),
 (3,    '72636913', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 5, 1),
 (4,    '47963094', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 5, 1),
 (5,    '46398243', '$2a$10$aDpSMIbSIsIaFJXW2cPNROYZiA.6Ezeog.RazzODXMZwnUfHtcZRO', 5, 1),
 (6,    '48332308', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 5, 1),
 (7,    '76723804', '$2a$10$Cf8GgTlX0hj36clzSu4./OzPuq1bAfPM1T1OA8gyen1W49ghN78ZC', 5, 1),
 (8,    '75833543', '$2a$10$W27E9zpnU4dU3mocJzqJme8w10VsyAtb0V9QzNqCKreM771XduU/y', 7, 1);

-- Saldos en cero: el admin carga los dias pendientes a la fecha
INSERT INTO saldos (empleado_id, tipo_saldo, saldo)
SELECT id, 'VACACIONES', 0 FROM empleados
UNION ALL
SELECT id, 'COMPENSACION', 0 FROM empleados;

-- ------------------------------------------------------------
-- Parametros del sistema
-- ------------------------------------------------------------
INSERT INTO parametros_sistema (clave, valor, tipo_dato, descripcion) VALUES
 ('DIAS_POR_FERIADO_LABORADO',   '2',    'NUMERO',   'Días de compensación que se abonan por trabajar un feriado'),
 ('DIAS_VACACIONES_POR_ANIO',    '30',   'NUMERO',   'Días de vacaciones que se abonan por cada año cumplido'),
 ('VACACIONES_ABONO_DESDE',      DATE_FORMAT(CURRENT_DATE, '%Y-%m-%d'), 'FECHA',
     'Solo se abonan automáticamente los aniversarios desde esta fecha. Lo anterior se registra con la carga inicial'),
 ('VALIDAR_IP_MARCACION',        'true', 'BOOLEANO', 'Si está activo, solo se puede marcar asistencia desde los segmentos de red registrados'),
 ('TOLERANCIA_TARDANZA_MINUTOS', '5',    'NUMERO',   'Minutos de tolerancia antes de considerar tardanza'),
 ('EVIDENCIA_MAX_MB',            '10',   'NUMERO',   'Tamaño máximo del archivo de evidencia (foto o PDF)');

-- ------------------------------------------------------------
-- Segmentos de red permitidos para marcar asistencia
-- ------------------------------------------------------------
INSERT INTO segmentos_red (nombre, patron, descripcion) VALUES
 ('Red oficina', '10.92.104.%',   'Red LAN de la oficina'),
 ('VPN',         '192.168.113.%', 'Conexión por VPN');

-- ------------------------------------------------------------
-- Tipos de solicitud
-- ------------------------------------------------------------
INSERT INTO tipos_solicitud (codigo, nombre, descripcion, descuenta_de, requiere_evidencia, requiere_motivo_licencia, tipo_dia_horario) VALUES
 ('vacaciones',      'Vacaciones',                'Descuenta del saldo de vacaciones al ser aprobada',          'VACACIONES',   0, 0, 'vacaciones'),
 ('compensacion',    'Compensación por feriado',  'Descuenta del saldo de días por compensar al ser aprobada',  'COMPENSACION', 0, 0, 'compensado'),
 ('descanso_medico', 'Descanso médico',           'Requiere adjuntar el certificado médico (foto o PDF)',       'NINGUNO',      1, 0, 'descanso_medico'),
 ('licencia',        'Licencia',                  'Requiere adjuntar evidencia (foto o PDF)',                   'NINGUNO',      1, 1, 'licencia');

INSERT INTO motivos_licencia (nombre, descripcion) VALUES
 ('Paternidad',              'Licencia por nacimiento de hijo'),
 ('Fallecimiento de familiar','Licencia por fallecimiento de familiar directo'),
 ('Otro',                    'Otro motivo sustentado');

-- ------------------------------------------------------------
-- Feriados nacionales de Peru (sector privado)
-- No incluye dias no laborables del sector publico.
-- ------------------------------------------------------------
INSERT INTO dias_feriados (fecha, descripcion) VALUES
 ('2026-01-01', 'Año Nuevo'),
 ('2026-04-02', 'Jueves Santo'),
 ('2026-04-03', 'Viernes Santo'),
 ('2026-05-01', 'Día del Trabajo'),
 ('2026-06-07', 'Batalla de Arica y Día de la Bandera'),
 ('2026-06-29', 'San Pedro y San Pablo'),
 ('2026-07-23', 'Día de la Fuerza Aérea del Perú'),
 ('2026-07-28', 'Fiestas Patrias'),
 ('2026-07-29', 'Fiestas Patrias'),
 ('2026-08-06', 'Batalla de Junín'),
 ('2026-08-30', 'Santa Rosa de Lima'),
 ('2026-10-08', 'Combate de Angamos'),
 ('2026-11-01', 'Día de Todos los Santos'),
 ('2026-12-08', 'Inmaculada Concepción'),
 ('2026-12-09', 'Batalla de Ayacucho'),
 ('2026-12-25', 'Navidad'),
 ('2027-01-01', 'Año Nuevo'),
 ('2027-03-25', 'Jueves Santo'),
 ('2027-03-26', 'Viernes Santo'),
 ('2027-05-01', 'Día del Trabajo'),
 ('2027-06-07', 'Batalla de Arica y Día de la Bandera'),
 ('2027-06-29', 'San Pedro y San Pablo'),
 ('2027-07-23', 'Día de la Fuerza Aérea del Perú'),
 ('2027-07-28', 'Fiestas Patrias'),
 ('2027-07-29', 'Fiestas Patrias'),
 ('2027-08-06', 'Batalla de Junín'),
 ('2027-08-30', 'Santa Rosa de Lima'),
 ('2027-10-08', 'Combate de Angamos'),
 ('2027-11-01', 'Día de Todos los Santos'),
 ('2027-12-08', 'Inmaculada Concepción'),
 ('2027-12-09', 'Batalla de Ayacucho'),
 ('2027-12-25', 'Navidad');
