package ar.edu.uade.reclamos.aplicacion.control;

import ar.edu.uade.reclamos.comun.excepcion.UsoRestringidoException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;

/** Sin teléfono verificado no se crean ni se reabren reclamos: un número, una cuenta. */
public class TelefonoVerificado extends ControlDeUso {

    @Override
    protected void revisar(Ciudadano ciudadano, LocalDateTime ahora) {
        if (!ciudadano.tieneTelefonoVerificado()) {
            throw new UsoRestringidoException(UsoRestringidoException.TELEFONO_NO_VERIFICADO,
                    "Para presentar o reabrir reclamos tenés que verificar tu teléfono con Telegram.");
        }
    }
}
