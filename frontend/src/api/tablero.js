import { pedir } from './cliente.js'

/**
 * GET /api/dashboard/publico. No necesita sesión. Sin fechas, los últimos 30 días. Los filtros vacíos no se
 * mandan.
 */
export function tableroPublico({ desde, hasta, barrioId, categoriaId } = {}) {
  return pedir('/dashboard/publico', { parametros: { desde, hasta, barrioId, categoriaId }, avisarSiNoHaySesion: false })
}

/** GET /api/dashboard/gestion. Agentes (su área) y administradores (todas). */
export function tableroGestion() {
  return pedir('/dashboard/gestion')
}
