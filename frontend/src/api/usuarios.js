import { pedir } from './cliente.js'

/** GET /api/usuarios. Solo administradores. El teléfono llega enmascarado. */
export function listarUsuarios() {
  return pedir('/usuarios')
}

/** PATCH /api/usuarios/{id}/bloqueo. Solo administradores y solo sobre cuentas de ciudadanos. */
export function cambiarBloqueo(id, bloqueado) {
  return pedir(`/usuarios/${encodeURIComponent(id)}/bloqueo`, { metodo: 'PATCH', cuerpo: { bloqueado } })
}
