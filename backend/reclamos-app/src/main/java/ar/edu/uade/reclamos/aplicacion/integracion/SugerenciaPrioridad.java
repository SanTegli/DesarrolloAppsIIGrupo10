package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;

/** Lo que respondió el servicio de prioridad, ya validado y con la prioridad del dominio. */
public record SugerenciaPrioridad(Prioridad prioridad, String motivo, String proveedor, String modelo) { }
