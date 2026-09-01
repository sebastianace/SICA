-- ============================================================================
--  SICA - data.sql : datos de poblado inicial
--
--  Este archivo esta disenado para la DEMOSTRACION. Cada persona y cada visita
--  existe para poder mostrar un flujo concreto del enunciado sin improvisar.
--  Al final de cada bloque se indica que se demuestra con esos datos.
--
--  La tabla bitacora_auditoria arranca VACIA a proposito: el enunciado exige
--  que se alimente desde la capa de servicio de Java. Si viniera poblada desde
--  aqui, no probaria nada.
-- ============================================================================

USE sica;

-- ----------------------------------------------------------------------------
-- ESTADOS (tablas de consulta del modelo entregado)
--
-- Los nombres deben coincidir exactamente con lo que traduce el adaptador
-- JDBC. En Java estos estados son enums con comportamiento; aqui son filas.
-- ----------------------------------------------------------------------------
INSERT INTO persona_estados_acceso (nombre_estado) VALUES
('Activo'),
('Con Prohibicion de Ingreso');

INSERT INTO visita_estados (nombre_estado) VALUES
('Aprobado'),                       -- pre-registrada, la persona aun no llega
('Pendiente de Aprobacion'),        -- invitado no anunciado, espera al anfitrion
('Pendiente de Aprobacion Olvido'), -- trabajador sin carnet, espera al anfitrion
('Rechazado'),                      -- el anfitrion nego el ingreso
('Dentro'),                         -- check-in hecho
('Finalizado'),                     -- check-out normal
('Cerrado por Sistema'),            -- salida olvidada, cerrada para auditoria
('Expirado');                       -- aprobada pero nunca se presento

-- ----------------------------------------------------------------------------
-- PERMISOS
--
-- Cada accion critica es una fila. El codigo Java jamas contiene la lista de
-- permisos de un rol: solo pregunta si el usuario tiene uno concreto.
-- ----------------------------------------------------------------------------
INSERT INTO permisos (nombre_permiso, descripcion, modulo) VALUES
-- Modulo de usuarios y seguridad
('crear_usuario',                 'Crear cuentas de usuario del sistema',        'USUARIOS'),
('editar_usuario',                'Modificar cuentas de usuario',                'USUARIOS'),
('eliminar_usuario',              'Desactivar cuentas de usuario',               'USUARIOS'),
('listar_usuarios',               'Consultar el listado de usuarios',            'USUARIOS'),
('gestionar_roles',               'Crear y modificar roles',                     'USUARIOS'),
('gestionar_permisos',            'Asignar permisos a los roles',                'USUARIOS'),
-- Modulo de personas
('crear_persona',                 'Registrar personas en el sistema',            'PERSONAS'),
('editar_persona',                'Modificar datos de una persona',              'PERSONAS'),
('eliminar_persona',              'Retirar una persona del sistema',             'PERSONAS'),
('listar_personas',               'Consultar el directorio de personas',         'PERSONAS'),
('bloquear_persona',              'Imponer prohibicion de ingreso',              'PERSONAS'),
('desbloquear_persona',           'Levantar una prohibicion de ingreso',         'PERSONAS'),
-- Modulo de empresas
('crear_empresa',                 'Registrar empresas del complejo',             'EMPRESAS'),
('editar_empresa',                'Modificar datos de una empresa',              'EMPRESAS'),
('listar_empresas',               'Consultar el listado de empresas',            'EMPRESAS'),
-- Modulo de control de acceso
('consultar_porteria',            'Buscar una persona en la porteria',           'ACCESO'),
('registrar_visita',              'Registrar el ingreso de una persona',         'ACCESO'),
('checkin_visita',                'Confirmar la entrada al complejo',            'ACCESO'),
('checkout_visita',               'Registrar la salida del complejo',            'ACCESO'),
('pre_registrar_visita',          'Anunciar una visita con antelacion',          'ACCESO'),
('aprobar_visita',                'Autorizar una solicitud de ingreso',          'ACCESO'),
('rechazar_visita',               'Negar una solicitud de ingreso',              'ACCESO'),
('ver_visitas_pendientes',        'Ver la bandeja de solicitudes',               'ACCESO'),
('ver_ocupacion_actual',          'Ver quien esta dentro del complejo',          'ACCESO'),
-- Modulo de incidentes
('registrar_incidente',           'Reportar un incidente de seguridad',          'INCIDENTES'),
('listar_incidentes',             'Consultar los incidentes reportados',         'INCIDENTES'),
('cerrar_incidente',              'Dar por resuelto un incidente',               'INCIDENTES'),
-- Modulo de reportes y auditoria
('generar_reporte',               'Generar reportes del sistema',                'REPORTES'),
('exportar_reporte',              'Exportar un reporte a archivo',               'REPORTES'),
('ver_bitacora',                  'Consultar la bitacora de auditoria',          'AUDITORIA'),
('verificar_integridad_bitacora', 'Comprobar que la bitacora no fue alterada',   'AUDITORIA');

