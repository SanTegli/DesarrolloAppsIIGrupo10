package ar.edu.uade.reclamos.aplicacion.foto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fotos en un directorio local. Las pendientes van a {@code .pendientes/} y se mueven al confirmar: en el
 * mismo disco el movimiento es atómico, así nunca se lee una foto a medio escribir.
 *
 * <p>La referencia es un nombre aleatorio de 32 caracteres hexadecimales más {@code .jpg}. Cualquier otra
 * cosa se rechaza antes de tocar el disco, así una referencia no puede salirse del directorio.
 */
public class AlmacenFotosLocal implements AlmacenFotos {

    private static final Pattern REFERENCIA = Pattern.compile("[0-9a-f]{32}\\.jpg");

    private final Path directorio;
    private final Path pendientes;

    public AlmacenFotosLocal(Path directorio) {
        this.directorio = directorio.toAbsolutePath().normalize();
        this.pendientes = this.directorio.resolve(".pendientes");
        try {
            Files.createDirectories(pendientes);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo crear el directorio de fotos " + this.directorio, error);
        }
    }

    public Path getDirectorio() {
        return directorio;
    }

    @Override
    public String guardarPendiente(byte[] jpeg) {
        String referencia = UUID.randomUUID().toString().replace("-", "") + ".jpg";
        try {
            Files.write(pendientes.resolve(referencia), jpeg, StandardOpenOption.CREATE_NEW);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo guardar la foto", error);
        }
        return referencia;
    }

    @Override
    public void confirmar(String referencia) {
        String valida = validar(referencia);
        try {
            Files.move(pendientes.resolve(valida), directorio.resolve(valida), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo confirmar la foto " + valida, error);
        }
    }

    @Override
    public void descartar(String referencia) {
        String valida = validar(referencia);
        try {
            Files.deleteIfExists(pendientes.resolve(valida));
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo borrar la foto pendiente " + valida, error);
        }
    }

    @Override
    public Optional<byte[]> leer(String referencia) {
        if (referencia == null || !REFERENCIA.matcher(referencia).matches()) {
            return Optional.empty();
        }
        Path archivo = directorio.resolve(referencia);
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(archivo));
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer la foto " + referencia, error);
        }
    }

    private static String validar(String referencia) {
        if (referencia == null || !REFERENCIA.matcher(referencia).matches()) {
            throw new IllegalArgumentException("Referencia de foto inválida");
        }
        return referencia;
    }
}
