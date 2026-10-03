package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReclamoTest {

    private DatosDePrueba datos;

    @BeforeEach
    void preparar() {
        datos = new DatosDePrueba();
    }

    @Test
    @DisplayName("Un reclamo nuevo nace INGRESADO, sin área ni agente y con un registro de historial")
    void estadoInicial() {
        Reclamo reclamo = datos.reclamoIngresado();

        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
        assertNull(reclamo.getArea());
        assertNull(reclamo.getAgente());
        assertFalse(reclamo.isVencido());
        assertEquals(1, reclamo.getHistorial().size());
        HistorialEstado ingreso = reclamo.getHistorial().get(0);
        assertNull(ingreso.getEstadoAnterior());
        assertEquals(EstadoReclamo.INGRESADO, ingreso.getEstadoNuevo());
        assertSame(datos.ana, ingreso.getUsuario());
    }

    @Test
    @DisplayName("Datos obligatorios faltantes o inválidos se rechazan al construir")
    void validaDatosAlConstruir() {
        assertThrows(DatosInvalidosException.class, () -> new Reclamo("REC-1", datos.ana, datos.luminaria,
                datos.bernal, "   ", "Belgrano 450", Prioridad.MEDIA, AHORA, AHORA.plusHours(1)));
        assertThrows(DatosInvalidosException.class, () -> new Reclamo("REC-1", datos.ana, datos.luminaria,
                datos.bernal, "Farol apagado", null, Prioridad.MEDIA, AHORA, AHORA.plusHours(1)));
        assertThrows(DatosInvalidosException.class, () -> new Reclamo("REC-1", null, datos.luminaria,
                datos.bernal, "Farol apagado", "Belgrano 450", Prioridad.MEDIA, AHORA, AHORA.plusHours(1)));
        assertThrows(DatosInvalidosException.class, () -> new Reclamo("REC-1", datos.ana, datos.luminaria,
                datos.bernal, "Farol apagado", "Belgrano 450", Prioridad.MEDIA, AHORA, AHORA.minusHours(1)));
        assertThrows(DatosInvalidosException.class, () -> new Reclamo("REC-1", datos.ana, datos.luminaria,
                datos.bernal, "x".repeat(Reclamo.LONGITUD_MAXIMA_DESCRIPCION + 1), "Belgrano 450",
                Prioridad.MEDIA, AHORA, AHORA.plusHours(1)));
    }

    @Test
    @DisplayName("La asignación automática deja el reclamo ASIGNADO y el historial sin usuario")
    void asignacionAutomatica() {
        Reclamo reclamo = datos.reclamoIngresado();

        reclamo.asignarArea(datos.alumbrado, null, "Asignación automática", AHORA);

        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertSame(datos.alumbrado, reclamo.getArea());
        assertEquals(2, reclamo.getHistorial().size());
        HistorialEstado asignacion = reclamo.getHistorial().get(1);
        assertEquals(EstadoReclamo.INGRESADO, asignacion.getEstadoAnterior());
        assertEquals(EstadoReclamo.ASIGNADO, asignacion.getEstadoNuevo());
        assertTrue(asignacion.fueAutomatico());
    }

    @Test
    @DisplayName("Flujo principal: ASIGNADO, EN_PROCESO, RESUELTO, CERRADO con un registro por cambio")
    void flujoPrincipal() {
        Reclamo reclamo = datos.reclamoAsignado();

        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, "Cuadrilla en camino", AHORA.plusHours(2));
        assertEquals(EstadoReclamo.EN_PROCESO, reclamo.getEstado());
        assertSame(datos.agenteAlumbrado, reclamo.getAgente());

        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, "Lámpara reemplazada", AHORA.plusHours(5));
        assertEquals(EstadoReclamo.RESUELTO, reclamo.getEstado());

        reclamo.cambiarEstado(EstadoReclamo.CERRADO, datos.ana, "Funciona", AHORA.plusHours(8));
        assertEquals(EstadoReclamo.CERRADO, reclamo.getEstado());

        List<HistorialEstado> historial = reclamo.getHistorial();
        assertEquals(5, historial.size());
        assertEquals(EstadoReclamo.CERRADO, historial.get(4).getEstadoNuevo());
        assertSame(datos.ana, historial.get(4).getUsuario());
        assertEquals("Lámpara reemplazada", historial.get(3).getObservacion());
        assertEquals(AHORA.plusHours(2), historial.get(2).getFecha());
    }

    @Test
    @DisplayName("El ciudadano puede reabrir un reclamo resuelto y conserva el agente")
    void reapertura() {
        Reclamo reclamo = datos.reclamoAsignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA);

        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue apagada", AHORA.plusHours(1));

        assertEquals(EstadoReclamo.EN_PROCESO, reclamo.getEstado());
        assertSame(datos.agenteAlumbrado, reclamo.getAgente());
    }

    @Test
    @DisplayName("El ciudadano puede cancelar antes de que empiece el trabajo, no después")
    void cancelacion() {
        Reclamo ingresado = datos.reclamoIngresado();
        ingresado.cambiarEstado(EstadoReclamo.CANCELADO, datos.ana, null, AHORA);
        assertEquals(EstadoReclamo.CANCELADO, ingresado.getEstado());

        Reclamo asignado = datos.reclamoAsignado();
        asignado.cambiarEstado(EstadoReclamo.CANCELADO, datos.ana, null, AHORA);
        assertEquals(EstadoReclamo.CANCELADO, asignado.getEstado());

        Reclamo enProceso = datos.reclamoAsignado();
        enProceso.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        assertThrows(ReglaNegocioException.class,
                () -> enProceso.cambiarEstado(EstadoReclamo.CANCELADO, datos.ana, null, AHORA));
        assertEquals(EstadoReclamo.EN_PROCESO, enProceso.getEstado());
    }

    @Test
    @DisplayName("Solo el administrador rechaza un reclamo ingresado")
    void rechazo() {
        Reclamo reclamo = datos.reclamoIngresado();
        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.RECHAZADO, datos.agenteAlumbrado, null, AHORA));

        reclamo.cambiarEstado(EstadoReclamo.RECHAZADO, datos.admin, "No corresponde al municipio", AHORA);
        assertEquals(EstadoReclamo.RECHAZADO, reclamo.getEstado());
    }

    @Test
    @DisplayName("Una transición inexistente no cambia el estado ni el historial")
    void transicionInexistente() {
        Reclamo reclamo = datos.reclamoIngresado();

        assertThrows(ReglaNegocioException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA));

        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
        assertEquals(1, reclamo.getHistorial().size());
    }

    @Test
    @DisplayName("Un ciudadano no opera sobre el reclamo de otro")
    void ciudadanoAjeno() {
        Reclamo reclamo = datos.reclamoIngresado();

        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.CANCELADO, datos.bruno, null, AHORA));
        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
    }

    @Test
    @DisplayName("Un agente de otra área no puede tomar el reclamo")
    void agenteDeOtraArea() {
        Reclamo reclamo = datos.reclamoAsignado();

        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteObras, null, AHORA));
        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertNull(reclamo.getAgente());
    }

    @Test
    @DisplayName("El administrador asigna a mano un reclamo que quedó INGRESADO")
    void asignacionManual() {
        Reclamo reclamo = datos.reclamoIngresado();

        reclamo.asignarArea(datos.obrasPublicas, datos.admin, "Sin área con jurisdicción", AHORA);

        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertSame(datos.obrasPublicas, reclamo.getArea());
        assertSame(datos.admin, reclamo.getHistorial().get(1).getUsuario());
    }

    @Test
    @DisplayName("El administrador reasigna a otra área: sigue ASIGNADO y queda registrado")
    void reasignacion() {
        Reclamo reclamo = datos.reclamoAsignado();

        reclamo.asignarArea(datos.obrasPublicas, datos.admin, "Corresponde a Obras", AHORA.plusHours(1));

        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertSame(datos.obrasPublicas, reclamo.getArea());
        assertEquals(3, reclamo.getHistorial().size());
        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getHistorial().get(2).getEstadoAnterior());
    }

    @Test
    @DisplayName("Solo el administrador reasigna, y no a la misma área ni a un área inactiva")
    void reglasDeAsignacion() {
        Reclamo reclamo = datos.reclamoAsignado();

        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.asignarArea(datos.obrasPublicas, null, null, AHORA));
        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.asignarArea(datos.obrasPublicas, datos.agenteAlumbrado, null, AHORA));
        assertThrows(ReglaNegocioException.class,
                () -> reclamo.asignarArea(datos.alumbrado, datos.admin, null, AHORA));
        datos.obrasPublicas.desactivar();
        assertThrows(ReglaNegocioException.class,
                () -> reclamo.asignarArea(datos.obrasPublicas, datos.admin, null, AHORA));

        assertSame(datos.alumbrado, reclamo.getArea());
        assertEquals(2, reclamo.getHistorial().size());
    }

    @Test
    @DisplayName("No se puede asignar un reclamo que ya está en proceso")
    void noSeAsignaEnProceso() {
        Reclamo reclamo = datos.reclamoAsignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);

        assertThrows(ReglaNegocioException.class,
                () -> reclamo.asignarArea(datos.obrasPublicas, datos.admin, null, AHORA));
    }

    @Test
    @DisplayName("Pasar a ASIGNADO por cambio de estado se rechaza: hay que usar la asignación")
    void asignadoNoEsUnCambioDeEstado() {
        Reclamo reclamo = datos.reclamoIngresado();

        assertThrows(DatosInvalidosException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.ASIGNADO, datos.admin, null, AHORA));
    }

    @Test
    @DisplayName("Pasada la fecha límite el reclamo se marca vencido una sola vez y sube de prioridad")
    void vencimiento() {
        Reclamo reclamo = datos.reclamoAsignado();

        assertFalse(reclamo.marcarVencido(AHORA.plusHours(48)), "en la fecha límite exacta todavía no venció");
        assertTrue(reclamo.marcarVencido(AHORA.plusHours(49)));
        assertTrue(reclamo.isVencido());
        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());

        assertFalse(reclamo.marcarVencido(AHORA.plusHours(60)), "no se marca dos veces");
        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());
    }

    @Test
    @DisplayName("Un reclamo resuelto o en estado final no vence")
    void noVenceSiYaNoEstaPendiente() {
        Reclamo resuelto = datos.reclamoAsignado();
        resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        resuelto.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA);
        assertFalse(resuelto.marcarVencido(AHORA.plusDays(30)));

        Reclamo cancelado = datos.reclamoIngresado();
        cancelado.cambiarEstado(EstadoReclamo.CANCELADO, datos.ana, null, AHORA);
        assertFalse(cancelado.marcarVencido(AHORA.plusDays(30)));
        assertEquals(Prioridad.MEDIA, cancelado.getPrioridad());
    }

    @Test
    @DisplayName("El historial no se puede modificar desde afuera")
    void historialInmutable() {
        Reclamo reclamo = datos.reclamoIngresado();

        assertThrows(UnsupportedOperationException.class, () -> reclamo.getHistorial().clear());
    }
}
