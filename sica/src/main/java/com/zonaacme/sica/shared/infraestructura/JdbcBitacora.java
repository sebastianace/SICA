package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.shared.auditoria.Bitacora;
import com.zonaacme.sica.shared.auditoria.LineaBitacora;
import com.zonaacme.sica.shared.auditoria.RegistroAuditoria;
import com.zonaacme.sica.shared.auditoria.ResultadoIntegridad;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de salida de la bitacora sobre MySQL.
 *
 * USA SU PROPIA CONEXION, separada de la transaccion de negocio. Es deliberado:
 * si una operacion falla y hace rollback, el registro de ese intento fallido
 * debe sobrevivir. Una bitacora que se revierte junto con lo que intenta
 * auditar no sirve para investigar nada.
 */
public final class JdbcBitacora implements Bitacora {

    private static final int LARGO_MAX_DETALLE = 1000;

    private static final String SQL_ULTIMO_HASH =
            "SELECT hash_actual FROM bitacora_auditoria ORDER BY id DESC LIMIT 1 FOR UPDATE";

    private static final String SQL_INSERTAR = """
            INSERT INTO bitacora_auditoria
                (usuario_id, usuario_intento, accion_realizada, tabla_afectada, registro_id_afectado,
                 detalles, resultado, terminal, fecha_hora, hash_anterior)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_SELLAR =
            "UPDATE bitacora_auditoria SET hash_actual = ? WHERE id = ?";

    private static final String SQL_ULTIMOS = """
            SELECT b.id, COALESCE(u.email, b.usuario_intento, 'SISTEMA') AS usuario,
                   b.accion_realizada, b.tabla_afectada, b.registro_id_afectado, b.detalles,
                   b.resultado, b.terminal, b.fecha_hora, b.hash_actual
              FROM bitacora_auditoria b
              LEFT JOIN usuarios u ON u.id = b.usuario_id
             ORDER BY b.id DESC
             LIMIT ?
            """;

    private static final String SQL_CADENA = """
            SELECT id, usuario_id, usuario_intento, accion_realizada, tabla_afectada, registro_id_afectado,
                   detalles, resultado, terminal, fecha_hora, hash_anterior, hash_actual
              FROM bitacora_auditoria
             ORDER BY id ASC
            """;

    @Override
    public void registrar(RegistroAuditoria registro) {
        Connection conexion = null;
        try {
            conexion = ConexionBD.abrir();
            conexion.setAutoCommit(false);

            String hashAnterior = leerUltimoHash(conexion);
            long id = insertar(conexion, registro, hashAnterior);

            String hashActual = CalculadorHashBitacora.calcular(
                    id,
                    registro.usuarioId(),
                    registro.usuarioIntento(),
                    registro.accion(),
                    registro.entidadAfectada(),
                    registro.idEntidad(),
                    recortar(registro.detalle()),
                    registro.resultado().name(),
                    registro.terminal(),
                    registro.fechaHora(),
                    hashAnterior);

            sellar(conexion, id, hashActual);
            conexion.commit();

        } catch (SQLException | RuntimeException e) {
            revertirSilencioso(conexion);
            // La auditoria nunca tumba la operacion de negocio, pero el fallo
            // no se esconde: queda en la salida de error para que se investigue.
            System.err.println("[BITACORA] No se pudo registrar la accion '"
                    + registro.accion() + "': " + e.getMessage());
        } finally {
            cerrarSilencioso(conexion);
        }
    }

    private String leerUltimoHash(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_ULTIMO_HASH);
             ResultSet fila = sentencia.executeQuery()) {

            if (fila.next()) {
                String hash = fila.getString("hash_actual");
                return hash != null ? hash : CalculadorHashBitacora.GENESIS;
            }
            return CalculadorHashBitacora.GENESIS;
        }
    }

    private long insertar(Connection conexion, RegistroAuditoria registro, String hashAnterior)
            throws SQLException {

        try (PreparedStatement sentencia =
                     conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            if (registro.usuarioId() != null) {
                sentencia.setLong(1, registro.usuarioId());
            } else {
                sentencia.setNull(1, Types.BIGINT);
            }
            sentencia.setString(2, registro.usuarioIntento());
            sentencia.setString(3, registro.accion());
            sentencia.setString(4, registro.entidadAfectada());
            if (registro.idEntidad() != null) {
                sentencia.setLong(5, registro.idEntidad());
            } else {
                sentencia.setNull(5, Types.BIGINT);
            }
            sentencia.setString(6, recortar(registro.detalle()));
            sentencia.setString(7, registro.resultado().name());
            sentencia.setString(8, registro.terminal());
            sentencia.setTimestamp(9, Timestamp.valueOf(registro.fechaHora()));
            sentencia.setString(10, hashAnterior);

            sentencia.executeUpdate();

            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (claves.next()) {
                    return claves.getLong(1);
                }
                throw new SQLException("MySQL no devolvio el id generado de la bitacora.");
            }
        }
    }

    private void sellar(Connection conexion, long id, String hashActual) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_SELLAR)) {
            sentencia.setString(1, hashActual);
            sentencia.setLong(2, id);
            sentencia.executeUpdate();
        }
    }

    @Override
    public List<LineaBitacora> consultarUltimos(int limite) {
        List<LineaBitacora> lineas = new ArrayList<>();

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ULTIMOS)) {

            sentencia.setInt(1, limite);
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    lineas.add(new LineaBitacora(
                            fila.getLong("id"),
                            fila.getString("usuario"),
                            fila.getString("accion_realizada"),
                            fila.getString("tabla_afectada"),
                            idOpcional(fila, "registro_id_afectado"),
                            fila.getString("detalles"),
                            fila.getString("resultado"),
                            fila.getString("terminal"),
                            fila.getTimestamp("fecha_hora").toLocalDateTime(),
                            fila.getString("hash_actual")));
                }
            }
            return lineas;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la bitacora.", e);
        }
    }

    @Override
    public ResultadoIntegridad verificarIntegridad() {
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_CADENA);
             ResultSet fila = sentencia.executeQuery()) {

            String hashEsperadoDelAnterior = CalculadorHashBitacora.GENESIS;
            int verificados = 0;

            while (fila.next()) {
                long id = fila.getLong("id");
                String hashAnteriorGuardado = fila.getString("hash_anterior");
                String hashActualGuardado   = fila.getString("hash_actual");

                // 1. El eslabon debe apuntar al hash del registro previo.
                if (hashAnteriorGuardado == null
                        || !hashAnteriorGuardado.equals(hashEsperadoDelAnterior)) {
                    return ResultadoIntegridad.rota(verificados, id);
                }

                // 2. El contenido debe seguir produciendo el mismo hash.
                Timestamp marca = fila.getTimestamp("fecha_hora");
                LocalDateTime fechaHora = marca == null ? null : marca.toLocalDateTime();

                String hashRecalculado = CalculadorHashBitacora.calcular(
                        id,
                        idOpcional(fila, "usuario_id"),
                        fila.getString("usuario_intento"),
                        fila.getString("accion_realizada"),
                        fila.getString("tabla_afectada"),
                        idOpcional(fila, "registro_id_afectado"),
                        fila.getString("detalles"),
                        fila.getString("resultado"),
                        fila.getString("terminal"),
                        fechaHora,
                        hashAnteriorGuardado);

                if (!hashRecalculado.equals(hashActualGuardado)) {
                    return ResultadoIntegridad.rota(verificados, id);
                }

                hashEsperadoDelAnterior = hashActualGuardado;
                verificados++;
            }

            return ResultadoIntegridad.intacta(verificados);

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo verificar la integridad de la bitacora.", e);
        }
    }

    /** El modelo usa INT; el dominio usa Long. getObject daria Integer. */
    private Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }

    private String recortar(String detalle) {
        if (detalle == null) return null;
        return detalle.length() <= LARGO_MAX_DETALLE
                ? detalle
                : detalle.substring(0, LARGO_MAX_DETALLE);
    }

    private void revertirSilencioso(Connection conexion) {
        if (conexion == null) return;
        try { conexion.rollback(); } catch (SQLException ignorado) { /* ya se reporto el fallo original */ }
    }

    private void cerrarSilencioso(Connection conexion) {
        if (conexion == null) return;
        try { conexion.close(); } catch (SQLException ignorado) { /* nada util que hacer aqui */ }
    }
}
