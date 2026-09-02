package com.zonaacme.sica.reportes.infraestructura;

import com.zonaacme.sica.reportes.aplicacion.ConsultaReportes;
import com.zonaacme.sica.reportes.aplicacion.FilaVisita;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de lectura para reportes.
 *
 * Trae filas de detalle con los joins ya resueltos. Las agregaciones no se
 * hacen aqui: se hacen en el caso de uso con Stream API, porque sobre el mismo
 * conjunto se construyen varios cortes distintos.
 */
public final class JdbcConsultaReportes implements ConsultaReportes {

    /**
     * El filtro por fecha usa fecha_creacion cuando no hubo ingreso, para que
     * las visitas rechazadas y las expiradas tambien aparezcan en el reporte.
     * Si filtrara solo por fecha_entrada, desaparecerian justamente las visitas
     * que nunca llegaron a materializarse, que son las mas interesantes de
     * revisar.
     */
    private static final String SQL = """
            SELECT v.id, v.persona_id, p.nombre AS persona, p.documento_identidad,
                   p.tipo_persona, e.nombre AS empresa, e.torre,
                   v.tipo_visita, ve.nombre_estado AS estado,
                   v.fecha_entrada, v.fecha_salida,
                   u.nombre AS registrada_por
              FROM visitas v
              JOIN personas p        ON p.id  = v.persona_id
              JOIN empresas e        ON e.id  = v.empresa_destino_id
              JOIN visita_estados ve ON ve.id = v.estado_visita_id
              LEFT JOIN usuarios u   ON u.id  = v.registrada_por_usuario_id
             WHERE DATE(COALESCE(v.fecha_entrada, v.fecha_creacion)) BETWEEN ? AND ?
               AND (? IS NULL OR v.empresa_destino_id = ?)
             ORDER BY COALESCE(v.fecha_entrada, v.fecha_creacion) DESC
            """;

    @Override
    public List<FilaVisita> visitasEntre(LocalDate desde, LocalDate hasta, Long empresaId) {
        List<FilaVisita> filas = new ArrayList<>();

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL)) {

            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));

            // El mismo valor va dos veces: una para la comprobacion de nulo y
            // otra para la comparacion. Es lo que permite que un solo SQL sirva
            // para "todo el complejo" y para "una empresa".
            if (empresaId == null) {
                sentencia.setNull(3, java.sql.Types.INTEGER);
                sentencia.setNull(4, java.sql.Types.INTEGER);
            } else {
                sentencia.setLong(3, empresaId);
                sentencia.setLong(4, empresaId);
            }

            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    filas.add(mapear(fila));
                }
            }
            return filas;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la consulta del reporte de visitas.", e);
        }
    }

    private FilaVisita mapear(ResultSet fila) throws SQLException {
        return new FilaVisita(
                fila.getLong("id"),
                fila.getLong("persona_id"),
                fila.getString("persona"),
                fila.getString("documento_identidad"),
                fila.getString("tipo_persona"),
                fila.getString("empresa"),
                fila.getString("torre"),
                fila.getString("tipo_visita"),
                fila.getString("estado"),
                aFecha(fila.getTimestamp("fecha_entrada")),
                aFecha(fila.getTimestamp("fecha_salida")),
                fila.getString("registrada_por"));
    }

    private LocalDateTime aFecha(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
