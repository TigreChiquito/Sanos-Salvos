package cl.sanosysalvos.mascotas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para crear un reporte (POST /api/reportes).
 * Las fotos llegan como MultipartFile[] en un request multipart/form-data
 * y se manejan por separado en el controlador.
 */
@Data
@Schema(description = "Datos para crear un reporte de mascota")
public class ReporteRequestDto {

    @Schema(description = "Tipo de reporte", allowableValues = {"perdido", "encontrado"}, example = "perdido")
    @NotBlank(message = "El tipo es requerido")
    @Pattern(regexp = "perdido|encontrado", message = "Tipo debe ser 'perdido' o 'encontrado'")
    private String tipo;

    @Schema(description = "Tipo de animal", allowableValues = {"perro", "gato", "otro"}, example = "perro")
    @NotBlank(message = "El animal es requerido")
    @Pattern(regexp = "perro|gato|otro", message = "Animal debe ser 'perro', 'gato' u 'otro'")
    private String animal;

    @Schema(description = "Nombre de la mascota", example = "Firulais")
    @Size(max = 100)
    private String nombre;

    @Schema(description = "Raza de la mascota", example = "Labrador")
    @Size(max = 100)
    private String raza;

    @Schema(description = "Color del pelaje", example = "café con manchas blancas")
    @Size(max = 100)
    private String color;

    @Schema(description = "Tamaño de la mascota", allowableValues = {"pequeño", "mediano", "grande"}, example = "mediano")
    @Pattern(regexp = "pequeño|mediano|grande|",
             message = "Tamaño debe ser 'pequeño', 'mediano' o 'grande'")
    private String tamano;

    @Schema(description = "Descripción adicional (máx. 600 caracteres)", example = "Lleva collar rojo, muy amigable.")
    @Size(max = 600, message = "La descripción no puede superar 600 caracteres")
    private String descripcion;

    @Schema(description = "Latitud del avistamiento (Región Metropolitana)", example = "-33.4489")
    @NotNull(message = "La latitud es requerida")
    @DecimalMin(value = "-34.6", message = "Latitud fuera de la Región Metropolitana")
    @DecimalMax(value = "-32.9", message = "Latitud fuera de la Región Metropolitana")
    private BigDecimal lat;

    @Schema(description = "Longitud del avistamiento (Región Metropolitana)", example = "-70.6693")
    @NotNull(message = "La longitud es requerida")
    @DecimalMin(value = "-71.8", message = "Longitud fuera de la Región Metropolitana")
    @DecimalMax(value = "-70.0", message = "Longitud fuera de la Región Metropolitana")
    private BigDecimal lng;

    @Schema(description = "Nombre de la zona o comuna", example = "Providencia")
    private String zona;
}
