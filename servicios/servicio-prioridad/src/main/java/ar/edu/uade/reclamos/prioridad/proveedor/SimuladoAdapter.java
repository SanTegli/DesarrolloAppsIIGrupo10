package ar.edu.uade.reclamos.prioridad.proveedor;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Proveedor sin red ni clave, para los tests y para correr el sistema sin cuenta de Gemini. <b>No es IA</b>:
 * busca palabras en la descripción. Lo dice en el nombre ({@code simulado}) y en cada motivo, así nunca se
 * confunde con una inferencia real en la demo.
 */
public class SimuladoAdapter implements ProveedorIA {

    private record Regla(String prioridad, List<String> palabras) { }

    private static final List<Regla> REGLAS = List.of(
            new Regla("CRITICA", List.of("cable", "electr", "gas", "incendio", "herid", "derrumb", "por caer")),
            new Regla("ALTA", List.of("escuela", "hospital", "avenida", "accidente", "inund", "peligro", "riesgo")),
            new Regla("MEDIA", List.of("apagad", "acumulad", "pozo", "bache", "olor")));

    @Override
    public String nombre() {
        return "simulado";
    }

    @Override
    public String modelo() {
        return "reglas-locales";
    }

    @Override
    public RespuestaIA sugerir(ConsultaIA consulta) {
        if (EsquemaRespuesta.CAMPO_CATEGORIA.equals(consulta.formato().campo())) {
            return categoria(consulta);
        }
        String texto = consulta.reclamo().toLowerCase(Locale.ROOT);
        for (Regla regla : REGLAS) {
            for (String palabra : regla.palabras()) {
                if (texto.contains(palabra)) {
                    return new RespuestaIA(regla.prioridad(),
                            "Simulado: la descripción menciona \"" + palabra + "\".");
                }
            }
        }
        return new RespuestaIA("BAJA", "Simulado: no se encontraron señales de riesgo.");
    }

    /**
     * La primera categoría con alguna palabra de cuatro letras o más que aparezca en el reclamo, sin tildes ni
     * mayúsculas; si no hay ninguna, {@code NINGUNA}.
     */
    private static RespuestaIA categoria(ConsultaIA consulta) {
        String texto = normalizar(consulta.reclamo());
        for (String categoria : consulta.formato().valores()) {
            for (String palabra : normalizar(categoria).split("[^a-z0-9]+")) {
                if (palabra.length() >= 4 && texto.contains(raiz(palabra))) {
                    return new RespuestaIA(categoria, "Simulado: el reclamo menciona \"" + palabra + "\".");
                }
            }
        }
        return new RespuestaIA(FormatoRespuesta.NINGUNA, "Simulado: ninguna categoría coincide con el texto.");
    }

    /** "árboles" y "árbol" comparten "arbol": sin la última letra alcanza para el plural más común. */
    private static String raiz(String palabra) {
        return palabra.length() > 4 ? palabra.substring(0, palabra.length() - 1) : palabra;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
