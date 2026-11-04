import { pedir } from './cliente.js'

const ruta = (numero) => `/reclamos/${encodeURIComponent(numero)}`

/**
 * POST /api/reclamos. Solo ciudadanos. Va como multipart/form-data: la parte `reclamo` con el JSON y la parte
 * `foto` con el archivo (obligatoria). `ubicacion` es la sugerencia elegida del mapa; sin ella, el reclamo queda
 * con la ubicación pendiente.
 */
export function crearReclamo({ categoriaId, barrioId, descripcion, direccion, ubicacion, foto }) {
  const reclamo = { categoriaId, barrioId, descripcion, direccion }
  if (ubicacion) {
    reclamo.ubicacion = ubicacion
  }
  const formulario = new FormData()
  formulario.append('reclamo', new Blob([JSON.stringify(reclamo)], { type: 'application/json' }))
  if (foto) {
    formulario.append('foto', foto)
  }
  return pedir('/reclamos', { metodo: 'POST', formulario })
}

/** GET /api/reclamos. El backend decide qué reclamos ve el usuario de la sesión según su rol. */
export function listarReclamos({ estado, areaId, ciudadanoId } = {}) {
  return pedir('/reclamos', { parametros: { estado, areaId, ciudadanoId } })
}

/** GET /api/reclamos/{numero}. Incluye historial y accionesDisponibles. */
export function obtenerReclamo(numero) {
  return pedir(ruta(numero))
}

/** PATCH /api/reclamos/{numero}/estado. */
export function cambiarEstado(numero, estado, observacion) {
  return pedir(`${ruta(numero)}/estado`, {
    metodo: 'PATCH',
    cuerpo: { estado, observacion: observacion || null },
  })
}

/** PATCH /api/reclamos/{numero}/asignacion. Solo administradores. */
export function asignarArea(numero, areaId, observacion) {
  return pedir(`${ruta(numero)}/asignacion`, {
    metodo: 'PATCH',
    cuerpo: { areaId, observacion: observacion || null },
  })
}

/** GET /api/notificaciones. Bandeja del usuario: los avisos que recibió, del más nuevo al más viejo. */
export function listarAvisos() {
  return pedir('/notificaciones')
}

/** GET /api/reclamos/{numero}/notificaciones. Avisos generados por los eventos. */
export function listarNotificaciones(numero) {
  return pedir(`${ruta(numero)}/notificaciones`)
}
