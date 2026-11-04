package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.Rol;

/**
 * @param telefonoVerificado enmascarado ({@code +54 ••••••••1234}); nulo si no lo verificó o no es ciudadano
 * @param bloqueado          bloqueada por un administrador: puede consultar, no crear ni reabrir
 */
public record UsuarioResponse(Long id, String nombreCompleto, Rol rol, ReferenciaResponse area,
                              String telefonoVerificado, boolean bloqueado) { }
