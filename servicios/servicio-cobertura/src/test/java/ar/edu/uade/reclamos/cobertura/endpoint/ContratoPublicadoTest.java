package ar.edu.uade.reclamos.cobertura.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * El XSD que usa el servicio es el mismo que se publica en {@code docs/contratos/soap/}: el backend y la
 * documentación se apoyan en esa copia.
 */
class ContratoPublicadoTest {

    @Test
    void elXsdDelServicioEsElDeDocs() throws IOException {
        String delServicio = leer(Path.of("src/main/resources/cobertura.xsd"));
        String deDocs = leer(Path.of("../../docs/contratos/soap/cobertura.xsd"));

        assertThat(deDocs).as("docs/contratos/soap/cobertura.xsd debe ser idéntico al del servicio")
                .isEqualTo(delServicio);
    }

    /** Ignora la diferencia de fin de línea entre Windows y Linux. */
    private static String leer(Path archivo) throws IOException {
        return Files.readString(archivo, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
