package ar.edu.uade.reclamos.aplicacion.foto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El almacén en disco: pendiente, confirmada o descartada, y nada fuera de su directorio. */
class AlmacenFotosLocalTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    @TempDir
    Path directorio;

    @Test
    void unaFotoPendienteNoSeVeHastaConfirmarla() {
        AlmacenFotosLocal almacen = new AlmacenFotosLocal(directorio);
        String referencia = almacen.guardarPendiente(JPEG);

        assertTrue(referencia.matches("[0-9a-f]{32}\\.jpg"));
        assertEquals(Optional.empty(), almacen.leer(referencia));

        almacen.confirmar(referencia);

        assertArrayEquals(JPEG, almacen.leer(referencia).orElseThrow());
        assertTrue(Files.isRegularFile(directorio.resolve(referencia)));
    }

    @Test
    void descartarBorraLaPendienteYNoDejaArchivos() throws IOException {
        AlmacenFotosLocal almacen = new AlmacenFotosLocal(directorio);
        String referencia = almacen.guardarPendiente(JPEG);

        almacen.descartar(referencia);
        almacen.descartar(referencia);

        assertEquals(Optional.empty(), almacen.leer(referencia));
        try (Stream<Path> archivos = Files.walk(directorio)) {
            assertFalse(archivos.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void cadaFotoTieneUnaReferenciaDistinta() {
        AlmacenFotosLocal almacen = new AlmacenFotosLocal(directorio);

        assertNotEquals(almacen.guardarPendiente(JPEG), almacen.guardarPendiente(JPEG));
    }

    @Test
    void unaReferenciaRaraNoSaleDelDirectorio() throws IOException {
        Files.writeString(directorio.resolve("secreto.txt"), "no");
        AlmacenFotosLocal almacen = new AlmacenFotosLocal(directorio.resolve("fotos"));

        assertEquals(Optional.empty(), almacen.leer("../secreto.txt"));
        assertEquals(Optional.empty(), almacen.leer(null));
        assertEquals(Optional.empty(), almacen.leer("0123456789abcdef0123456789abcdef.png"));
        assertThrows(IllegalArgumentException.class, () -> almacen.confirmar("../secreto.txt"));
        assertThrows(IllegalArgumentException.class, () -> almacen.descartar("../../etc/passwd"));
    }
}
