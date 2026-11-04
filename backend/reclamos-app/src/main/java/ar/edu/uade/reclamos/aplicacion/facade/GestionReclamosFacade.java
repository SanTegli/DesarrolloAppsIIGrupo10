package ar.edu.uade.reclamos.aplicacion.facade;

import ar.edu.uade.reclamos.aplicacion.foto.FotoSubida;
import ar.edu.uade.reclamos.aplicacion.foto.ServicioFotos;
import ar.edu.uade.reclamos.aplicacion.servicio.*;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.aplicacion.verificacion.CodigoEmitido;
import ar.edu.uade.reclamos.aplicacion.verificacion.ServicioVerificacionTelefono;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Punto de entrada de la aplicación, independiente de cualquier transporte. */
@Component
public class GestionReclamosFacade {
    private final ReclamoService escritura;
    private final ConsultaReclamosService consultas;
    private final ConsultaCatalogosService catalogos;
    private final ServicioUbicaciones ubicaciones;
    private final ServicioCuentas cuentas;
    private final ServicioVerificacionTelefono verificacion;
    private final ServicioFotos fotos;
    private final ServicioDuplicados duplicados;
    private final ServicioApoyos apoyos;

    public GestionReclamosFacade(ReclamoService escritura, ConsultaReclamosService consultas,
                                  ConsultaCatalogosService catalogos, ServicioUbicaciones ubicaciones,
                                  ServicioCuentas cuentas, ServicioVerificacionTelefono verificacion,
                                  ServicioFotos fotos, ServicioDuplicados duplicados, ServicioApoyos apoyos) {
        this.fotos = fotos;
        this.duplicados = duplicados;
        this.apoyos = apoyos;
        this.escritura = escritura;
        this.consultas = consultas;
        this.catalogos = catalogos;
        this.ubicaciones = ubicaciones;
        this.cuentas = cuentas;
        this.verificacion = verificacion;
    }

    /** Código para verificar el teléfono del ciudadano por el canal configurado (Telegram o simulado). */
    public CodigoEmitido iniciarVerificacionTelefono(Long usuarioId) {
        return verificacion.iniciar(usuarioId);
    }

    /** Solo en modo simulado: confirma el código propio con un número escrito a mano. */
    public Usuario confirmarVerificacionSimulada(Long usuarioId, String codigo, String telefono) {
        return verificacion.confirmarSimulado(usuarioId, codigo, telefono);
    }

    public Usuario cambiarBloqueo(Long actorId, Long usuarioId, boolean bloqueado) {
        return cuentas.cambiarBloqueo(actorId, usuarioId, bloqueado);
    }

    /** Registro de un ciudadano con email y contraseña. */
    public Ciudadano registrarCiudadano(String dni, String nombre, String apellido, String email,
                                        String contrasena) {
        return cuentas.registrarCiudadano(dni, nombre, apellido, email, contrasena);
    }

    public Reclamo crear(Long usuarioId, Long categoriaId, Long barrioId, String descripcion, String direccion) {
        return escritura.crear(usuarioId, categoriaId, barrioId, descripcion, direccion);
    }

    /** Alta con la sugerencia de ubicación que eligió el ciudadano, o {@code null} si no eligió ninguna. */
    public Reclamo crear(Long usuarioId, Long categoriaId, Long barrioId, String descripcion, String direccion,
                         UbicacionElegida ubicacion) {
        return escritura.crear(usuarioId, categoriaId, barrioId, descripcion, direccion,
                ubicaciones.confirmar(barrioId, ubicacion));
    }

    /**
     * Alta desde la API: la foto es obligatoria. Se valida y se guarda como pendiente antes de la transacción
     * del alta; se confirma cuando el reclamo ya está en la base y se descarta si el alta falla. Así nunca queda
     * un archivo huérfano ni un reclamo que apunte a un archivo a medio escribir.
     */
    public Reclamo crear(Long usuarioId, Long categoriaId, Long barrioId, String descripcion, String direccion,
                         UbicacionElegida ubicacion, FotoSubida foto) {
        String referencia = fotos.preparar(foto);
        Reclamo reclamo;
        try {
            reclamo = escritura.crear(usuarioId, categoriaId, barrioId, descripcion, direccion,
                    ubicaciones.confirmar(barrioId, ubicacion), referencia);
        } catch (RuntimeException error) {
            fotos.descartar(referencia);
            throw error;
        }
        fotos.confirmar(referencia);
        return reclamo;
    }

