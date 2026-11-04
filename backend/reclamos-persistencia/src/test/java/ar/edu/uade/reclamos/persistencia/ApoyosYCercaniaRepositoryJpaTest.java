package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.ApoyoRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Apoyos (uno por ciudadano y reclamo) y la búsqueda de reclamos abiertos cerca de un punto. */
class ApoyosYCercaniaRepositoryJpaTest extends PersistenciaTestBase {
    private static final double LATITUD = -34.7361;
    private static final double LONGITUD = -58.3229;

    @Autowired ApoyoRepository apoyos;

    @Test
    void guardaCuentaBuscaYEliminaApoyos() {
        prepararDatos();
        Reclamo reclamo = reclamos.guardar(nuevoReclamo());
        Ciudadano bruno = otroCiudadano("31222333", "bruno@example.org");
        Ciudadano laura = otroCiudadano("33444555", "laura@example.org");
        apoyos.guardar(new Apoyo(bruno, reclamo, AHORA));
        apoyos.guardar(new Apoyo(laura, reclamo, AHORA));
        flushYLimpiar();

        assertThat(apoyos.contarPorReclamo(reclamo.getId())).isEqualTo(2);
        Apoyo deBruno = apoyos.buscar(bruno.getId(), reclamo.getId()).orElseThrow();
        assertThat(deBruno.getFecha()).isEqualTo(AHORA);
        assertThat(apoyos.buscar(ciudadano.getId(), reclamo.getId())).isEmpty();

        apoyos.eliminar(deBruno);
        flushYLimpiar();
        assertThat(apoyos.contarPorReclamo(reclamo.getId())).isEqualTo(1);
        assertThat(apoyos.buscar(bruno.getId(), reclamo.getId())).isEmpty();
    }

    @Test
    void unCiudadanoNoApoyaDosVecesElMismoReclamo() {
        prepararDatos();
        Reclamo reclamo = reclamos.guardar(nuevoReclamo());
        Ciudadano bruno = otroCiudadano("31222333", "bruno@example.org");
        apoyos.guardar(new Apoyo(bruno, reclamo, AHORA));

        assertThatThrownBy(() -> apoyos.guardar(new Apoyo(bruno, reclamo, AHORA.plusMinutes(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscaSoloReclamosAbiertosUbicadosEnElRecuadroYDeLaCategoria() {
        prepararDatos();
        Reclamo cerca = ubicado(LATITUD, LONGITUD);
        Reclamo lejos = ubicado(LATITUD + 0.01, LONGITUD);
        Reclamo sinUbicacion = reclamos.guardar(nuevoReclamo());
        Reclamo resuelto = ubicado(LATITUD, LONGITUD);
        resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, AHORA);
        resuelto.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, AHORA);
        reclamos.guardar(resuelto);
        Categoria otra = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));
        Reclamo otraCategoria = new ReclamoFactory(Clock.systemUTC()).crear(ciudadano, otra, barrio, "Farol",
                "Belgrano 450");
        otraCategoria.registrarUbicacion(Ubicacion.confirmada(LATITUD, LONGITUD, "BELGRANO 450", "georef"));
        reclamos.guardar(otraCategoria);
        flushYLimpiar();

        var encontrados = reclamos.buscarAbiertosConUbicacionEn(categoria.getId(), LATITUD - 0.001, LATITUD + 0.001,
                LONGITUD - 0.001, LONGITUD + 0.001);

        assertThat(encontrados).extracting(Reclamo::getNumero).containsExactly(cerca.getNumero());
        assertThat(encontrados.get(0).getUbicacion().getLatitud()).isEqualTo(LATITUD);
        assertThat(encontrados).extracting(Reclamo::getNumero)
                .doesNotContain(lejos.getNumero(), sinUbicacion.getNumero(), resuelto.getNumero());
    }

    private Reclamo ubicado(double latitud, double longitud) {
        Reclamo reclamo = nuevoReclamo();
        reclamo.asignarArea(area, null, "Asignación automática", AHORA);
        reclamo.registrarUbicacion(Ubicacion.confirmada(latitud, longitud, "BELGRANO 450, Bernal", "georef"));
        return reclamos.guardar(reclamo);
    }

    private Ciudadano otroCiudadano(String dni, String email) {
        return (Ciudadano) usuarios.guardar(new Ciudadano(dni, "Vecino", "De Prueba", email, null));
    }
}
