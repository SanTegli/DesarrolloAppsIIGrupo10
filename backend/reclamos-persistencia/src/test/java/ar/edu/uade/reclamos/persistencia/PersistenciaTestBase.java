package ar.edu.uade.reclamos.persistencia;

import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = ConfiguracionPersistenciaTest.class)
abstract class PersistenciaTestBase {
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 0);
    @Autowired EntityManager em;
    @Autowired ReclamoRepository reclamos;
    @Autowired UsuarioRepository usuarios;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired NotificacionRepository notificaciones;

    Municipio municipio;
    Barrio barrio;
    Categoria categoria;
    AreaMunicipal area;
    Ciudadano ciudadano;
    AgenteMunicipal agente;

    void prepararDatos() {
        municipio = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
        barrio = barrios.guardar(new Barrio("Bernal", municipio));
        categoria = categorias.guardar(new Categoria("Bache", "Pozo", 120, Prioridad.BAJA));
        area = new AreaMunicipal("Obras Públicas", "obras@example.org", municipio);
        area.agregarBarrio(barrio);
        area.agregarCategoria(categoria);
        area = areas.guardar(area);
        ciudadano = (Ciudadano) usuarios.guardar(
                new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null));
        agente = (AgenteMunicipal) usuarios.guardar(
                new AgenteMunicipal("25333444", "Carla", "Gómez", "carla@example.org", null, area));
    }

    Reclamo nuevoReclamo() {
        return new ReclamoFactory(Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC))
                .crear(ciudadano, categoria, barrio, "Pozo profundo", "Belgrano 450");
    }

    void flushYLimpiar() {
        em.flush();
        em.clear();
    }
}

