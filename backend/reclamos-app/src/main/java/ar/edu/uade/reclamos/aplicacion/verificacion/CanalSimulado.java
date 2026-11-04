package ar.edu.uade.reclamos.aplicacion.verificacion;

/**
 * Sin Telegram, para desarrollar, probar y hacer la demo sin bot: no hay enlace y el código se confirma con
 * {@code POST /api/verificacion-telefono/simulada}. No verifica nada de verdad: el número lo escribe el usuario.
 */
public class CanalSimulado implements CanalVerificacion {

    @Override
    public String nombre() {
        return "simulado";
    }

    @Override
    public String enlace(String codigo) {
        return null;
    }
}
