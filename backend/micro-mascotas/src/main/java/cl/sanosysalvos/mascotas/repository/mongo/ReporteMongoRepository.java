package cl.sanosysalvos.mascotas.repository.mongo;

import cl.sanosysalvos.mascotas.model.mongo.ReporteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReporteMongoRepository extends MongoRepository<ReporteDocument, String> {

    /** Reportes activos filtrados por tipo y/o animal */
    List<ReporteDocument> findByEstadoAndTipoAndAnimal(String estado, String tipo, String animal);

    List<ReporteDocument> findByEstadoAndTipo(String estado, String tipo);

    List<ReporteDocument> findByEstadoAndAnimal(String estado, String animal);

    List<ReporteDocument> findByEstado(String estado);

    /** Reportes de un usuario específico */
    List<ReporteDocument> findByUsuarioIdAndEstadoNot(String usuarioId, String estado);

    /** Búsqueda dentro de un bounding box geográfico (lectura del mapa) */
    @Query("""
        {
          'estado': 'activo',
          'lat': { $gte: ?0, $lte: ?1 },
          'lng': { $gte: ?2, $lte: ?3 }
        }
    """)
    List<ReporteDocument> findActivosEnBounds(
            Double latMin, Double latMax, Double lngMin, Double lngMax);
}
