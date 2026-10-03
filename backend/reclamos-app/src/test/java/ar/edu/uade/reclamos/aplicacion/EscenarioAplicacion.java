package ar.edu.uade.reclamos.aplicacion;

import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public class EscenarioAplicacion {
    public final Clock reloj = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);
    public final ReclamoFactory factory = new ReclamoFactory(reloj);
    public final Municipio municipio = id(new Municipio("Quilmes", "Buenos Aires"), 1L);
    public final Barrio barrio = id(new Barrio("Bernal", municipio), 1L);
    public final Categoria categoria = id(new Categoria("Bache", "Pozo", 48, Prioridad.BAJA), 1L);
    public final AreaMunicipal area = id(new AreaMunicipal("Obras", "obras@example.org", municipio), 1L);
    public final AreaMunicipal otraArea = id(new AreaMunicipal("Vial", "vial@example.org", municipio), 2L);
    public final Ciudadano ciudadano = id(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null), 1L);
    public final Ciudadano otroCiudadano = id(new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org", null), 2L);
    public final AgenteMunicipal agente = id(new AgenteMunicipal("25333444", "Carla", "Gomez",
            "carla@example.org", null, area), 3L);
    public final AgenteMunicipal otroAgente = id(new AgenteMunicipal("26444555", "Diego", "Sosa",
            "diego@example.org", null, otraArea), 4L);
    public final Administrador administrador = id(new Administrador("20555666", "Elena", "Ruiz",
            "elena@example.org", null), 5L);

    public Reclamo ingresado() {
        return factory.crear(ciudadano, categoria, barrio, "Pozo en la calle", "Belgrano 450");
    }

    public Reclamo asignado() {
        Reclamo reclamo = ingresado();
        reclamo.asignarArea(area, null, "Automática", java.time.LocalDateTime.now(reloj));
        return reclamo;
    }

    public Reclamo resuelto() {
        Reclamo reclamo = asignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, java.time.LocalDateTime.now(reloj));
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, java.time.LocalDateTime.now(reloj));
        return reclamo;
    }

    private static <T extends Entidad> T id(T entidad, Long id) {
        entidad.setId(id);
        return entidad;
    }
}
