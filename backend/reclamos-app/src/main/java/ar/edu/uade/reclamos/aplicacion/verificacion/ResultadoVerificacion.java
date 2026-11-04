package ar.edu.uade.reclamos.aplicacion.verificacion;

/** Cómo terminó un intento de verificación, con lo que el bot le contesta al ciudadano. */
public enum ResultadoVerificacion {
    VERIFICADO("Listo, tu número quedó verificado. Ya podés presentar reclamos desde la página."),
    CODIGO_INEXISTENTE("No encontré ese código. Volvé a tocar \"Verificar con Telegram\" en la página."),
    CODIGO_VENCIDO("El código venció o ya se usó. Pedí uno nuevo en la página."),
    TELEFONO_EN_OTRA_CUENTA("Ese número ya está verificado en otra cuenta. Cada número puede estar en una sola."),
    TELEFONO_INVALIDO("No pude leer tu número. Probá de nuevo con el botón \"Compartir mi número\"."),
    CUENTA_BLOQUEADA("La cuenta está bloqueada. Comunicate con el municipio.");

    private final String mensaje;

    ResultadoVerificacion(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
