package cl.sanosysalvos.mascotas.repository.pg;

import cl.sanosysalvos.mascotas.model.pg.Foto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FotoRepository extends JpaRepository<Foto, UUID> {

    List<Foto> findByReporteIdOrderByOrdenAsc(UUID reporteId);

    void deleteByReporteId(UUID reporteId);
}
