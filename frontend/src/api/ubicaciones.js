import { pedir } from './cliente.js'

/**
 * GET /api/ubicaciones/sugerencias. Busca la dirección en el mapa (Georef, desde el backend) en todos los
 * municipios. Responde { disponible, sugerencias: [{ direccionNormalizada, latitud, longitud, municipio }] }.
 */
export function sugerirUbicaciones(direccion) {
  return pedir('/ubicaciones/sugerencias', { parametros: { direccion } })
}
