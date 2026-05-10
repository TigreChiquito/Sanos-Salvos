package cl.sanosysalvos.mascotas.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para crear un reporte (POST /api/reportes).
 * Las fotos llegan como MultipartFile[] en un request multipart/form-data
 * y se manejan por separado en el controlador.
 */
@Data
public class ReporteRequestDto {

    @NotBlank(message = "El tipo es requerido")
    @Pattern(regexp = "perdido|encontrado", message = "Tipo debe ser 'perdido' o 'encontrado'")
    private String tipo;

    @NotBlank(message = "El animal es requerido")
    @Pattern(regexp = "perro|gato|otro", message = "Animal debe ser 'perro', 'gato' u 'otro'")
    private String animal;

    @Size(max = 100)
    private String nombre;

    @Size(max = 100)
    private String raza;

    @Size(max = 100)
    private String color;

    @Pattern(regexp = "pequeño|mediano|grande|",
             message = "Tamaño debe ser 'pequeño', 'mediano' o 'grande'")
    private String tamano;

    @Size(max = 600, message = "La descripción no puede superar 600 caracteres")
    private String descripcion;

    @NotNull(message = "La latitud es requerida")
    @DecimalMin(value = "-34.6", message = "Latitud fuera de la Región Metropolitana")
    @DecimalMax(value = "-32.9", message = "Latitud fuera de la Región Metropolitana")
    private BigDecimal lat;

    @NotNull(message = "La longitud es requerida")
    @DecimalMin(value = "-71.8", message = "Longitud fuera de la Región Metropolitana")
    @DecimalMax(value = "-70.0", message = "Longitud fuera de la Región Metropolitana")
    private BigDecimal lng;

    private String zona;
}
