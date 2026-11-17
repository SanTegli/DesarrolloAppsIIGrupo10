package ar.edu.uade.reclamos.prioridad.api;

/** Dos candidatos con el mismo id: la respuesta del modelo sería ambigua. */
class IdsRepetidosException extends RuntimeException {

    IdsRepetidosException() {
        super("Los ids de los candidatos no se pueden repetir.");
    }
}
