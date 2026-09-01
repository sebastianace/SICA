-- ============================================================================
--  SICA - Sistema Integrado de Control de Acceso para Zona Acme
--  schema.sql : creacion de la base de datos
--  Motor: MySQL 8.x  |  Engine: InnoDB (obligatorio: se usan transacciones)
-- ============================================================================
--
--  BASE: este esquema parte del modelo de datos entregado por el docente.
--  Se conservan sus nombres de tablas, de columnas y sus relaciones.
--
--  EXTENSIONES: se agregan columnas donde los flujos exigidos por el enunciado
--  no se pueden implementar sin ellas. Cada una va marcada con [+] y su razon.
--  Ninguna extension elimina ni renombra nada del modelo original.
--
--  Las tres extensiones de fondo, resumidas:
--    1. visitas necesita tipo, empresa destino y motivo, o los flujos 2 y 3
--       (invitado no anunciado y trabajador sin carnet) no son implementables.
--    2. bitacora_auditoria necesita registrar QUIEN intento entrar cuando el
--       login falla. Con usuario_id nulo se pierde el dato, y el enunciado
--       pide explicitamente auditar los intentos fallidos.
--    3. bitacora_auditoria encadena hashes para que "inmutable" sea una
--       propiedad verificable y no una promesa.
-- ============================================================================

DROP DATABASE IF EXISTS sica;
CREATE DATABASE sica
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE sica;

-- ============================================================================
--  AUTENTICACION Y AUTORIZACION (RBAC)
-- ============================================================================

