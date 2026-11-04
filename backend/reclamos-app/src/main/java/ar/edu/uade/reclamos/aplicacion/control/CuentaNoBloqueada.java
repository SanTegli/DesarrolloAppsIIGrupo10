package ar.edu.uade.reclamos.aplicacion.control;

import ar.edu.uade.reclamos.comun.excepcion.UsoRestringidoException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;

/** Una cuenta bloqueada por un administrador puede consultar, pero no crear ni reabrir reclamos. */
public class CuentaNoBloqueada extends ControlDeUso {

    @Override
    protected void revisar(Ciudadano ciudadano, LocalDateTime ahora) {
        if (ciudadano.isBloqueado()) {
            throw new UsoRestringidoException(UsoRestringidoException.CUENTA_BLOQUEADA,
                    "Tu cuenta está bloqueada: podés consultar tus reclamos, pero no crear ni reabrir. "
                            + "Comunicate con el municipio.");
        }
    }
}
