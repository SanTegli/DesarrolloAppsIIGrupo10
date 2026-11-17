package ar.edu.uade.reclamos.aplicacion.rest;

import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Los dos tableros por la API, con totales contados a mano sobre este escenario (fechas relativas a hoy):
 *
 * <pre>
 * 1. Luminaria, Quilmes,    hace 40 días, ASIGNADO a Alumbrado -> pendiente y vencido; fuera de los 30 días
 * 2. Luminaria, Quilmes,    hace 1 hora,  ASIGNADO a Alumbrado -> pendiente
 * 3. Luminaria, Quilmes,    hace 1 hora,  RESUELTO
 * 4. Bache,     Quilmes,    hace 10 días, INGRESADO (sin área) -> pendiente y vencido (plazo de 5 días)
 * 5. Luminaria, Avellaneda, hace 2 horas, CERRADO (por el Alumbrado de Avellaneda)
 * 6. Bache,     Avellaneda, hace 3 horas, CANCELADO
 * </pre>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tablero;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TableroIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReclamoRepository reclamos;

    private record Datos(Long ana, Long carla, Long diego, Long elena, Long quilmes, Long avellaneda, Long luminaria,
                         Long bache, Long alumbrado) { }

    private Datos datos;

    @BeforeEach
    void escenario() {
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            for (String tabla : List.of("apoyos", "codigos_verificacion", "notificaciones", "historial_estados",
                    "reclamos", "area_categoria", "usuarios", "areas_municipales", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Municipio avellaneda = municipios.guardar(new Municipio("Avellaneda", "Buenos Aires"));
            Categoria luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));
            Categoria bache = categorias.guardar(new Categoria("Bache", "Pozo", 120, Prioridad.BAJA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarCategoria(luminaria);
            alumbrado = areas.guardar(alumbrado);
            AreaMunicipal obras = areas.guardar(new AreaMunicipal("Obras", "obras@example.org", quilmes));
            AreaMunicipal alumbradoAvellaneda = new AreaMunicipal("Alumbrado", "alumbrado.av@example.org", avellaneda);
            alumbradoAvellaneda.agregarCategoria(luminaria);
            alumbradoAvellaneda = areas.guardar(alumbradoAvellaneda);
            Ciudadano ana = (Ciudadano) usuarios.guardar(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org",
                    null));
            AgenteMunicipal carla = (AgenteMunicipal) usuarios.guardar(new AgenteMunicipal("25333444", "Carla",
                    "Gomez", "carla@example.org", null, alumbrado));
            Usuario diego = usuarios.guardar(new AgenteMunicipal("26444555", "Diego", "Sosa", "diego@example.org",
                    null, obras));
            Usuario elena = usuarios.guardar(new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
            AgenteMunicipal hugo = (AgenteMunicipal) usuarios.guardar(new AgenteMunicipal("29777888", "Hugo",
                    "Medina", "hugo@example.org", null, alumbradoAvellaneda));

            Reclamo uno = crear(ana, luminaria, quilmes, Duration.ofDays(40));
            uno.asignarArea(alumbrado, null, "Automática", uno.getFechaCreacion());
            Reclamo dos = crear(ana, luminaria, quilmes, Duration.ofHours(1));
            dos.asignarArea(alumbrado, null, "Automática", dos.getFechaCreacion());
            Reclamo tres = crear(ana, luminaria, quilmes, Duration.ofHours(1));
            tres.asignarArea(alumbrado, null, "Automática", tres.getFechaCreacion());
            tres.cambiarEstado(EstadoReclamo.EN_PROCESO, carla, null, tres.getFechaCreacion());
            tres.cambiarEstado(EstadoReclamo.RESUELTO, carla, null, tres.getFechaCreacion());
            Reclamo cuatro = crear(ana, bache, quilmes, Duration.ofDays(10));
            Reclamo cinco = crear(ana, luminaria, avellaneda, Duration.ofHours(2));
            cinco.asignarArea(alumbradoAvellaneda, null, "Automática", cinco.getFechaCreacion());
            cinco.cambiarEstado(EstadoReclamo.EN_PROCESO, hugo, null, cinco.getFechaCreacion());
            cinco.cambiarEstado(EstadoReclamo.RESUELTO, hugo, null, cinco.getFechaCreacion());
            cinco.cambiarEstado(EstadoReclamo.CERRADO, ana, null, cinco.getFechaCreacion());
            Reclamo seis = crear(ana, bache, avellaneda, Duration.ofHours(3));
            seis.cambiarEstado(EstadoReclamo.CANCELADO, ana, null, seis.getFechaCreacion());
            List.of(uno, dos, tres, cuatro, cinco, seis).forEach(reclamos::guardar);
            return new Datos(ana.getId(), carla.getId(), diego.getId(), elena.getId(), quilmes.getId(),
                    avellaneda.getId(), luminaria.getId(), bache.getId(), alumbrado.getId());
        });
    }

    @Test
    void elTableroPublicoNoPideSesionYCoincideConLaCuentaAMano() throws Exception {
        String respuesta = mvc.perform(get("/api/dashboard/publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.desde").value(LocalDate.now().minusDays(29).toString()))
                .andExpect(jsonPath("$.creadosEnPeriodo").value(5))
                .andExpect(jsonPath("$.pendientes").value(3))
                .andExpect(jsonPath("$.resueltos").value(1))
                .andExpect(jsonPath("$.cerrados").value(1))
                .andExpect(jsonPath("$.cancelados").value(1))
                .andExpect(jsonPath("$.rechazados").value(0))
                .andExpect(jsonPath("$.porcentajeAtendidos").value(40.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("Ana", "Perez", "ana@example.org", "REC-");
    }

    @Test
    void losFiltrosDeMunicipioCategoriaYPeriodo() throws Exception {
        mvc.perform(get("/api/dashboard/publico").param("municipioId", datos.quilmes().toString()))
                .andExpect(jsonPath("$.creadosEnPeriodo").value(3))
                .andExpect(jsonPath("$.pendientes").value(3))
                .andExpect(jsonPath("$.cerrados").value(0))
                .andExpect(jsonPath("$.porcentajeAtendidos").value(25.0));
        // Sin pendientes, resueltos ni cerrados: "sin casos", no un porcentaje.
        mvc.perform(get("/api/dashboard/publico").param("municipioId", datos.avellaneda().toString())
                        .param("categoriaId", datos.bache().toString()))
                .andExpect(jsonPath("$.cancelados").value(1))
                .andExpect(jsonPath("$.porcentajeAtendidos").value(nullValue()));
        mvc.perform(get("/api/dashboard/publico")
                        .param("desde", LocalDate.now().minusDays(45).toString())
                        .param("hasta", LocalDate.now().toString()))
                .andExpect(jsonPath("$.creadosEnPeriodo").value(6));
    }

    @Test
    void losParametrosInvalidosResponden400O404() throws Exception {
        mvc.perform(get("/api/dashboard/publico").param("desde", "2026-10-02").param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(get("/api/dashboard/publico").param("desde", "2025-01-01").param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/publico").param("desde", "ayer"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/publico").param("municipioId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAdministradorVeTodasLasAreasConVencidosPorFechaYSinAsignar() throws Exception {
        mvc.perform(get("/api/dashboard/gestion").with(como(datos.elena())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value(nullValue()))
                .andExpect(jsonPath("$.pendientes").value(3))
                .andExpect(jsonPath("$.vencidos").value(2))
                .andExpect(jsonPath("$.sinAsignar").value(1))
                .andExpect(jsonPath("$.porcentajeVencidos").value(66.7))
                .andExpect(jsonPath("$.filas", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].area.nombre").value("Alumbrado"))
                .andExpect(jsonPath("$.filas[0].municipio.nombre").value("Quilmes"))
                .andExpect(jsonPath("$.filas[0].pendientes").value(2))
                .andExpect(jsonPath("$.filas[0].vencidos").value(1))
                .andExpect(jsonPath("$.filas[1].area").value(nullValue()))
                .andExpect(jsonPath("$.filas[1].pendientes").value(1))
                .andExpect(jsonPath("$.filas[1].vencidos").value(1));
    }

    @Test
    void cadaAgenteVeSoloSuAreaYLosCiudadanosNoLoVen() throws Exception {
        mvc.perform(get("/api/dashboard/gestion").with(como(datos.carla())))
                .andExpect(jsonPath("$.area.id").value(datos.alumbrado()))
                .andExpect(jsonPath("$.pendientes").value(2))
                .andExpect(jsonPath("$.vencidos").value(1))
                .andExpect(jsonPath("$.sinAsignar").value(nullValue()))
                .andExpect(jsonPath("$.porcentajeVencidos").value(50.0))
                .andExpect(jsonPath("$.filas", hasSize(1)));
        mvc.perform(get("/api/dashboard/gestion").with(como(datos.diego())))
                .andExpect(jsonPath("$.pendientes").value(0))
                .andExpect(jsonPath("$.porcentajeVencidos").value(nullValue()))
                .andExpect(jsonPath("$.filas", hasSize(0)));
        mvc.perform(get("/api/dashboard/gestion").with(como(datos.ana())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get("/api/dashboard/gestion")).andExpect(status().isUnauthorized());
    }

    private static Reclamo crear(Ciudadano ciudadano, Categoria categoria, Municipio municipio, Duration hace) {
        Clock entonces = Clock.fixed(Instant.now().minus(hace), ZoneId.systemDefault());
        return new ReclamoFactory(entonces).crear(ciudadano, categoria, municipio, "Reclamo de prueba", "Belgrano 450");
    }
}
