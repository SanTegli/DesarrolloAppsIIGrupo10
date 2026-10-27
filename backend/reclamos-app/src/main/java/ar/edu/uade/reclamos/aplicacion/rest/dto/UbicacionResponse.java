package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoUbicacion;

/** Coordenadas del reclamo. Con estado {@code PENDIENTE}, todos los demás campos son nulos. */
public record UbicacionResponse(EstadoUbicacion estado, Double latitud, Double longitud,
                                String direccionNormalizada, String proveedor) { }
