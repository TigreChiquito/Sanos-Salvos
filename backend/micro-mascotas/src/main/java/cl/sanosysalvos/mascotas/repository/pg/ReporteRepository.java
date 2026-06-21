package cl.sanosysalvos.mascotas.repository.pg;

import cl.sanosysalvos.mascotas.model.pg.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReporteRepository extends JpaRepository<Reporte, UUID> {

    List<Reporte> findByUsuarioIdAndEstadoNot(UUID usuarioId, String estado);

    /** Reportes dentro de un bounding box geográfico */
    @Query("""
        SELECT r FROM Reporte r
        WHERE r.estado = 'activo'
          AND r.lat BETWEEN :latMin AND :latMax
          AND r.lng BETWEEN :lngMin AND :lngMax
    """)
    List<Reporte> findActivosEnBounds(
            @Param("latMin") BigDecimal latMin,
            @Param("latMax") BigDecimal latMax,
            @Param("lngMin") BigDecimal lngMin,
            @Param("lngMax") BigDecimal lngMax
    );
}