-- ----------------------------------------------------------------------------
-- ROLES
--
-- SUPERVISOR se crea aqui, con un INSERT y sin tocar una sola linea de Java.
-- Es la demostracion de que los permisos viven en la base de datos.
-- ----------------------------------------------------------------------------
INSERT INTO roles (nombre_rol, descripcion) VALUES
('Superusuario',            'Control total del sistema'),
('Guarda de Seguridad',     'Opera la porteria: consulta, ingreso y salida'),
('Funcionario de Empresa',  'Autoriza visitas dirigidas a su empresa'),
('Supervisor de Seguridad', 'Supervisa incidentes, reportes y auditoria');

-- ---- Superusuario: todos los permisos ----
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT (SELECT id FROM roles WHERE nombre_rol='Superusuario'), id FROM permisos;

-- ---- Guarda de Seguridad ----
-- Puede operar la porteria, pero NO puede autorizar: esa decision es del
-- anfitrion. Esa separacion es la que sostiene los flujos 2 y 3.
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT (SELECT id FROM roles WHERE nombre_rol='Guarda de Seguridad'), id
  FROM permisos
 WHERE nombre_permiso IN (
    'consultar_porteria','registrar_visita','checkin_visita','checkout_visita',
    'ver_ocupacion_actual','ver_visitas_pendientes','listar_personas',
    'registrar_incidente','listar_empresas');

-- ---- Funcionario de Empresa ----
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT (SELECT id FROM roles WHERE nombre_rol='Funcionario de Empresa'), id
  FROM permisos
 WHERE nombre_permiso IN (
    'pre_registrar_visita','aprobar_visita','rechazar_visita',
    'ver_visitas_pendientes','listar_personas','crear_persona',
    'ver_ocupacion_actual');

-- ---- Supervisor de Seguridad ----
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT (SELECT id FROM roles WHERE nombre_rol='Supervisor de Seguridad'), id
  FROM permisos
 WHERE nombre_permiso IN (
    'listar_personas','crear_persona','editar_persona','eliminar_persona',
    'bloquear_persona','desbloquear_persona',
    'registrar_incidente','listar_incidentes','cerrar_incidente',
    'generar_reporte','exportar_reporte','ver_bitacora',
    'verificar_integridad_bitacora','ver_ocupacion_actual','listar_empresas');

-- ----------------------------------------------------------------------------
-- EMPRESAS
-- ----------------------------------------------------------------------------
INSERT INTO empresas (nombre, contacto_principal, torre, oficina, telefono) VALUES
('Nexo Analytics S.A.S.',    'Mariana Rodriguez', 'Torre A', '1201', '601 4567890'),
('Vertiga Ingenieria S.A.',  'Camilo Duarte',     'Torre A', '804',  '601 4567891'),
('Lumen Studio Creativo',    'Luis Carlos Gomez', 'Torre B', '502',  '601 4567892'),
('Andes BioLab',             'Sebastian Ochoa',   'Torre B', '1503', '601 4567893'),
('Zona Acme Administracion', 'Patricia Alvarez',  'Torre A', '101',  '601 4567800');

