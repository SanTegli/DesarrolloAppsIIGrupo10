package ar.edu.uade.reclamos.aplicacion.estrategia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrioridadTest {
    @ParameterizedTest
    @EnumSource(Prioridad.class)
    void categoriaDevuelveSuBaseSinModificarElReclamo(Prioridad base) {
        Reclamo reclamo = reclamo(base, "Problema urgente");
        assertThat(new PrioridadPorCategoria().calcular(reclamo)).isEqualTo(PrioridadCalculada.porCategoria(base));
        assertThat(reclamo.getPrioridad()).isEqualTo(base);
        assertThat(reclamo.getHistorial()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"urgente", "peligro", "riesgo", "accidente", "¡URGENTE!", "Hay PELIGRO, actuar",
            "riesgo/accidente", "un accidente\nfrente a la plaza"})
    void palabrasCompletasElevanHastaAltaSinModificarElReclamo(String descripcion) {
        Reclamo reclamo = reclamo(Prioridad.BAJA, descripcion);
        PrioridadCalculada calculada = new PrioridadPorPalabrasClave().calcular(reclamo);
        assertThat(calculada.prioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(calculada.origen()).isEqualTo(OrigenPrioridad.PALABRAS_CLAVE);
        assertThat(calculada.motivo()).startsWith("La descripción menciona \"");
        assertThat(reclamo.getPrioridad()).isEqualTo(Prioridad.BAJA);
    }

    @ParameterizedTest
    @EnumSource(Prioridad.class)
    void palabrasNuncaReducenLaBase(Prioridad base) {
        assertThat(new PrioridadPorPalabrasClave().calcular(reclamo(base, "riesgo urgente")).prioridad())
                .isEqualTo(Prioridad.mayor(base, Prioridad.ALTA));
        assertThat(new PrioridadPorPalabrasClave().calcular(reclamo(base, "Farol apagado")))
                .isEqualTo(PrioridadCalculada.porCategoria(base));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accidental", "riesgoso", "urgentemente", "peligroso", "accidentes",
            "preurgente", "urgente123", "urgente_", "éurgente", "urgenteñ"})
    void noCoincidenFragmentosDeOtrasPalabras(String descripcion) {
        assertThat(new PrioridadPorPalabrasClave().calcular(reclamo(Prioridad.MEDIA, descripcion)))
                .isEqualTo(PrioridadCalculada.porCategoria(Prioridad.MEDIA));
    }

    @ParameterizedTest
    @EnumSource(value = Prioridad.class, names = {"ALTA", "CRITICA"})
    void siLaBaseYaEsAltaLaPrioridadSigueSiendoDeLaCategoria(Prioridad base) {
        assertThat(new PrioridadPorPalabrasClave().calcular(reclamo(base, "Hay peligro")))
                .isEqualTo(PrioridadCalculada.porCategoria(base));
    }

    public static Reclamo reclamo(Prioridad base, String descripcion) {
        Municipio municipio = new Municipio("Quilmes", "Buenos Aires");
        Barrio barrio = new Barrio("Bernal", municipio);
        Categoria categoria = new Categoria("Bache", "Pozo", 48, base);
        Ciudadano ciudadano = new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null);
        return new ReclamoFactory().crear(ciudadano, categoria, barrio, descripcion, "Belgrano 450");
    }
}
