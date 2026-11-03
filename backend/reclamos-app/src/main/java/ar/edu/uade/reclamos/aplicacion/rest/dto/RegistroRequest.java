package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un ciudadano. El email es el nombre de usuario; se guarda en minúsculas. */
public record RegistroRequest(
        @Schema(example = "32111222") @NotBlank @Pattern(regexp = "\\d{7,8}", message = "debe tener 7 u 8 dígitos")
        String dni,
        @Schema(example = "Laura") @NotBlank @Size(max = 80) String nombre,
        @Schema(example = "Martínez") @NotBlank @Size(max = 80) String apellido,
        @Schema(example = "laura@example.org") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "una-clave-larga") @NotBlank @Size(min = 8, max = 72) String contrasena) { }
