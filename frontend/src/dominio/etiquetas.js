// Textos de la interfaz para los valores del contrato REST.
// Las reglas (quién puede hacer qué) NO están acá: llegan del backend en accionesDisponibles.

export const ESTADOS = {
  INGRESADO: 'Ingresado',
  ASIGNADO: 'Asignado',
  EN_PROCESO: 'En proceso',
  RESUELTO: 'Resuelto',
  CERRADO: 'Cerrado',
  RECHAZADO: 'Rechazado',
  CANCELADO: 'Cancelado',
}

export const PRIORIDADES = {
  BAJA: 'Baja',
  MEDIA: 'Media',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
}

export const ROLES = {
  CIUDADANO: 'Ciudadano',
  AGENTE_MUNICIPAL: 'Agente municipal',
  ADMINISTRADOR: 'Administrador',
}

/**
 * Nombre del botón que lleva un reclamo de estadoActual a destino.
 * EN_PROCESO se llama distinto según venga de ASIGNADO (tomar) o de RESUELTO (reabrir).
 */
export function nombreDeAccion(estadoActual, destino) {
  switch (destino) {
    case 'EN_PROCESO':
      return estadoActual === 'RESUELTO' ? 'Reabrir' : 'Tomar reclamo'
    case 'RESUELTO':
      return 'Marcar como resuelto'
    case 'CERRADO':
      return 'Confirmar solución'
    case 'CANCELADO':
      return 'Cancelar reclamo'
    case 'RECHAZADO':
      return 'Rechazar'
    default:
      return ESTADOS[destino] ?? destino
  }
}

/** Acciones que cierran el reclamo sin vuelta atrás: se muestran como botón de peligro. */
export const esAccionDefinitiva = (destino) => destino === 'CANCELADO' || destino === 'RECHAZADO'

/** "2026-10-05T10:00:00" pasa a "05/10/2026 10:00". Las fechas del contrato no llevan zona horaria. */
export function formatearFecha(iso) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso ?? '')
  if (!partes) {
    return ''
  }
  const [, anio, mes, dia, hora, minuto] = partes
  return `${dia}/${mes}/${anio} ${hora}:${minuto}`
}

/** Texto del usuario para el selector: nombre, rol y área si es agente. */
export function describirUsuario(usuario) {
  const rol = ROLES[usuario.rol] ?? usuario.rol
  return usuario.area ? `${usuario.nombreCompleto}, ${rol} de ${usuario.area.nombre}` : `${usuario.nombreCompleto}, ${rol}`
}
