package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

class RelacionesFueraDeTransaccionTest extends PersistenciaTestBase {
    @Autowired PlatformTransactionManager transacciones;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void adaptadoresEntreganRelacionesUsablesCuandoTerminaSuTransaccion() {
        String numero = new TransactionTemplate(transacciones).execute(status -> {
            prepararDatos();
            Reclamo reclamo = nuevoReclamo();
            reclamo.asignarArea(area, null, "Asignación", AHORA);
            reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, "Tomado", AHORA);
            reclamos.guardar(reclamo);
            notificaciones.guardar(new Notificacion(reclamo, agente, CanalNotificacion.INTERNO, "Aviso", AHORA));
            flushYLimpiar();
            return reclamo.getNumero();
        });

        Reclamo recuperado = reclamos.buscarPorNumero(numero).orElseThrow();
        assertThat(recuperado.getCiudadano().getNombre()).isEqualTo("Ana");
        assertThat(recuperado.getBarrio().getMunicipio().getNombre()).isEqualTo("Quilmes");
        assertThat(recuperado.getCategoria().getNombre()).isEqualTo("Bache");
        assertThat(recuperado.getArea().puedeAtender(recuperado.getCategoria(), recuperado.getBarrio())).isTrue();
        assertThat(recuperado.getAgente().perteneceA(recuperado.getArea())).isTrue();
        assertThat(recuperado.getHistorial()).extracting(h -> h.getUsuario() == null ? Rol.SISTEMA : h.getUsuario().getRol())
                .containsExactly(Rol.CIUDADANO, Rol.SISTEMA, Rol.AGENTE_MUNICIPAL);
        assertThat(areas.buscarPorId(area.getId()).orElseThrow().getBarrios()).hasSize(1);
        assertThat(barrios.buscarPorId(barrio.getId()).orElseThrow().getMunicipio().getNombre()).isEqualTo("Quilmes");
        Usuario usuario = usuarios.buscarPorId(agente.getId()).orElseThrow();
        assertThat(usuario).isInstanceOf(AgenteMunicipal.class);
        assertThat(((AgenteMunicipal) usuario).getArea().getCategorias()).hasSize(1);
        Notificacion aviso = notificaciones.buscarPorReclamo(numero).get(0);
        assertThat(aviso.getReclamo().getHistorial()).hasSize(3);
        assertThat(aviso.getDestinatario().getRol()).isEqualTo(Rol.AGENTE_MUNICIPAL);
    }
}

