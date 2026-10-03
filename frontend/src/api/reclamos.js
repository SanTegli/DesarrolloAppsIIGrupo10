import { pedir } from './cliente.js'

const ruta = (numero) => `/reclamos/${encodeURIComponent(numero)}`

/** POST /api/reclamos. Solo ciudadanos. */
export function crearReclamo(usuarioId, { categoriaId, barrioId, descripcion, direccion }) {
  return pedir('/reclamos', {
    usuarioId,
    metodo: 'POST',
    cuerpo: { categoriaId, barrioId, descripcion, direccion },
  })
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

/** GET /api/reclamos/{numero}/notificaciones. Avisos generados por los eventos. */
export function listarNotificaciones(usuarioId, numero) {
  return pedir(`${ruta(numero)}/notificaciones`, { usuarioId })
}
