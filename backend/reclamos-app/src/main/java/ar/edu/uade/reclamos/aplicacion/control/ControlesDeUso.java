package ar.edu.uade.reclamos.aplicacion.control;

import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;

/**
 * Las dos cadenas de controles: antes de crear un reclamo, y antes de reabrirlo o apoyarlo.
 * Los agentes y administradores no pasan por acá.
 */
public record ControlesDeUso(ControlDeUso alCrear, ControlDeUso alReabrir) {

    /** Sin controles: para tests unitarios que no prueban esto. */
    public static final ControlesDeUso NINGUNO = new ControlesDeUso(null, null);

    /** Teléfono verificado → cuenta no bloqueada → límite diario. Al reabrir, los dos primeros. */
    public static ControlesDeUso estandar(LimiteDiarioDeReclamos limiteDiario) {
        ControlDeUso alCrear = new TelefonoVerificado();
        alCrear.luego(new CuentaNoBloqueada()).luego(limiteDiario);
        ControlDeUso alReabrir = new TelefonoVerificado();
        alReabrir.luego(new CuentaNoBloqueada());
        return new ControlesDeUso(alCrear, alReabrir);
    }

    public void antesDeCrear(Ciudadano ciudadano, LocalDateTime ahora) {
        if (alCrear != null) {
            alCrear.verificar(ciudadano, ahora);
        }
    }

    public void antesDeReabrir(Ciudadano ciudadano, LocalDateTime ahora) {
        if (alReabrir != null) {
            alReabrir.verificar(ciudadano, ahora);
        }
    }

    /** Apoyar pide lo mismo que reabrir: teléfono verificado y cuenta sin bloquear. */
    public void antesDeApoyar(Ciudadano ciudadano, LocalDateTime ahora) {
        antesDeReabrir(ciudadano, ahora);
    }
}
