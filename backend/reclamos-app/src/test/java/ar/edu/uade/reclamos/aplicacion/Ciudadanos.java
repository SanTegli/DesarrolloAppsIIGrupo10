package ar.edu.uade.reclamos.aplicacion;

import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;

/**
 * Desde el PR 15, un ciudadano necesita el teléfono verificado para crear o reabrir reclamos. Las fixtures que no
 * prueban la verificación lo crean ya verificado, con un número ficticio derivado del DNI (único como el DNI).
 */
public final class Ciudadanos {

    private Ciudadanos() {
    }

    public static Ciudadano verificado(Ciudadano ciudadano) {
        ciudadano.verificarTelefono("+5491" + ciudadano.getDni(), LocalDateTime.of(2026, 1, 1, 0, 0));
        return ciudadano;
    }
}
