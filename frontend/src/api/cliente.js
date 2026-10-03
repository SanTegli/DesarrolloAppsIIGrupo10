// Único punto de salida HTTP de la aplicación. Ningún componente llama a fetch directamente.

const URL_BASE = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '')

/** Error con la forma del contrato REST: status, codigo, mensaje y detalles. */
export class ErrorApi extends Error {
  constructor({ status, codigo, mensaje, detalles = [] }) {
    super(mensaje)
    this.name = 'ErrorApi'
    this.status = status
    this.codigo = codigo
    this.detalles = detalles
  }
}

function armarUrl(ruta, parametros) {
  const consulta = new URLSearchParams()
  Object.entries(parametros ?? {}).forEach(([clave, valor]) => {
    if (valor !== undefined && valor !== null && valor !== '') {
      consulta.set(clave, valor)
    }
  })
  const texto = consulta.toString()
  return URL_BASE + ruta + (texto ? `?${texto}` : '')
}

async function leerError(respuesta) {
  try {
    const cuerpo = await respuesta.json()
    if (cuerpo && cuerpo.mensaje) {
      return new ErrorApi({
        status: respuesta.status,
        codigo: cuerpo.codigo,
        mensaje: cuerpo.mensaje,
        detalles: cuerpo.detalles ?? [],
      })
    }
  } catch {
    // El cuerpo no es JSON: pasa cuando responde el proxy y no el backend.
  }
  const sinBackend = respuesta.status >= 500
  return new ErrorApi({
    status: respuesta.status,
    codigo: sinBackend ? 'SIN_CONEXION' : 'ERROR_DESCONOCIDO',
    mensaje: sinBackend
      ? 'El servidor no responde. Verificá que el backend esté levantado en el puerto 8080.'
      : `El servidor respondió con el código ${respuesta.status}.`,
  })
}

/**
 * Ejecuta una llamada a la API.
 *
 * @param {string} ruta ruta relativa a la URL base, por ejemplo "/reclamos"
 * @param {object} opciones usuarioId (encabezado X-Usuario-Id), metodo, cuerpo y parametros de consulta
 */
export async function pedir(ruta, { usuarioId, metodo = 'GET', cuerpo, parametros } = {}) {
  const encabezados = { Accept: 'application/json' }
  if (usuarioId !== undefined && usuarioId !== null) {
    encabezados['X-Usuario-Id'] = String(usuarioId)
  }
  if (cuerpo !== undefined) {
    encabezados['Content-Type'] = 'application/json'
  }

  let respuesta
  try {
    respuesta = await fetch(armarUrl(ruta, parametros), {
      method: metodo,
      headers: encabezados,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    throw new ErrorApi({
      status: 0,
      codigo: 'SIN_CONEXION',
      mensaje: 'No se pudo conectar con el servidor. Verificá que el backend esté levantado.',
    })
  }

  if (!respuesta.ok) {
    throw await leerError(respuesta)
  }
  return respuesta.status === 204 ? null : respuesta.json()
}