-- ----------------------------------------------------------------------------
-- PERSONAS
--
-- Nota: el modelo entregado define tipo_persona como ('Trabajador','Invitado').
-- Los contratistas se registran como Invitado, que es su condicion de acceso
-- real: entran por autorizacion puntual y no tienen carnet permanente.
-- ----------------------------------------------------------------------------
INSERT INTO personas (nombre, documento_identidad, tipo_documento, empresa_id,
                      tipo_persona, estado_acceso_id, url_foto, telefono) VALUES
-- Trabajadores
('Andres Felipe Mejia Rojas', '1015234567','CC', 1,'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=12','3101234567'),

('Juliana Torres Bedoya',     '1032998877','CC', 2,'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=45','3119876543'),

('Mariana Rodriguez Salas',   '1098765432','CC', 1,'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=32','3125554433'),

('Luis Carlos Gomez Parra',   '1076543210','CC', 3,'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=59','3134445566'),

('Sebastian Ochoa Nino',      '1045678901','CC', 4,'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=68','3141112233'),

-- Invitados
('Camila Restrepo Villa',     '1020304050','CC', NULL,'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=25','3156667788'),

('Thomas Keller',             'E4455667',  'CE', NULL,'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=51','3168889900'),

('Valentina Nieto Cardenas',  '1088776655','CC', NULL,'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=20','3172223344'),

('Ricardo Beltran Munoz',     '1099887766','CC', NULL,'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Activo'),
 'https://i.pravatar.cc/300?img=13','3183334455'),

-- Persona con prohibicion activa: al buscarla, la franja debe ponerse roja.
('Oscar Pineda Lara',         '1011223344','CC', NULL,'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE nombre_estado='Con Prohibicion de Ingreso'),
 'https://i.pravatar.cc/300?img=7','3194445566');

UPDATE personas
   SET motivo_bloqueo = 'Intento de ingreso con documento adulterado. Caso en investigacion.',
       fecha_bloqueo  = DATE_SUB(NOW(), INTERVAL 6 DAY)
 WHERE documento_identidad = '1011223344';

-- ----------------------------------------------------------------------------
-- USUARIOS
--
-- La columna password almacena PBKDF2-HMAC-SHA256 con 120.000 iteraciones y
-- salt aleatorio por usuario, en el formato  iteraciones:salt:hash
-- Los hashes de abajo fueron generados con la clase HasheadorPassword del
-- proyecto y verificados contra ella.
--
-- El inicio de sesion usa el correo, que es la columna unica del modelo.
--
--   admin@zonaacme.co          Admin123*     Superusuario
--   hrincon@zonaacme.co        Guarda123*    Guarda de Seguridad
--   ncastillo@zonaacme.co      Guarda123*    Guarda de Seguridad
--   mrodriguez@nexoanalytics.co Funciona123* Funcionario (Nexo Analytics)
--   lgomez@lumenstudio.co      Funciona123*  Funcionario (Lumen Studio)
--   palvarez@zonaacme.co       Super123*     Supervisor de Seguridad
-- ----------------------------------------------------------------------------
INSERT INTO usuarios (nombre, email, password, rol_id, empresa_id, persona_id) VALUES
('Administrador SICA', 'admin@zonaacme.co',
 '120000:4ssiXeDlobJVqM/xbJI5Mg==:FAH7XC7wlq67zKhIfbWuhozrYGjsmNiMXRX9hgQF7gY=',
 (SELECT id FROM roles WHERE nombre_rol='Superusuario'), 5, NULL),

('Hector Rincon Diaz', 'hrincon@zonaacme.co',
 '120000:doP1bUrhVYxb+AT6PMbACg==:OyCn4YrRM8yD39kqQEzpeQ69u0uBl5v3aIE6OlByRHk=',
 (SELECT id FROM roles WHERE nombre_rol='Guarda de Seguridad'), 5, NULL),

