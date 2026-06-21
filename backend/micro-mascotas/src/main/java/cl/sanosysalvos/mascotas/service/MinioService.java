package cl.sanosysalvos.mascotas.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Servicio para subir y eliminar fotos de mascotas en MinIO.
 *
 * El bucket se configura con política pública de lectura, por lo que las
 * URLs son simples y permanentes: {publicUrl}/{bucket}/{bucketKey}
 * Sin firma — el browser las accede directamente sin autenticación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.minio.public-url:http://localhost:9000}")
    private String publicUrl;

    /**
     * Sube una foto al bucket y retorna la URL pública.
     *
     * @return Par [bucketKey, url] donde url es accesible directamente desde el browser
     */
    public String[] subirFoto(UUID reporteId, MultipartFile archivo, int orden) {
        String ext = getExtension(archivo.getOriginalFilename());
        String bucketKey = "reportes/%s/%d-%s.%s"
                .formatted(reporteId, orden, UUID.randomUUID(), ext);

        try {
            ensureBucketPublico();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(bucketKey)
                    .stream(archivo.getInputStream(), archivo.getSize(), -1)
                    .contentType(archivo.getContentType())
                    .build());

            String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            String url = base + "/" + bucket + "/" + bucketKey;

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

    private void ensureBucketPublico() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Bucket '{}' creado en MinIO", bucket);
        }
        // Política pública de lectura — permite GET sin autenticación
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}]}
                """.formatted(bucket);
        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder().bucket(bucket).config(policy.strip()).build());
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
