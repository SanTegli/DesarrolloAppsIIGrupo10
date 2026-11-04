package ar.edu.uade.reclamos.comun.excepcion;

/**
 * La cuenta existe y tiene el rol correcto, pero todavía no puede hacer la operación: le falta verificar el
 * teléfono o está bloqueada. HTTP 403, con un código propio para que la interfaz sepa qué mostrar.
 */
public class UsoRestringidoException extends ReclamosException {

    public static final String TELEFONO_NO_VERIFICADO = "TELEFONO_NO_VERIFICADO";
    public static final String CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";

    private final String codigo;

    public UsoRestringidoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