    /**
     * La foto del reclamo, JPEG sin metadatos. La ve quien ve el detalle y, mientras el reclamo puede aparecer
     * como posible duplicado, cualquier ciudadano.
     */
    public byte[] foto(Long usuarioId, String numero) {
        Reclamo reclamo = consultas.buscarParaFoto(usuarioId, numero);
        return reclamo.getFoto().flatMap(fotos::leer)
                .orElseThrow(() -> new RecursoNoEncontradoException("El reclamo " + numero + " no tiene foto."));
    }

    /** Reclamos abiertos de la categoría cerca del punto elegido, para no crear uno repetido. */
    public List<CandidatoDuplicado> candidatosDuplicados(Long usuarioId, Long categoriaId, double latitud,
                                                         double longitud, Integer radioMetros, Integer limite) {
        return duplicados.candidatos(usuarioId, categoriaId, latitud, longitud, radioMetros, limite);
    }

    /**
     * Apoya el reclamo. Si dos pedidos iguales llegan a la vez (un doble clic), el segundo choca con la
     * restricción única de la base: se responde el estado actual, como si hubiera llegado después.
     */
    public EstadoApoyo apoyar(Long usuarioId, String numero) {
        try {
            return apoyos.apoyar(usuarioId, numero);
        } catch (DataIntegrityViolationException repetido) {
            return apoyos.estado(usuarioId, numero);
        }
    }

    public EstadoApoyo retirarApoyo(Long usuarioId, String numero) {
        return apoyos.retirar(usuarioId, numero);
    }

    /** Cuántos vecinos apoyan el reclamo. */
    public long cantidadApoyos(Reclamo reclamo) {
        return apoyos.cantidad(reclamo);
    }

    /** Sugerencias del geocodificador para la dirección, antes del alta. */
    public SugerenciasUbicacion sugerirUbicaciones(String direccion, Long barrioId) {
        return ubicaciones.sugerir(direccion, barrioId);
    }

    public Reclamo asignar(Long usuarioId, String numero, Long areaId, String observacion) {
        return escritura.asignar(usuarioId, numero, areaId, observacion);
    }

    public Reclamo reasignar(Long usuarioId, String numero, Long areaId, String observacion) {
        return escritura.reasignar(usuarioId, numero, areaId, observacion);
    }

    public Reclamo cambiarEstado(Long usuarioId, String numero, EstadoReclamo estado, String observacion) {
        return escritura.cambiarEstado(usuarioId, numero, estado, observacion);
    }

    public Reclamo tomar(Long usuarioId, String numero, String observacion) {
        return escritura.tomar(usuarioId, numero, observacion);
    }

    public Reclamo resolver(Long usuarioId, String numero, String observacion) {
        return escritura.resolver(usuarioId, numero, observacion);
    }

    public Reclamo cerrar(Long usuarioId, String numero, String observacion) {
        return escritura.cerrar(usuarioId, numero, observacion);
    }

    public Reclamo reabrir(Long usuarioId, String numero, String observacion) {
        return escritura.reabrir(usuarioId, numero, observacion);
    }

    public Reclamo rechazar(Long usuarioId, String numero, String observacion) {
        return escritura.rechazar(usuarioId, numero, observacion);
    }

    public Reclamo cancelar(Long usuarioId, String numero, String observacion) {
        return escritura.cancelar(usuarioId, numero, observacion);
    }

    public List<Reclamo> listar(Long usuarioId, EstadoReclamo estado, Long areaId, Long ciudadanoId) {
        return consultas.listar(usuarioId, estado, areaId, ciudadanoId);
    }

    public Reclamo buscarPorNumero(Long usuarioId, String numero) {
        return consultas.buscarPorNumero(usuarioId, numero);
    }

    public List<Notificacion> consultarNotificaciones(Long usuarioId, String numero) {
        return consultas.consultarNotificaciones(usuarioId, numero);
    }

    public List<Notificacion> consultarAvisosDelUsuario(Long usuarioId) {
        return consultas.consultarAvisosDelUsuario(usuarioId);
    }

    public Set<EstadoReclamo> accionesDisponibles(Long usuarioId, String numero) {
        return consultas.accionesDisponibles(usuarioId, numero);
    }

    public List<Usuario> usuarios(Long actorId) {
        return catalogos.usuarios(actorId);
    }

    public List<Categoria> categorias() {
        return catalogos.categorias();
    }

    public List<Barrio> barrios() {
        return catalogos.barrios();
    }

    public List<AreaMunicipal> areas() {
        return catalogos.areas();
    }

    public Usuario usuario(Long id) {
        return catalogos.usuario(id);
    }

    public Usuario usuario(Long actorId, Long id) {
        return catalogos.usuario(actorId, id);
    }
}
