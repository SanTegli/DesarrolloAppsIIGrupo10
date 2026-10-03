package ar.edu.uade.reclamos.aplicacion.seguridad;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;

class PermisosReclamoTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final PermisosReclamo permisos = new PermisosReclamo();

    @Test
    void ciudadanoVeSoloPropiosYPuedeCrear() {
        Reclamo reclamo = e.ingresado();
        assertThat(permisos.validarCreacion(e.ciudadano)).isSameAs(e.ciudadano);
        permisos.validarConsulta(e.ciudadano, reclamo);
        assertThatThrownBy(() -> permisos.validarConsulta(e.otroCiudadano, reclamo))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(permisos.accionesDisponibles(e.ciudadano, reclamo)).containsExactly(EstadoReclamo.CANCELADO);
        assertThat(permisos.accionesDisponibles(e.otroCiudadano, reclamo)).isEmpty();
    }

    @Test
    void agenteVeSoloSuArea() {
        permisos.validarConsulta(e.agente, e.asignado());
        assertThatThrownBy(() -> permisos.validarConsulta(e.otroAgente, e.asignado()))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> permisos.validarConsulta(e.agente, e.ingresado()))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> permisos.validarCreacion(e.agente))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(permisos.accionesDisponibles(e.agente, e.asignado())).containsExactly(EstadoReclamo.EN_PROCESO);
    }

    @Test
    void administradorVeTodosYFiltraPeroNoCrea() {
        permisos.validarConsulta(e.administrador, e.ingresado());
        permisos.validarConsulta(e.administrador, e.asignado());
        permisos.validarFiltros(e.administrador, 99L, 99L);
        assertThatThrownBy(() -> permisos.validarCreacion(e.administrador))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(permisos.accionesDisponibles(e.administrador, e.ingresado()))
                .containsExactlyInAnyOrder(EstadoReclamo.ASIGNADO, EstadoReclamo.RECHAZADO);
        assertThat(permisos.accionesDisponibles(e.administrador, e.resuelto())).isEmpty();
    }
}
