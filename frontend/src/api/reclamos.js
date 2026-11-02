import { pedir } from './cliente.js'

const ruta = (numero) => `/reclamos/${encodeURIComponent(numero)}`

/**
 * POST /api/reclamos. Solo ciudadanos. `ubicacion` es la sugerencia elegida del mapa; sin ella, el reclamo
 * queda con la ubicación pendiente.
 */
export function crearReclamo(usuarioId, { categoriaId, barrioId, descripcion, direccion, ubicacion }) {
  const cuerpo = { categoriaId, barrioId, descripcion, direccion }
  if (ubicacion) {
    cuerpo.ubicacion = ubicacion
  }
  return pedir('/reclamos', { usuarioId, metodo: 'POST', cuerpo })
}

/** GET /api/reclamos. El backend decide qué reclamos ve cada rol. */
export function listarReclamos(usuarioId, { estado, areaId, ciudadanoId } = {}) {
  return pedir('/reclamos', { usuarioId, parametros: { estado, areaId, ciudadanoId } })
}

/** GET /api/reclamos/{numero}. Incluye historial y accionesDisponibles. */
export function obtenerReclamo(usuarioId, numero) {
  return pedir(ruta(numero), { usuarioId })
}

/** PATCH /api/reclamos/{numero}/estado. */
export function cambiarEstado(usuarioId, numero, estado, observacion) {
  return pedir(`${ruta(numero)}/estado`, {
    usuarioId,
    metodo: 'PATCH',
    cuerpo: { estado, observacion: observacion || null },
  })
}

/** PATCH /api/reclamos/{numero}/asignacion. Solo administradores. */
export function asignarArea(usuarioId, numero, areaId, observacion) {
  return pedir(`${ruta(numero)}/asignacion`, {
    usuarioId,
    metodo: 'PATCH',
    cuerpo: { areaId, observacion: observacion || null },
  })
}

/** GET /api/notificaciones. Bandeja del usuario: los avisos que recibió, del más nuevo al más viejo. */
export function listarAvisos(usuarioId) {
  return pedir('/notificaciones', { usuarioId })
}

/** GET /api/reclamos/{numero}/notificaciones. Avisos generados por los eventos. */
export function listarNotificaciones(usuarioId, numero) {
  return pedir(`${ruta(numero)}/notificaciones`, { usuarioId })
}
