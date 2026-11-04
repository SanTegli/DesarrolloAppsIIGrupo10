package ar.edu.uade.reclamos.aplicacion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Desde el PR 16, el alta es {@code multipart/form-data} con la parte {@code reclamo} (JSON) y la parte
 * {@code foto}. Las imágenes se generan en memoria: los tests no dependen de archivos.
 */
public final class Fotos {

    private Fotos() {
    }

    /** {@code POST /api/reclamos} con el JSON del reclamo y una foto JPEG válida. */
    public static MockMultipartHttpServletRequestBuilder alta(String reclamo) {
        return alta(reclamo, foto(jpeg(64, 48), MediaType.IMAGE_JPEG_VALUE));
    }

    public static MockMultipartHttpServletRequestBuilder alta(String reclamo, MockMultipartFile foto) {
        MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart("/api/reclamos")
                .file(new MockMultipartFile("reclamo", "", MediaType.APPLICATION_JSON_VALUE,
                        reclamo.getBytes(StandardCharsets.UTF_8)));
        return foto == null ? pedido : pedido.file(foto);
    }

    public static MockMultipartFile foto(byte[] contenido, String tipo) {
        return new MockMultipartFile("foto", "foto.jpg", tipo, contenido);
    }

    public static byte[] jpeg(int ancho, int alto) {
        return imagen(ancho, alto, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] png(int ancho, int alto) {
        return imagen(ancho, alto, "png", BufferedImage.TYPE_INT_ARGB);
    }

    public static byte[] gif(int ancho, int alto) {
        return imagen(ancho, alto, "gif", BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Un JPEG con un bloque EXIF (APP1) como el de un celular, con una marca reconocible en lugar de las
     * coordenadas del GPS.
     */
    public static byte[] jpegConExif(int ancho, int alto, String marca) {
        byte[] jpeg = jpeg(ancho, alto);
        byte[] carga = ("Exif\0\0" + marca).getBytes(StandardCharsets.ISO_8859_1);
        int largo = carga.length + 2;
        // Va después del APP0 (JFIF) que escribe ImageIO, como lo dejaría un editor de fotos.
        int despues = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            despues = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, despues);
        salida.write(0xFF);
        salida.write(0xE1); // APP1
        salida.write(largo >> 8);
        salida.write(largo & 0xFF);
        salida.write(carga, 0, carga.length);
        salida.write(jpeg, despues, jpeg.length - despues);
        return salida.toByteArray();
    }

    private static byte[] imagen(int ancho, int alto, String formato, int tipo) {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        Graphics2D lienzo = imagen.createGraphics();
        lienzo.setColor(new Color(30, 92, 72));
        lienzo.fillRect(0, 0, ancho / 2, alto);
        lienzo.setColor(new Color(240, 200, 60));
        lienzo.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
        lienzo.dispose();
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagen, formato, salida)) {
                throw new IllegalStateException("Sin codificador para " + formato);
            }
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
