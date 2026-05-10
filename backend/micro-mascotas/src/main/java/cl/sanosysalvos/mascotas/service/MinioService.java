package cl.sanosysalvos.mascotas.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Servicio para subir y eliminar fotos de mascotas en MinIO.
 *
 * Las fotos se guardan en el bucket con la ruta:
 *   reportes/{reporteId}/{uuid}.{ext}
 *
 * Se genera una URL presignada válida por 7 días para el acceso
 * desde el frontend. En producción se puede cambiar a URLs
 * públicas permanentes si el bucket es de acceso público.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket}")
    private String bucket;

    /**
     * Sube una foto al bucket y retorna la URL presignada.
     *
     * @param reporteId UUID del reporte al que pertenece la foto
     * @param archivo   Archivo recibido desde el frontend
     * @param orden     Orden de display (0 = foto principal)
     * @return Par [bucketKey, url] donde bucketKey es la ruta interna
     */
    public String[] subirFoto(UUID reporteId, MultipartFile archivo, int orden) {
        String ext = getExtension(archivo.getOriginalFilename());
        String bucketKey = "reportes/%s/%d-%s.%s"
                .formatted(reporteId, orden, UUID.randomUUID(), ext);

        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(bucketKey)
                    .stream(archivo.getInputStream(), archivo.getSize(), -1)
                    .contentType(archivo.getContentType())
                    .build());

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(bucketKey)
                            .expiry(7, TimeUnit.DAYS)
                            .build());

            log.debug("Foto subida: {}", bucketKey);
            return new String[]{bucketKey, url};

        } catch (Exception e) {
            log.error("Error subiendo foto a MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Error al subir la foto: " + e.getMessage(), e);
        }
    }

    /** Elimina una foto del bucket dado su bucketKey */
    public void eliminarFoto(String bucketKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(bucketKey)
                    .build());
            log.debug("Foto eliminada: {}", bucketKey);
        } catch (Exception e) {
            log.error("Error eliminando foto de MinIO: {}", e.getMessage(), e);
        }
    }

    // ── Privado ────────────────────────────────────────────────

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Bucket '{}' creado en MinIO", bucket);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
