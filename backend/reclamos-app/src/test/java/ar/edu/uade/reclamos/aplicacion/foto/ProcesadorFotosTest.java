package ar.edu.uade.reclamos.aplicacion.foto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.Fotos;
import ar.edu.uade.reclamos.comun.excepcion.ArchivoDemasiadoGrandeException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Validación y re-codificación de la foto, sin Spring. */
class ProcesadorFotosTest {

    private static final long CINCO_MB = 5L * 1024 * 1024;
    private final ProcesadorFotos procesador = new ProcesadorFotos(CINCO_MB, 4000, 0.85f);

    @Test
    void unJpegSaleComoJpegConLasMismasDimensiones() throws IOException {
        byte[] salida = procesador.procesar(new FotoSubida(Fotos.jpeg(64, 48)));

        assertEquals(0xFF, salida[0] & 0xFF);
        assertEquals(0xD8, salida[1] & 0xFF);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(salida));
        assertEquals(64, imagen.getWidth());
        assertEquals(48, imagen.getHeight());
    }

    @Test
    void unPngTransparenteSeConvierteAJpeg() throws IOException {
        byte[] salida = procesador.procesar(new FotoSubida(Fotos.png(30, 20)));

        assertEquals(0xD8, salida[1] & 0xFF);
        assertEquals(30, ImageIO.read(new ByteArrayInputStream(salida)).getWidth());
    }

    @Test
    void reCodificarBorraElExifConLaUbicacion() {
        byte[] original = Fotos.jpegConExif(40, 30, "GPS-34.7086,-58.2797");
        assertTrue(contiene(original, "GPS-34.7086"), "la foto de prueba tiene que traer el EXIF");

        byte[] salida = procesador.procesar(new FotoSubida(original));

        assertFalse(contiene(salida, "Exif"));
        assertFalse(contiene(salida, "GPS-34.7086"));
    }

    @Test
    void unArchivoQueNoEsImagenSeRechazaAunqueSeLlameFotoJpg() {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                () -> procesador.procesar(new FotoSubida("no soy una foto".getBytes(StandardCharsets.UTF_8))));
        assertEquals("El archivo no es una imagen JPEG o PNG válida.", error.getMessage());
    }

    @Test
    void unJpegCortadoSeRechaza() {
        byte[] jpeg = Fotos.jpeg(64, 48);
        byte[] cortado = java.util.Arrays.copyOf(jpeg, 40);

        assertThrows(DatosInvalidosException.class, () -> procesador.procesar(new FotoSubida(cortado)));
    }

    @Test
    void otrosFormatosDeImagenNoSeAceptan() {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                () -> procesador.procesar(new FotoSubida(Fotos.gif(10, 10))));
        assertEquals("La foto tiene que ser JPEG o PNG.", error.getMessage());
    }

    @Test
    void unaFotoVaciaSeRechaza() {
        assertThrows(DatosInvalidosException.class, () -> procesador.procesar(new FotoSubida(new byte[0])));
        assertThrows(DatosInvalidosException.class, () -> procesador.procesar(null));
    }

    @Test
    void elPesoYLasDimensionesTienenLimite() {
        ProcesadorFotos chico = new ProcesadorFotos(1024, 50, 0.85f);

        ArchivoDemasiadoGrandeException pesada = assertThrows(ArchivoDemasiadoGrandeException.class,
                () -> chico.procesar(new FotoSubida(new byte[2048])));
        assertTrue(pesada.getMessage().contains("1 KB"));
        ArchivoDemasiadoGrandeException enorme = assertThrows(ArchivoDemasiadoGrandeException.class,
                () -> chico.procesar(new FotoSubida(Fotos.png(51, 10))));
        assertTrue(enorme.getMessage().contains("51 × 10"));
    }

    @Test
    void elMismoArchivoDaElMismoResultado() {
        byte[] jpeg = Fotos.jpeg(20, 20);

        assertArrayEquals(procesador.procesar(new FotoSubida(jpeg)), procesador.procesar(new FotoSubida(jpeg)));
    }

    private static boolean contiene(byte[] datos, String texto) {
        return new String(datos, StandardCharsets.ISO_8859_1).contains(texto);
    }
}