CREATE TABLE roles (
    id          INT          NOT NULL AUTO_INCREMENT,
    nombre_rol  VARCHAR(50)  NOT NULL,
    descripcion VARCHAR(200) NULL,          -- [+] para explicar el rol en la UI
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_nombre UNIQUE (nombre_rol)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- permisos
--   Cada accion critica del sistema es una fila aqui. El codigo Java NUNCA
--   contiene la lista de permisos de un rol: solo pregunta si el usuario tiene
--   un permiso concreto. Crear un rol nuevo es un INSERT, no un recompilado.
-- ----------------------------------------------------------------------------
CREATE TABLE permisos (
    id             INT          NOT NULL AUTO_INCREMENT,
    nombre_permiso VARCHAR(100) NOT NULL,
    descripcion    TEXT         NULL,
    modulo         VARCHAR(40)  NULL,       -- [+] agrupa permisos en la pantalla de roles
    CONSTRAINT pk_permisos PRIMARY KEY (id),
    CONSTRAINT uq_permisos_nombre UNIQUE (nombre_permiso)
) ENGINE=InnoDB;

CREATE TABLE rol_permisos (
    rol_id     INT NOT NULL,
    permiso_id INT NOT NULL,
    CONSTRAINT pk_rol_permisos PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT fk_rp_rol     FOREIGN KEY (rol_id)     REFERENCES roles (id)    ON DELETE CASCADE,
    CONSTRAINT fk_rp_permiso FOREIGN KEY (permiso_id) REFERENCES permisos (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- usuarios
--   El modelo entregado anota sobre la columna password: "en una aplicacion
--   real, esto deberia ser un hash". Se implemento asi. La columna conserva su
--   nombre y almacena PBKDF2-HMAC-SHA256 con 120.000 iteraciones y salt
--   aleatorio por usuario, en el formato  iteraciones:salt:hash
--   Se uso javax.crypto del JDK para respetar la restriccion de Java puro.
--
--   usuarios y personas son tablas distintas a proposito: toda persona puede
--   cruzar la porteria, pero solo algunas operan el sistema.
-- ----------------------------------------------------------------------------
CREATE TABLE usuarios (
    id             INT          NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(100) NOT NULL,
    email          VARCHAR(100) NOT NULL,
    password       VARCHAR(255) NOT NULL,
    rol_id         INT          NOT NULL,
    esta_activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    empresa_id     INT          NULL,       -- [+] el rol Funcionario responde por SU empresa
    persona_id     INT          NULL,       -- [+] enlaza al usuario con su ficha de persona
    ultimo_acceso  DATETIME     NULL,       -- [+] dato de seguridad basico
    fecha_creacion DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles (id)
) ENGINE=InnoDB;

-- ============================================================================
--  TABLAS DE CONSULTA (LOOKUP) PARA ESTADOS
--
--  El modelo entregado normaliza los estados en tablas en vez de usar ENUM.
--  Se respeta. Vale la pena notar la consecuencia arquitectonica: en Java los
--  estados son enums CON COMPORTAMIENTO (EstadoVisita implementa el patron
--  State y declara sus transiciones validas), y el adaptador JDBC traduce
--  entre ambas representaciones. El dominio no sabe que esta tabla existe.
-- ============================================================================

CREATE TABLE persona_estados_acceso (
    id            INT         NOT NULL AUTO_INCREMENT,
    nombre_estado VARCHAR(50) NOT NULL,
    CONSTRAINT pk_persona_estados PRIMARY KEY (id),
    CONSTRAINT uq_persona_estados UNIQUE (nombre_estado)
) ENGINE=InnoDB;

CREATE TABLE visita_estados (
    id            INT         NOT NULL AUTO_INCREMENT,
    nombre_estado VARCHAR(50) NOT NULL,
    CONSTRAINT pk_visita_estados PRIMARY KEY (id),
    CONSTRAINT uq_visita_estados UNIQUE (nombre_estado)
) ENGINE=InnoDB;

-- ============================================================================
--  TABLAS OPERACIONALES
-- ============================================================================

CREATE TABLE empresas (
    id                 INT          NOT NULL AUTO_INCREMENT,
    nombre             VARCHAR(100) NOT NULL,
    contacto_principal VARCHAR(100) NULL,
    torre              VARCHAR(20)  NULL,   -- [+] el guarda indica a donde dirigirse
    oficina            VARCHAR(20)  NULL,   -- [+] idem
    telefono           VARCHAR(30)  NULL,
    esta_activa        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_empresas PRIMARY KEY (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- personas
--   estado_acceso_id es el interruptor del requisito "bloquear_persona".
--   Al ser una columna de esta tabla, la restriccion es efectiva de inmediato
--   en TODOS los puntos de entrada, porque todos consultan la misma fila.
--   Eso responde al problema de "gestion de incidentes reactiva" del enunciado.
-- ----------------------------------------------------------------------------
CREATE TABLE personas (
    id                  INT          NOT NULL AUTO_INCREMENT,
    nombre              VARCHAR(100) NOT NULL,
    documento_identidad VARCHAR(20)  NOT NULL,
    empresa_id          INT          NULL,
    tipo_persona        ENUM('Trabajador','Invitado') NOT NULL,
    estado_acceso_id    INT          NULL,
    url_foto            VARCHAR(255) NULL,
    tipo_documento      ENUM('CC','CE','TI','PASAPORTE','PEP') NOT NULL DEFAULT 'CC',
                                            -- [+] en Colombia el documento se identifica
                                            -- por tipo y numero. La unicidad sigue siendo
                                            -- la del modelo: documento_identidad solo.
    telefono            VARCHAR(30)  NULL,
    motivo_bloqueo      VARCHAR(300) NULL,  -- [+] el enunciado exige trazabilidad:
    fecha_bloqueo       DATETIME     NULL,  --     un bloqueo sin motivo no es auditable
    CONSTRAINT pk_personas PRIMARY KEY (id),
    CONSTRAINT uq_personas_documento UNIQUE (documento_identidad),
    CONSTRAINT fk_personas_empresa FOREIGN KEY (empresa_id)       REFERENCES empresas (id),
    CONSTRAINT fk_personas_estado  FOREIGN KEY (estado_acceso_id) REFERENCES persona_estados_acceso (id)
) ENGINE=InnoDB;

-- La busqueda por documento en la porteria es la consulta mas frecuente del
-- sistema entero. Sin indice, cada persona en la fila cuesta un recorrido
-- completo de la tabla.
CREATE INDEX idx_personas_documento ON personas (documento_identidad);
CREATE INDEX idx_personas_nombre    ON personas (nombre);

-- ----------------------------------------------------------------------------
-- visitas
--   Entidad central. Una fila = un ciclo de acceso al complejo.
--
--   INVARIANTE DEL SISTEMA: una persona no puede tener mas de una visita en
--   estado "Dentro" al mismo tiempo. Si esa garantia se rompe, el tablero de
--   ocupacion miente, y un tablero que miente no sirve para una evacuacion.
--   MySQL no soporta indices unicos parciales, asi que la invariante se hace
--   cumplir en la capa de servicio con SELECT ... FOR UPDATE dentro de la
--   transaccion de ingreso. Eso ademas bloquea la fila y evita que dos guardas
--   registrando a la misma persona al tiempo creen dos visitas abiertas.
--
--   Las columnas [+] de esta tabla no son opcionales: sin tipo_visita,
--   empresa_destino_id y motivo, los flujos 2 y 3 del enunciado no se pueden
--   implementar, porque no habria a quien notificar ni que mostrarle.
-- ----------------------------------------------------------------------------
CREATE TABLE visitas (
    id                        INT         NOT NULL AUTO_INCREMENT,
    persona_id                INT         NOT NULL,
    fecha_entrada             DATETIME    NULL,
    fecha_salida              DATETIME    NULL,
    estado_visita_id          INT         NOT NULL,
    vehiculo_placa            VARCHAR(10) NULL,
    visita_aprobada_por       INT         NULL,
    empresa_destino_id        INT         NOT NULL,  -- [+] a que empresa va
    anfitrion_usuario_id      INT         NULL,      -- [+] a quien se le notifica
    tipo_visita               ENUM('PRE_REGISTRADA','NO_ANUNCIADA',
                                   'OLVIDO_CARNET','RUTINA_TRABAJADOR') NOT NULL,
                                                     -- [+] distingue los 4 flujos
    motivo                    VARCHAR(300) NULL,     -- [+] el anfitrion decide con esto
    fecha_programada          DATETIME    NULL,      -- [+] flujo 1: pre-registro
    registrada_por_usuario_id INT         NULL,      -- [+] que guarda la registro
    fecha_aprobacion          DATETIME    NULL,      -- [+] cuando respondio el anfitrion
    observaciones             VARCHAR(500) NULL,     -- [+] motivo de rechazo / cierre por sistema
    fecha_creacion            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_visitas PRIMARY KEY (id),
    CONSTRAINT fk_visitas_persona   FOREIGN KEY (persona_id)                REFERENCES personas (id),
    CONSTRAINT fk_visitas_estado    FOREIGN KEY (estado_visita_id)          REFERENCES visita_estados (id),
    CONSTRAINT fk_visitas_aprueba   FOREIGN KEY (visita_aprobada_por)       REFERENCES usuarios (id),
    CONSTRAINT fk_visitas_empresa   FOREIGN KEY (empresa_destino_id)        REFERENCES empresas (id),
    CONSTRAINT fk_visitas_anfitrion FOREIGN KEY (anfitrion_usuario_id)      REFERENCES usuarios (id),
    CONSTRAINT fk_visitas_registra  FOREIGN KEY (registrada_por_usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB;

CREATE INDEX idx_visitas_persona_estado ON visitas (persona_id, estado_visita_id);
CREATE INDEX idx_visitas_estado         ON visitas (estado_visita_id);
CREATE INDEX idx_visitas_fecha_entrada  ON visitas (fecha_entrada);
CREATE INDEX idx_visitas_anfitrion      ON visitas (anfitrion_usuario_id, estado_visita_id);

-- ----------------------------------------------------------------------------
-- incidentes
-- ----------------------------------------------------------------------------
CREATE TABLE incidentes (
    id               INT      NOT NULL AUTO_INCREMENT,
    visita_id        INT      NULL,
    reportado_por_id INT      NOT NULL,
    fecha            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    descripcion      TEXT     NOT NULL,
    persona_id       INT      NULL,         -- [+] no todo incidente ocurre en una visita
    tipo             ENUM('DOCUMENTO_FALSO','COMPORTAMIENTO','OBJETO_PROHIBIDO',
                          'DANO_PROPIEDAD','ACCESO_NO_AUTORIZADO','OTRO')
                     NOT NULL DEFAULT 'OTRO',                        -- [+] reportes por tipo
    gravedad         ENUM('BAJA','MEDIA','ALTA','CRITICA') NOT NULL DEFAULT 'MEDIA', -- [+] prioriza
    estado           ENUM('ABIERTO','EN_REVISION','CERRADO') NOT NULL DEFAULT 'ABIERTO', -- [+] ciclo de vida
    CONSTRAINT pk_incidentes PRIMARY KEY (id),
    CONSTRAINT fk_incidentes_visita  FOREIGN KEY (visita_id)        REFERENCES visitas (id),
    CONSTRAINT fk_incidentes_usuario FOREIGN KEY (reportado_por_id) REFERENCES usuarios (id),
    CONSTRAINT fk_incidentes_persona FOREIGN KEY (persona_id)       REFERENCES personas (id)
) ENGINE=InnoDB;

CREATE INDEX idx_incidentes_fecha  ON incidentes (fecha);
CREATE INDEX idx_incidentes_estado ON incidentes (estado);

-- ----------------------------------------------------------------------------
-- Llaves foraneas diferidas de usuarios
--
-- usuarios se declara antes que empresas y personas porque esas dos tablas
-- dependen de los catalogos de estado, y estos de nada. Como consecuencia, las
-- dos columnas [+] de usuarios no pueden declarar su llave foranea en el
-- CREATE TABLE: las tablas referenciadas todavia no existen.
--
-- Se agregan aqui, una vez creadas. Dejarlas sin restriccion habria permitido
-- que un usuario apuntara a una empresa inexistente, que es justo lo que la
-- integridad referencial existe para impedir.
-- ----------------------------------------------------------------------------
ALTER TABLE usuarios
    ADD CONSTRAINT fk_usuarios_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    ADD CONSTRAINT fk_usuarios_persona FOREIGN KEY (persona_id) REFERENCES personas (id);

-- ============================================================================
--  AUDITORIA
-- ============================================================================
-- ----------------------------------------------------------------------------
-- bitacora_auditoria
--   Se escribe UNICAMENTE desde la capa de servicio de Java, que es requisito
--   explicito del enunciado. NO hay triggers en esta base de datos: si los
--   hubiera, la bitacora no probaria que la logica de Java hizo su trabajo.
--
--   Sobre las extensiones:
--
--   usuario_intento  El enunciado exige auditar los intentos de login fallidos.
--                    En un login fallido no hay usuario autenticado, asi que
--                    usuario_id queda nulo y se perderia el dato mas importante:
--                    QUE cuenta se intento usar. Esta columna lo conserva.
--
--   resultado        Distingue EXITO de FALLO sin depender de como se redacte
--                    accion_realizada. Permite filtrar "todo lo que fue negado"
--                    en una sola consulta, que es lo que se necesita al
--                    investigar un incidente.
--
--   terminal         De que porteria salio la accion.
--
--   hash_anterior /  Encadenan los registros: cada fila incluye el hash de la
--   hash_actual      anterior dentro de su propio calculo SHA-256. Alterar o
--                    borrar una fila vieja rompe la cadena, y el sistema
--                    reporta el numero exacto del registro afectado. Es lo que
--                    convierte "bitacora inmutable" en una propiedad
--                    demostrable en vez de una declaracion de intenciones.
--
--   fecha_hora usa precision de milisegundos porque el valor entra en el
--   calculo del hash: sin ella, el hash no se puede volver a verificar.
-- ----------------------------------------------------------------------------
CREATE TABLE bitacora_auditoria (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id           INT          NULL,
    fecha_hora           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    accion_realizada     VARCHAR(255) NOT NULL,
    tabla_afectada       VARCHAR(100) NULL,
    registro_id_afectado INT          NULL,
    detalles             TEXT         NULL,
    usuario_intento      VARCHAR(100) NULL,                              -- [+] ver nota
    resultado            ENUM('EXITO','FALLO') NOT NULL DEFAULT 'EXITO', -- [+] ver nota
    terminal             VARCHAR(100) NULL,                              -- [+] ver nota
    hash_anterior        CHAR(64)     NULL,                              -- [+] ver nota
    hash_actual          CHAR(64)     NULL,                              -- [+] ver nota
    CONSTRAINT pk_bitacora PRIMARY KEY (id),
    CONSTRAINT fk_bitacora_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB;

CREATE INDEX idx_bitacora_fecha   ON bitacora_auditoria (fecha_hora);
CREATE INDEX idx_bitacora_accion  ON bitacora_auditoria (accion_realizada);
CREATE INDEX idx_bitacora_usuario ON bitacora_auditoria (usuario_id);

-- ============================================================================
--  VISTA DE OCUPACION
--  Responde al problema numero 1 del enunciado: en una emergencia, saber
--  exactamente quien esta dentro del complejo.
-- ============================================================================
CREATE OR REPLACE VIEW v_ocupacion_actual AS
SELECT  v.id     AS visita_id,
        p.id     AS persona_id,
        p.tipo_documento,
        p.documento_identidad,
        p.nombre AS nombre_completo,
        p.tipo_persona,
        p.url_foto,
        e.nombre AS empresa_destino,
        e.torre,
        v.fecha_entrada,
        TIMESTAMPDIFF(MINUTE, v.fecha_entrada, NOW()) AS minutos_dentro
  FROM  visitas v
  JOIN  personas p       ON p.id = v.persona_id
  JOIN  empresas e       ON e.id = v.empresa_destino_id
  JOIN  visita_estados s ON s.id = v.estado_visita_id
 WHERE  s.nombre_estado = 'Dentro'
 ORDER BY v.fecha_entrada;
