import { pedir } from './cliente.js'

/**
 * GET /api/ubicaciones/sugerencias. Busca la dirección en el mapa (Georef, desde el backend) dentro del
 * municipio del barrio. Responde { disponible, sugerencias: [{ direccionNormalizada, latitud, longitud }] }.
 */
export function sugerirUbicaciones(direccion, barrioId) {
  return pedir('/ubicaciones/sugerencias', { parametros: { direccion, barrioId } })
}
