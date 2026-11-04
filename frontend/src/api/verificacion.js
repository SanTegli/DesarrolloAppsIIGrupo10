import { pedir } from './cliente.js'

/**
 * POST /api/verificacion-telefono. Pide un código de un solo uso para verificar el teléfono.
 * Devuelve { codigo, enlace, vence, modo }: con modo "telegram", `enlace` abre el bot con el código cargado;
 * con modo "simulado" no hay enlace y el código se confirma con confirmarVerificacionSimulada.
 * 429 LIMITE_EXCEDIDO si ya pidió demasiados en la última hora.
 */
export function pedirCodigoVerificacion() {
  return pedir('/verificacion-telefono', { metodo: 'POST' })
}

/** POST /api/verificacion-telefono/simulada. Solo en modo simulado: confirma el código propio con un número. */
export function confirmarVerificacionSimulada(codigo, telefono) {
  return pedir('/verificacion-telefono/simulada', { metodo: 'POST', cuerpo: { codigo, telefono } })
}
