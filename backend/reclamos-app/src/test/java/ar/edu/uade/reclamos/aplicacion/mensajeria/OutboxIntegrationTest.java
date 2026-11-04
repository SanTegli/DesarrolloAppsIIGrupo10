package ar.edu.uade.reclamos.aplicacion.mensajeria;

import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La outbox de punta a punta con la base real (H2): el alta de un reclamo deja un mensaje pendiente por cada
 * aviso, en la misma transacción, y el publicador los marca publicados. El broker es un doble
 * ({@link MockitoBean}): la conexión real con RabbitMQ la prueba {@code PublicacionRabbitIntegrationTest}.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.mensajeria.habilitada=true", "reclamos.mensajeria.intervalo-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired MensajeSalienteRepository mensajes;
    @Autowired PublicadorMensajes publicador;
    @MockitoBean BrokerMensajes broker;

    private Long barrio;
    private Long categoria;
    private Long ciudadano;
    private Long agente;

    @BeforeEach
    void escenario() {
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("mensajes_salientes", "notificaciones", "historial_estados", "reclamos",
                    "area_barrio", "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias",
                    "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria luminaria = categorias.guardar(
                    new Categoria("Luminaria rota", "Farol apagado o dañado", 48, Prioridad.MEDIA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarBarrio(bernal);
            alumbrado.agregarCategoria(luminaria);
            areas.guardar(alumbrado);
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    verificado(new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", "1155550000")));
            AgenteMunicipal carla = (AgenteMunicipal) usuarios.guardar(
                    new AgenteMunicipal("30333444", "Carla", "Gómez", "carla@example.org", null, alumbrado));
            barrio = bernal.getId();
            categoria = luminaria.getId();
            ciudadano = ana.getId();
            agente = carla.getId();
        });
    }

    @AfterEach
    void limpiarDoble() {
        reset(broker);
    }

    @Test
    void elAltaDejaUnMensajePendientePorCadaAvisoConElContrato() throws Exception {
        crearReclamo(ciudadano).andExpect(status().isCreated());

        // Ingreso al ciudadano; asignación al ciudadano y a la agente del área.
        List<MensajeSaliente> pendientes = mensajes.buscarPendientes(10);
        assertThat(pendientes).extracting(MensajeSaliente::getClaveRuteo).containsExactly(
                "aviso.reclamo-creado", "aviso.reclamo-asignado", "aviso.reclamo-asignado");
        JsonNode asignacion = json.readTree(pendientes.get(2).getContenido());
        assertThat(asignacion.get("tipoEvento").asText()).isEqualTo("ReclamoAsignado");
        assertThat(asignacion.at("/destinatario/email").asText()).isEqualTo("carla@example.org");
        assertThat(asignacion.get("fechaEvento").asText()).matches(".*[+-]\\d{2}:\\d{2}$|.*Z$");
        assertThat(pendientes).allSatisfy(mensaje -> assertThat(mensaje.getContenido())
                .doesNotContain("30111222", "1155550000", "Farol apagado frente a la plaza"));
    }

    @Test
    void siElAltaFallaNoQuedaNingunMensaje() throws Exception {
        crearReclamo(agente).andExpect(status().isForbidden());

        assertThat(mensajes.contarPorEstado(EstadoMensaje.PENDIENTE)).isZero();
    }

    @Test
    void elPublicadorLosMandaAlBrokerYLosMarcaPublicados() throws Exception {
        crearReclamo(ciudadano).andExpect(status().isCreated());

        assertThat(publicador.publicarPendientes()).isEqualTo(3);

        verify(broker, times(3)).publicar(any());
        assertThat(mensajes.contarPorEstado(EstadoMensaje.PENDIENTE)).isZero();
        assertThat(mensajes.contarPorEstado(EstadoMensaje.PUBLICADO)).isEqualTo(3);
    }

    @Test
    void conElBrokerCaidoLosMensajesEsperanYSalenCuandoVuelve() throws Exception {
        crearReclamo(ciudadano).andExpect(status().isCreated());
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(broker).publicar(any());

        assertThat(publicador.publicarPendientes()).isZero();
        MensajeSaliente primero = mensajes.buscarPendientes(1).get(0);
        assertThat(primero.getIntentos()).isEqualTo(1);
        assertThat(primero.getUltimoError()).contains("Connection refused");

        reset(broker);
        assertThat(publicador.publicarPendientes()).isEqualTo(3);
        assertThat(mensajes.contarPorEstado(EstadoMensaje.PENDIENTE)).isZero();
    }

    private ResultActions crearReclamo(Long usuario) throws Exception {
        return mvc.perform(alta("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Farol apagado frente a la plaza",
                         "direccion": "Belgrano 450"}""".formatted(categoria, barrio)).with(como(usuario)));
    }
}
