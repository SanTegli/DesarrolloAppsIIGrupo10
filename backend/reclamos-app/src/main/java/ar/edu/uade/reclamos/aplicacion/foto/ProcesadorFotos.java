package ar.edu.uade.reclamos.aplicacion.foto;

import ar.edu.uade.reclamos.comun.excepcion.ArchivoDemasiadoGrandeException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Valida la foto y la re-codifica a JPEG.
 *
 * <ul>
 *   <li>El formato sale del contenido, no del nombre ni del tipo declarado: solo JPEG o PNG.</li>
 *   <li>Las dimensiones se leen del encabezado antes de decodificar, para no gastar memoria en una imagen
 *   gigante.</li>
 *   <li>Re-codificar descarta los metadatos: el EXIF, con la ubicación del GPS y el modelo del teléfono, no
 *   se guarda.</li>
 * </ul>
 */
public class ProcesadorFotos {

    private static final Set<String> FORMATOS = Set.of("jpeg", "png");

    static {
        // Sin archivos temporales de ImageIO: todo en memoria, acotado por el tamaño máximo.
        ImageIO.setUseCache(false);
    }

    private final long tamanoMaximo;
    private final int ladoMaximo;
    private final float calidad;

    public ProcesadorFotos(long tamanoMaximo, int ladoMaximo, float calidad) {
        this.tamanoMaximo = tamanoMaximo;
        this.ladoMaximo = ladoMaximo;
        this.calidad = calidad;
    }

    /** @return la foto como JPEG, sin metadatos */
    public byte[] procesar(FotoSubida foto) {
        byte[] datos = foto == null ? null : foto.contenido();
        if (datos == null || datos.length == 0) {
            throw new DatosInvalidosException("La foto está vacía.");
        }
        if (datos.length > tamanoMaximo) {
            throw new ArchivoDemasiadoGrandeException("La foto pesa más de " + megas(tamanoMaximo)
                    + ". Sacala con menos resolución o recortala.");
        }
        try {
            return codificar(aRgb(leer(datos)));
        } catch (IOException | RuntimeException error) {
            if (error instanceof DatosInvalidosException || error instanceof ArchivoDemasiadoGrandeException) {
                throw (RuntimeException) error;
            }
            throw new DatosInvalidosException("El archivo no es una imagen JPEG o PNG válida.");
        }
    }

    private BufferedImage leer(byte[] datos) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new DatosInvalidosException("El archivo no es una imagen JPEG o PNG válida.");
            }
            ImageReader lector = lectores.next();
            try {
                if (!FORMATOS.contains(lector.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new DatosInvalidosException("La foto tiene que ser JPEG o PNG.");
                }
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho > ladoMaximo || alto > ladoMaximo) {
                    throw new ArchivoDemasiadoGrandeException("La foto mide " + ancho + " × " + alto
                            + " píxeles; el máximo es " + ladoMaximo + " × " + ladoMaximo + ".");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        }
    }

    /** JPEG no tiene transparencia: un PNG transparente queda sobre fondo blanco. */
    private static BufferedImage aRgb(BufferedImage original) {
        BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = rgb.createGraphics();
        try {
            lienzo.setColor(Color.WHITE);
            lienzo.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            lienzo.drawImage(original, 0, 0, null);
        } finally {
            lienzo.dispose();
        }
        return rgb;
    }

    private byte[] codificar(BufferedImage imagen) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(calidad);
            // Sin metadatos: el JPEG sale solo con el encabezado JFIF.
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static String megas(long bytes) {
        long megas = bytes / (1024 * 1024);
        return megas > 0 ? megas + " MB" : bytes / 1024 + " KB";
    }
}
