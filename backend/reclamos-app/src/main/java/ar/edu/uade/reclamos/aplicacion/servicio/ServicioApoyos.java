package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.control.ControlesDeUso;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.ApoyoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apoyos: "a mí también me pasa", en lugar de un reclamo repetido. Apoyar y retirar el apoyo son idempotentes:
 * repetirlos deja el mismo resultado. Apoyar no da permisos sobre el reclamo ni le cambia la prioridad.
 */
@Service
public class ServicioApoyos {

    private final ApoyoRepository apoyos;
    private final ReclamoRepository reclamos;
    private final UsuarioRepository usuarios;
    private final ControlesDeUso controles;
    private final Clock reloj;

    public ServicioApoyos(ApoyoRepository apoyos, ReclamoRepository reclamos, UsuarioRepository usuarios,
                          ControlesDeUso controles, Clock reloj) {
        this.apoyos = apoyos;
        this.reclamos = reclamos;
        this.usuarios = usuarios;
        this.controles = controles;
        this.reloj = reloj;
    }

    /** Teléfono verificado y cuenta sin bloquear, como para reabrir. Si ya lo apoyaba, no hace nada. */
    @Transactional
    public EstadoApoyo apoyar(Long usuarioId, String numero) {
        Ciudadano ciudadano = ciudadano(usuarioId);
        Reclamo reclamo = reclamo(numero);
        if (apoyos.buscar(ciudadano.getId(), reclamo.getId()).isEmpty()) {
            LocalDateTime ahora = LocalDateTime.now(reloj);
            controles.antesDeApoyar(ciudadano, ahora);
            apoyos.guardar(new Apoyo(ciudadano, reclamo, ahora));
        }
        return estado(ciudadano, reclamo);
    }

    /** Si no lo apoyaba, no hace nada. Se puede retirar aunque el reclamo ya esté cerrado. */
    @Transactional
    public EstadoApoyo retirar(Long usuarioId, String numero) {
        Ciudadano ciudadano = ciudadano(usuarioId);
        Reclamo reclamo = reclamo(numero);
        apoyos.buscar(ciudadano.getId(), reclamo.getId()).ifPresent(apoyos::eliminar);
        return estado(ciudadano, reclamo);
    }

    /** Cómo está el apoyo de este ciudadano a este reclamo, sin cambiar nada. */
    @Transactional(readOnly = true)
    public EstadoApoyo estado(Long usuarioId, String numero) {
        return estado(ciudadano(usuarioId), reclamo(numero));
    }

    /** Cuántos vecinos apoyan el reclamo. Lo muestra el detalle. */
    @Transactional(readOnly = true)
    public long cantidad(Reclamo reclamo) {
        return reclamo.getId() == null ? 0 : apoyos.contarPorReclamo(reclamo.getId());
    }

    private EstadoApoyo estado(Ciudadano ciudadano, Reclamo reclamo) {
        return new EstadoApoyo(reclamo.getNumero(), apoyos.contarPorReclamo(reclamo.getId()),
                apoyos.buscar(ciudadano.getId(), reclamo.getId()).isPresent());
    }

    private Ciudadano ciudadano(Long usuarioId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", usuarioId));
        if (usuario instanceof Ciudadano ciudadano) {
            return ciudadano;
        }
        throw new AccesoDenegadoException("Solo los ciudadanos apoyan reclamos.");
    }

    private Reclamo reclamo(String numero) {
        return reclamos.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNoEncontradoException("reclamo", numero));
    }
}
