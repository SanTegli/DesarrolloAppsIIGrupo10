package ar.edu.uade.reclamos.dominio;

import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;

/**
 * Escenario mínimo compartido por los tests del dominio: dos municipios. Quilmes tiene Alumbrado, que atiende
 * "Luminaria rota", y Obras Públicas, que atiende "Bache". Avellaneda solo tiene su propia Obras Públicas: una
 * luminaria en Avellaneda no tiene área.
 */
public class DatosDePrueba {

    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 0);

    public final Municipio quilmes = conId(new Municipio("Quilmes", "Buenos Aires"), 1L);

    public final Municipio avellaneda = conId(new Municipio("Avellaneda", "Buenos Aires"), 2L);

    public final Categoria luminaria =
            conId(new Categoria("Luminaria rota", "Farol apagado o dañado", 48, Prioridad.MEDIA), 1L);
    public final Categoria bache =
            conId(new Categoria("Bache", "Pozo en la calzada", 120, Prioridad.BAJA), 2L);

    public final AreaMunicipal alumbrado =
            conId(new AreaMunicipal("Alumbrado", "alumbrado@quilmes.gob.ar", quilmes), 1L);
    public final AreaMunicipal obrasPublicas =
            conId(new AreaMunicipal("Obras Públicas", "obras@quilmes.gob.ar", quilmes), 2L);
    public final AreaMunicipal obrasAvellaneda =
            conId(new AreaMunicipal("Obras Públicas", "obras@avellaneda.gob.ar", avellaneda), 3L);

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
        obrasPublicas.agregarCategoria(bache);
        obrasAvellaneda.agregarCategoria(bache);
    }

    /** Reclamo de Ana por una luminaria en Quilmes, recién ingresado, con 48 horas de plazo. */
    public Reclamo reclamoIngresado() {
        return new Reclamo("REC-TEST0001", ana, luminaria, quilmes, "Farol apagado frente a la plaza",
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
