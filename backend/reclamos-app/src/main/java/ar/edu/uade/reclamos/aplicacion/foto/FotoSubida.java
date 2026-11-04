package ar.edu.uade.reclamos.aplicacion.foto;

/**
 * Los bytes de la foto tal como llegaron. El tipo que declara el navegador no se usa: el formato se
 * reconoce por el contenido.
 */
public record FotoSubida(byte[] contenido) {
}
