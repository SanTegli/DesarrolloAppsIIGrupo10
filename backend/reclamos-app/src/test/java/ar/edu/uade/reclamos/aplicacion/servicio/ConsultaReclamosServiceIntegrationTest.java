package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:consultas-por-rol;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ConsultaReclamosServiceIntegrationTest {
    @Autowired ConsultaReclamosService consultas;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReclamoRepository reclamos;
    @Autowired PlatformTransactionManager transacciones;

    @Test
    void consultasRespetanRolesFiltrosOrdenYNoDuplicanReclamos() {
        record Datos(Long ciudadano1, Long ciudadano2, Long agente1, Long agente2,
                     Long administrador, Long area1, Long area2, List<String> numeros) { }
        Datos datos = new TransactionTemplate(transacciones).execute(tx -> {
            Municipio municipio = municipios.guardar(new Municipio("Municipio consultas", "Buenos Aires"));
            Barrio barrio = barrios.guardar(new Barrio("Barrio consultas", municipio));
            Categoria categoria = categorias.guardar(new Categoria("Baches consultas", "Prueba", 24, Prioridad.ALTA));
            AreaMunicipal area1 = new AreaMunicipal("Area uno", "uno@test.org", municipio);
            AreaMunicipal area2 = new AreaMunicipal("Area dos", "dos@test.org", municipio);
            for (AreaMunicipal area : List.of(area1, area2)) {
                area.agregarBarrio(barrio);
                area.agregarCategoria(categoria);
                areas.guardar(area);
            }
            Ciudadano ciudadano1 = (Ciudadano) usuarios.guardar(
                    new Ciudadano("80111222", "Ana", "Consultas", "ana@test.org", null));
            Ciudadano ciudadano2 = (Ciudadano) usuarios.guardar(
                    new Ciudadano("80222333", "Luis", "Consultas", "luis@test.org", null));
            AgenteMunicipal agente1 = (AgenteMunicipal) usuarios.guardar(
                    new AgenteMunicipal("80333444", "Carla", "Consultas", "carla@test.org", null, area1));
            AgenteMunicipal agente2 = (AgenteMunicipal) usuarios.guardar(
                    new AgenteMunicipal("80444555", "Diego", "Consultas", "diego@test.org", null, area2));
            Usuario administrador = usuarios.guardar(
                    new Administrador("80555666", "Elena", "Consultas", "elena@test.org", null));

            Reclamo r1 = crear(ciudadano1, categoria, barrio, 3);
            Reclamo r2 = crear(ciudadano2, categoria, barrio, 1);
            Reclamo r3 = crear(ciudadano1, categoria, barrio, 2);
            Reclamo r4 = crear(ciudadano1, categoria, barrio, 3);
            Reclamo r5 = crear(ciudadano2, categoria, barrio, 2);
            Reclamo r6 = crear(ciudadano2, categoria, barrio, 4);
            LocalDateTime ahora = LocalDateTime.of(2026, 1, 5, 12, 0);
            for (Reclamo reclamo : List.of(r1, r2, r4)) {
                reclamo.asignarArea(area1, administrador, "Asignado", ahora);
            }
            for (Reclamo reclamo : List.of(r3, r5)) {
                reclamo.asignarArea(area2, administrador, "Asignado", ahora);
            }
            r4.cambiarEstado(EstadoReclamo.EN_PROCESO, agente1, "Tomado", ahora);
            r4.cambiarEstado(EstadoReclamo.RESUELTO, agente1, "Resuelto", ahora);
            r5.cambiarEstado(EstadoReclamo.EN_PROCESO, agente2, "Tomado", ahora);
            // El orden de insercion difiere del cronologico e incluye empates de fecha.
            List<Reclamo> creados = List.of(r1, r2, r3, r4, r5, r6);
            creados.forEach(reclamos::guardar);
            return new Datos(ciudadano1.getId(), ciudadano2.getId(), agente1.getId(), agente2.getId(),
                    administrador.getId(), area1.getId(), area2.getId(),
                    creados.stream().map(Reclamo::getNumero).toList());
        });

        String r1 = datos.numeros().get(0);
        String r2 = datos.numeros().get(1);
        String r3 = datos.numeros().get(2);
        String r4 = datos.numeros().get(3);
        String r5 = datos.numeros().get(4);
        String r6 = datos.numeros().get(5);
        // Todas las consultas ocurren despues del commit del escenario, sin una transaccion de test.
        assertThat(consultas.listar(datos.ciudadano1(), null, null, null))
                .extracting(Reclamo::getNumero).containsExactly(r4, r1, r3);
        assertThat(consultas.listar(datos.ciudadano2(), null, null, null))
                .extracting(Reclamo::getNumero).containsExactly(r6, r5, r2);
        assertThat(consultas.listar(datos.agente1(), null, null, null))
                .extracting(Reclamo::getNumero).containsExactly(r4, r1, r2);
        assertThat(consultas.listar(datos.agente2(), null, null, null))
                .extracting(Reclamo::getNumero).containsExactly(r5, r3);
        assertThat(consultas.listar(datos.administrador(), null, null, null))
                .extracting(Reclamo::getNumero).containsExactly(r6, r4, r1, r5, r3, r2);

        assertThat(consultas.listar(datos.administrador(), null, datos.area1(), datos.ciudadano1()))
                .extracting(Reclamo::getNumero).containsExactly(r4, r1);
        assertThat(consultas.listar(datos.administrador(), EstadoReclamo.ASIGNADO,
                datos.area1(), datos.ciudadano1()))
                .extracting(Reclamo::getNumero).containsExactly(r1);
        assertThat(consultas.listar(datos.administrador(), EstadoReclamo.ASIGNADO, datos.area1(), null))
                .extracting(Reclamo::getNumero).containsExactly(r1, r2);
        assertThat(consultas.listar(datos.administrador(), EstadoReclamo.ASIGNADO, null, datos.ciudadano1()))
                .extracting(Reclamo::getNumero).containsExactly(r1, r3);
        assertThat(consultas.listar(datos.administrador(), EstadoReclamo.RESUELTO,
                datos.area2(), datos.ciudadano1())).isEmpty();
        assertThat(consultas.listar(datos.administrador(), null, Long.MAX_VALUE, null)).isEmpty();
        assertThat(consultas.listar(datos.administrador(), null, null, Long.MAX_VALUE)).isEmpty();
        assertThat(consultas.listar(datos.administrador(), EstadoReclamo.ASIGNADO,
                datos.area1(), Long.MAX_VALUE)).isEmpty();
    }

    private Reclamo crear(Ciudadano ciudadano, Categoria categoria, Barrio barrio, int dia) {
        Clock reloj = Clock.fixed(Instant.parse("2026-01-0" + dia + "T12:00:00Z"), ZoneOffset.UTC);
        return new ReclamoFactory(reloj).crear(ciudadano, categoria, barrio, "Pozo", "Calle 450");
    }
}
