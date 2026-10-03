package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifica commits y rollback reales, sin una transacción envolviendo el test ni MySQL. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:servicios;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ServiciosAplicacionIntegrationTest {
    @Autowired GestionReclamosFacade facade;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired Environment entorno;
    @MockitoSpyBean ReclamoRepository reclamos;

    @Test
    void flujoPersisteEntreTransaccionesYUnaFallaRevierteEstadoAgenteEHistorial() {
        record Datos(Long ciudadano, Long agente, Long administrador, Long categoria, Long barrio, Long area) { }
        Datos datos = new TransactionTemplate(transacciones).execute(tx -> {
            Municipio municipio = municipios.guardar(new Municipio("Municipio del test", "Buenos Aires"));
            Barrio barrio = barrios.guardar(new Barrio("Barrio del test", municipio));
            Categoria categoria = categorias.guardar(new Categoria("Categoría del test", "Prueba", 24, Prioridad.ALTA));
            AreaMunicipal area = new AreaMunicipal("Área del test", "area@test.org", municipio);
            area.agregarCategoria(categoria);
            area.agregarBarrio(barrio);
            area = areas.guardar(area);
            Usuario ciudadano = usuarios.guardar(new Ciudadano("70111222", "Ana", "Test", "ana@test.org", null));
            Usuario agente = usuarios.guardar(new AgenteMunicipal("70222333", "Carla", "Test", "carla@test.org", null, area));
            Usuario administrador = usuarios.guardar(new Administrador("70333444", "Elena", "Test", "elena@test.org", null));
            return new Datos(ciudadano.getId(), agente.getId(), administrador.getId(),
                    categoria.getId(), barrio.getId(), area.getId());
        });
        assertThat(entorno.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        Reclamo creado = facade.crear(datos.ciudadano(), datos.categoria(), datos.barrio(), "Pozo", "Calle 450");
        String numero = creado.getNumero();
        Reclamo guardado = facade.buscarPorNumero(datos.ciudadano(), numero);
        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(guardado.getPrioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(guardado.getArea().getCategorias()).hasSize(1);
        assertThat(guardado.getHistorial()).hasSize(2);

        doAnswer(invocacion -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            throw new ReglaNegocioException("Falla de persistencia simulada");
        }).when(reclamos).guardar(any());
        assertThatThrownBy(() -> facade.tomar(datos.agente(), numero, "Tomar"))
                .isInstanceOf(ReglaNegocioException.class);
        doCallRealMethod().when(reclamos).guardar(any());
        Reclamo revertido = facade.buscarPorNumero(datos.administrador(), numero);
        assertThat(revertido.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(revertido.getAgente()).isNull();
        assertThat(revertido.getHistorial()).hasSize(2);

        facade.tomar(datos.agente(), numero, "Cuadrilla");
        facade.resolver(datos.agente(), numero, "Reparado");
        facade.reabrir(datos.ciudadano(), numero, "Persiste");
        facade.resolver(datos.agente(), numero, "Reparación final");
        facade.cerrar(datos.ciudadano(), numero, "Confirmado");
        Reclamo cerrado = facade.buscarPorNumero(datos.ciudadano(), numero);
        assertThat(cerrado.getEstado()).isEqualTo(EstadoReclamo.CERRADO);
        assertThat(cerrado.getAgente().getId()).isEqualTo(datos.agente());
        assertThat(cerrado.getHistorial()).hasSize(7);
        assertThat(facade.listar(datos.administrador(), EstadoReclamo.CERRADO, datos.area(), datos.ciudadano()))
                .extracting(Reclamo::getNumero).containsExactly(numero);
        assertThat(facade.consultarNotificaciones(datos.ciudadano(), numero)).hasSize(9);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