('Nubia Castillo Perez', 'ncastillo@zonaacme.co',
 '120000:KpiK0jkqXgfgRDVkGbnKNw==:3oSazmiSnC8WpKtEujGUO0MwI6mPfhzCybK/Dn1dXXM=',
 (SELECT id FROM roles WHERE nombre_rol='Guarda de Seguridad'), 5, NULL),

('Mariana Rodriguez Salas', 'mrodriguez@nexoanalytics.co',
 '120000:dxFFFk0rxwansO4ZY+rEqQ==:TEUdeXSzltzDYBe3kL+dg5OhBvcATGj2ffH7Lf8MLNQ=',
 (SELECT id FROM roles WHERE nombre_rol='Funcionario de Empresa'), 1,
 (SELECT id FROM personas WHERE documento_identidad='1098765432')),

('Luis Carlos Gomez Parra', 'lgomez@lumenstudio.co',
 '120000:T4nBlXy4ygaKVR2ZVhWPNQ==:aHHwya0SoGGTNmPdBKB8ONubxMOJST44E5ChI5iNGd8=',
 (SELECT id FROM roles WHERE nombre_rol='Funcionario de Empresa'), 3,
 (SELECT id FROM personas WHERE documento_identidad='1076543210')),

('Patricia Alvarez Ruiz', 'palvarez@zonaacme.co',
 '120000:nheLI4RkObDOLsE0v9HtVw==:Wj9eYQidiwOVO5laDjYMN3BzezjPDw5/AfhSvNcDs/s=',
 (SELECT id FROM roles WHERE nombre_rol='Supervisor de Seguridad'), 5, NULL);

-- ----------------------------------------------------------------------------
-- VISITAS DE DEMOSTRACION
-- ----------------------------------------------------------------------------

-- FLUJO 1. Camila fue pre-registrada para hoy por la funcionaria de Nexo.
-- DEMO: buscar CC 1020304050 -> la franja debe decir AUTORIZADO.
INSERT INTO visitas (persona_id, empresa_destino_id, anfitrion_usuario_id, tipo_visita,
                     estado_visita_id, motivo, fecha_programada, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1020304050'), 1,
        (SELECT id FROM usuarios WHERE email='mrodriguez@nexoanalytics.co'),
        'PRE_REGISTRADA',
        (SELECT id FROM visita_estados WHERE nombre_estado='Aprobado'),
        'Entrevista de seleccion', DATE_ADD(CURDATE(), INTERVAL 9 HOUR),
        (SELECT id FROM usuarios WHERE email='mrodriguez@nexoanalytics.co'));

-- FLUJO 4. Juliana entro ayer y nunca registro su salida.
-- DEMO: buscar CC 1032998877 -> avisa la salida olvidada y dice desde cuando.
-- Al registrar el ingreso, el total de ocupacion NO debe subir.
INSERT INTO visitas (persona_id, empresa_destino_id, tipo_visita, estado_visita_id,
                     fecha_entrada, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1032998877'), 2,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Dentro'),
        DATE_SUB(NOW(), INTERVAL 26 HOUR),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co'));

-- Ocupacion de fondo: dos trabajadores dentro ahora mismo, para que el
-- tablero de "quien esta dentro" no aparezca vacio en la demostracion.
INSERT INTO visitas (persona_id, empresa_destino_id, tipo_visita, estado_visita_id,
                     fecha_entrada, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1098765432'), 1,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Dentro'),
        DATE_SUB(NOW(), INTERVAL 3 HOUR),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co')),

       ((SELECT id FROM personas WHERE documento_identidad='1045678901'), 4,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Dentro'),
        DATE_SUB(NOW(), INTERVAL 5 HOUR),
        (SELECT id FROM usuarios WHERE email='ncastillo@zonaacme.co'));

