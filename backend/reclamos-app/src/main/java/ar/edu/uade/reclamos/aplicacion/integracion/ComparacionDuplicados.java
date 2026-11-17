package ar.edu.uade.reclamos.aplicacion.integracion;

/**
 * Lo que respondió REST 2 al comparar un reclamo nuevo con los cercanos (PR 27), ya validado.
 *
 * @param similar el id que se le mandó al candidato más parecido, o {@code null} si ninguno es el mismo problema
 */
public record ComparacionDuplicados(String similar, String motivo) { }
