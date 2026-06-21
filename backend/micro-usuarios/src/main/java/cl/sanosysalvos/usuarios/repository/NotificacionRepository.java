package cl.sanosysalvos.usuarios.repository;

import cl.sanosysalvos.usuarios.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId);

    long countByUsuarioIdAndLeidaFalse(UUID usuarioId);

    boolean existsByCoincidenciaIdAndUsuarioId(UUID coincidenciaId, UUID usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") UUID usuarioId);
}
