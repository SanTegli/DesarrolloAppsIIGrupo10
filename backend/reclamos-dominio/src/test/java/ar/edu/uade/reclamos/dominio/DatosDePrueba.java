package ar.edu.uade.reclamos.dominio;

import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;

/**
 * Escenario mínimo compartido por los tests del dominio: el municipio de Quilmes con dos áreas.
 * Alumbrado atiende "Luminaria rota" en Bernal y Quilmes Centro; Obras Públicas atiende "Bache" en Bernal.
 * Ezpeleta no tiene ningún área con jurisdicción.
 */
public class DatosDePrueba {

    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 0);

    public final Municipio quilmes = conId(new Municipio("Quilmes", "Buenos Aires"), 1L);

    public final Barrio bernal = conId(new Barrio("Bernal", quilmes), 1L);
    public final Barrio quilmesCentro = conId(new Barrio("Quilmes Centro", quilmes), 2L);
    public final Barrio ezpeleta = conId(new Barrio("Ezpeleta", quilmes), 3L);

    public final Categoria luminaria =
            conId(new Categoria("Luminaria rota", "Farol apagado o dañado", 48, Prioridad.MEDIA), 1L);
    public final Categoria bache =
            conId(new Categoria("Bache", "Pozo en la calzada", 120, Prioridad.BAJA), 2L);

    public final AreaMunicipal alumbrado =
            conId(new AreaMunicipal("Alumbrado", "alumbrado@quilmes.gob.ar", quilmes), 1L);
    public final AreaMunicipal obrasPublicas =
            conId(new AreaMunicipal("Obras Públicas", "obras@quilmes.gob.ar", quilmes), 2L);

    public final Ciudadano ana =
            conId(new Ciudadano("30111222", "Ana", "Pérez", "ana.perez@mail.com", "1140001111"), 1L);
    public final Ciudadano bruno =
            conId(new Ciudadano("31222333", "Bruno", "Díaz", "bruno.diaz@mail.com", null), 2L);
    public final AgenteMunicipal agenteAlumbrado = conId(new AgenteMunicipal(
            "25333444", "Carla", "Gómez", "carla.gomez@quilmes.gob.ar", null, alumbrado), 3L);
    public final AgenteMunicipal agenteObras = conId(new AgenteMunicipal(
            "26444555", "Diego", "Sosa", "diego.sosa@quilmes.gob.ar", null, obrasPublicas), 4L);
    public final Administrador admin = conId(new Administrador(
            "20555666", "Elena", "Ruiz", "elena.ruiz@quilmes.gob.ar", null), 5L);

    public DatosDePrueba() {
        alumbrado.agregarCategoria(luminaria);
        alumbrado.agregarBarrio(bernal);
        alumbrado.agregarBarrio(quilmesCentro);
        obrasPublicas.agregarCategoria(bache);
        obrasPublicas.agregarBarrio(bernal);
    }

    /** Reclamo de Ana por una luminaria en Bernal, recién ingresado, con 48 horas de plazo. */
    public Reclamo reclamoIngresado() {
        return new Reclamo("REC-TEST0001", ana, luminaria, bernal, "Farol apagado frente a la plaza",
                "Belgrano 450", Prioridad.MEDIA, AHORA, AHORA.plusHours(48));
    }

    /** El mismo reclamo, ya asignado automáticamente a Alumbrado. */
    public Reclamo reclamoAsignado() {
        Reclamo reclamo = reclamoIngresado();
        reclamo.asignarArea(alumbrado, null, "Asignación automática", AHORA);
        return reclamo;
    }

    private static <T extends ar.edu.uade.reclamos.dominio.modelo.Entidad> T conId(T entidad, Long id) {
        entidad.setId(id);
        return entidad;
    }
}