-- Historial cerrado: da cuerpo a los reportes por rango de fechas.
INSERT INTO visitas (persona_id, empresa_destino_id, tipo_visita, estado_visita_id,
                     fecha_entrada, fecha_salida, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1015234567'), 1,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Finalizado'),
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 47 HOUR),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co')),

       ((SELECT id FROM personas WHERE documento_identidad='1076543210'), 3,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Finalizado'),
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 70 HOUR),
        (SELECT id FROM usuarios WHERE email='ncastillo@zonaacme.co')),

       ((SELECT id FROM personas WHERE documento_identidad='E4455667'), 2,
        'NO_ANUNCIADA',
        (SELECT id FROM visita_estados WHERE nombre_estado='Finalizado'),
        DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 92 HOUR),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co'));

-- Una visita rechazada y una cerrada por el sistema: dan material a los
-- reportes y demuestran que la maquina de estados usa todos sus estados.
INSERT INTO visitas (persona_id, empresa_destino_id, anfitrion_usuario_id, tipo_visita,
                     estado_visita_id, motivo, observaciones, visita_aprobada_por,
                     fecha_aprobacion, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1099887766'), 3,
        (SELECT id FROM usuarios WHERE email='lgomez@lumenstudio.co'),
        'NO_ANUNCIADA',
        (SELECT id FROM visita_estados WHERE nombre_estado='Rechazado'),
        'Entrega de materiales sin orden de compra',
        'No hay orden de compra asociada. Debe volver con la orden firmada.',
        (SELECT id FROM usuarios WHERE email='lgomez@lumenstudio.co'),
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co'));

INSERT INTO visitas (persona_id, empresa_destino_id, tipo_visita, estado_visita_id,
                     fecha_entrada, fecha_salida, observaciones, registrada_por_usuario_id)
VALUES ((SELECT id FROM personas WHERE documento_identidad='1045678901'), 4,
        'RUTINA_TRABAJADOR',
        (SELECT id FROM visita_estados WHERE nombre_estado='Cerrado por Sistema'),
        DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY),
        'Cerrada automaticamente por el sistema: se detecto un nuevo ingreso sin que se hubiera registrado la salida anterior.',
        (SELECT id FROM usuarios WHERE email='ncastillo@zonaacme.co'));

-- ----------------------------------------------------------------------------
-- INCIDENTES
-- ----------------------------------------------------------------------------
INSERT INTO incidentes (visita_id, persona_id, reportado_por_id, fecha, descripcion, tipo, gravedad, estado)
VALUES ((SELECT id FROM visitas WHERE persona_id =
            (SELECT id FROM personas WHERE documento_identidad='1099887766') LIMIT 1),
        (SELECT id FROM personas WHERE documento_identidad='1099887766'),
        (SELECT id FROM usuarios WHERE email='hrincon@zonaacme.co'),
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        'La persona insistio en ingresar tras el rechazo del anfitrion y fue necesario acompanarla a la salida.',
        'COMPORTAMIENTO','MEDIA','ABIERTO'),

       (NULL,
        (SELECT id FROM personas WHERE documento_identidad='1011223344'),
        (SELECT id FROM usuarios WHERE email='palvarez@zonaacme.co'),
        DATE_SUB(NOW(), INTERVAL 6 DAY),
        'Se detecto que el documento presentado estaba adulterado. Se impuso prohibicion de ingreso.',
        'DOCUMENTO_FALSO','ALTA','EN_REVISION');

-- ============================================================================
--  RESUMEN PARA LA DEMOSTRACION
--
--  Documento      Persona                  Que demuestra
--  ------------   ----------------------   --------------------------------
--  1020304050     Camila Restrepo          Flujo 1: AUTORIZADO
--  1088776655     Valentina Nieto          Flujo 2: requiere autorizacion
--  1015234567     Andres Mejia             Flujo 3: trabajador sin carnet
--  1032998877     Juliana Torres           Flujo 4: salida olvidada (26 h)
--  1011223344     Oscar Pineda             Bloqueo activo: franja roja
--  9999999999     (no existe)              Veredicto NO REGISTRADO
-- ============================================================================
