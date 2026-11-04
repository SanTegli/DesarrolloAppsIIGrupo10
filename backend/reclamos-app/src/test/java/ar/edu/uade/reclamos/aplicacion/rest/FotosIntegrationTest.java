package ar.edu.uade.reclamos.aplicacion.rest;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Fotos.foto;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.aplicacion.Fotos;
import ar.edu.uade.reclamos.aplicacion.servicio.ReclamoService;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La foto del reclamo de punta a punta: alta multipart, validación, archivos en disco y acceso con los permisos
 * del detalle. Las fotos van a un directorio temporal propio, para poder revisar qué archivos quedan.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fotos;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FotosIntegrationTest {

    private static final Path DIRECTORIO = temporal();
    private static final String MARCA_GPS = "GPS-34.7086,-58.2797";

    @DynamicPropertySource
    static void directorioDeFotos(DynamicPropertyRegistry propiedades) {
        propiedades.add("reclamos.fotos.directorio", DIRECTORIO::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReclamoRepository reclamos;
    @Autowired ReclamoService servicio;

    private record Datos(Long ana, Long bruno, Long carla, Long diego, Long elena, Long categoria, Long barrio) { }

    private Datos datos;

    @BeforeEach
    void escenario() throws IOException {
        vaciar(DIRECTORIO);
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            for (String tabla : List.of("codigos_verificacion", "notificaciones", "historial_estados", "reclamos",
                    "area_barrio", "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias",
                    "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarBarrio(bernal);
            alumbrado.agregarCategoria(luminaria);
            alumbrado = areas.guardar(alumbrado);
            AreaMunicipal obras = areas.guardar(new AreaMunicipal("Obras", "obras@example.org", quilmes));
            Usuario ana = usuarios.guardar(verificado(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null)));
            Usuario bruno = usuarios.guardar(verificado(new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org",
                    null)));
            Usuario carla = usuarios.guardar(new AgenteMunicipal("25333444", "Carla", "Gomez", "carla@example.org",
                    null, alumbrado));
            Usuario diego = usuarios.guardar(new AgenteMunicipal("26444555", "Diego", "Sosa", "diego@example.org",
                    null, obras));
            Usuario elena = usuarios.guardar(new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
            return new Datos(ana.getId(), bruno.getId(), carla.getId(), diego.getId(), elena.getId(),
                    luminaria.getId(), bernal.getId());
        });
    }

    @Test
    void elAltaGuardaLaFotoSinMetadatosYLaSirveComoJpeg() throws Exception {
        byte[] original = Fotos.jpegConExif(80, 60, MARCA_GPS);
        String numero = numero(crear(datos.ana(), foto(original, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foto").value(containsString("/foto"))));

        MvcResult respuesta = mvc.perform(get("/api/reclamos/" + numero + "/foto").with(como(datos.ana())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andReturn();
        byte[] servida = respuesta.getResponse().getContentAsByteArray();
        assertThat(servida[0] & 0xFF).isEqualTo(0xFF);
        assertThat(servida[1] & 0xFF).isEqualTo(0xD8);
        String comoTexto = new String(servida, StandardCharsets.ISO_8859_1);
        assertThat(comoTexto).doesNotContain("Exif").doesNotContain(MARCA_GPS);
        assertThat(archivos(DIRECTORIO)).hasSize(1);
        assertThat(archivos(DIRECTORIO.resolve(".pendientes"))).isEmpty();
        String referencia = reclamos.buscarPorNumero(numero).orElseThrow().getFoto().orElseThrow();
        assertThat(referencia).matches("[0-9a-f]{32}\\.jpg").doesNotContain(numero);
    }

    @Test
    void unPngTambienSeAceptaYSeGuardaComoJpeg() throws Exception {
        String numero = numero(crear(datos.ana(), foto(Fotos.png(40, 40), MediaType.IMAGE_PNG_VALUE))
                .andExpect(status().isCreated()));

        mvc.perform(get("/api/reclamos/" + numero + "/foto").with(como(datos.ana())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE));
    }

    @Test
    void laFotoLaVeQuienPuedeVerElDetalle() throws Exception {
        String numero = numero(crear(datos.ana(), foto(Fotos.jpeg(40, 30), MediaType.IMAGE_JPEG_VALUE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.area.nombre").value("Alumbrado")));
        String ruta = "/api/reclamos/" + numero + "/foto";

        mvc.perform(get(ruta).with(como(datos.carla()))).andExpect(status().isOk());
        mvc.perform(get(ruta).with(como(datos.elena()))).andExpect(status().isOk());
        mvc.perform(get(ruta).with(como(datos.bruno()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get(ruta).with(como(datos.diego()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reclamos/REC-00000000/foto").with(como(datos.elena()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void sinFotoOConUnArchivoQueNoEsFotoNoSeCreaNada() throws Exception {
        crear(datos.ana(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("El reclamo necesita una foto del problema."));
        crear(datos.ana(), foto("no soy una foto".getBytes(StandardCharsets.UTF_8), MediaType.IMAGE_JPEG_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El archivo no es una imagen JPEG o PNG válida."));
        crear(datos.ana(), foto(Fotos.gif(10, 10), "image/gif")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La foto tiene que ser JPEG o PNG."));
        crear(datos.ana(), foto(new byte[0], MediaType.IMAGE_JPEG_VALUE)).andExpect(status().isBadRequest());

        assertThat(reclamos.buscarTodos()).isEmpty();
        assertThat(archivos(DIRECTORIO)).isEmpty();
        assertThat(archivos(DIRECTORIO.resolve(".pendientes"))).isEmpty();
    }

    @Test
    void unaFotoDeMasDeCincoMegasEs413() throws Exception {
        byte[] pesada = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(Fotos.jpeg(10, 10), 0, pesada, 0, 4);

        crear(datos.ana(), foto(pesada, MediaType.IMAGE_JPEG_VALUE)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
        assertThat(reclamos.buscarTodos()).isEmpty();
    }

    @Test
    void siElAltaFallaDespuesDeGuardarLaFotoElArchivoSeBorra() throws Exception {
        MockMultipartFile valida = foto(Fotos.jpeg(40, 30), MediaType.IMAGE_JPEG_VALUE);
        mvc.perform(alta(reclamo(Long.MAX_VALUE), valida).with(como(datos.ana())))
                .andExpect(status().isNotFound());
        crear(datos.carla(), valida).andExpect(status().isForbidden());

        assertThat(reclamos.buscarTodos()).isEmpty();
        assertThat(archivos(DIRECTORIO)).isEmpty();
        assertThat(archivos(DIRECTORIO.resolve(".pendientes"))).isEmpty();
    }

    @Test
    void unReclamoAnteriorALaFotoNoTieneFoto() throws Exception {
        String numero = servicio.crear(datos.ana(), datos.categoria(), datos.barrio(), "Farol apagado", "Belgrano 450")
                .getNumero();

        mvc.perform(get("/api/reclamos/" + numero).with(como(datos.ana()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value(nullValue()));
        mvc.perform(get("/api/reclamos/" + numero + "/foto").with(como(datos.ana())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El reclamo " + numero + " no tiene foto."));
    }

    private ResultActions crear(Long usuario, MockMultipartFile foto) throws Exception {
        return mvc.perform(alta(reclamo(datos.categoria()), foto).with(como(usuario)));
    }

    private String reclamo(Long categoria) {
        return """
                {"categoriaId": %d, "barrioId": %d, "descripcion": "Farol apagado frente a la plaza",
                 "direccion": "Belgrano 450"}""".formatted(categoria, datos.barrio());
    }

    private String numero(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("numero").asText();
    }

    /** Los archivos de un directorio, sin entrar en subdirectorios. */
    private static List<Path> archivos(Path directorio) throws IOException {
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> contenido = Files.list(directorio)) {
            return contenido.filter(Files::isRegularFile).toList();
        }
    }

    private static void vaciar(Path directorio) throws IOException {
        for (Path carpeta : List.of(directorio, directorio.resolve(".pendientes"))) {
            for (Path archivo : archivos(carpeta)) {
                Files.delete(archivo);
            }
        }
    }

    private static Path temporal() {
        try {
            return Files.createTempDirectory("reclamos-fotos-");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
