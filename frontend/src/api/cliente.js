// Único punto de salida HTTP de la aplicación. Ningún componente llama a fetch directamente.

const URL_BASE = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '')

// CSRF: el backend deja el token en la cookie XSRF-TOKEN y lo espera en este encabezado en cada POST y PATCH.
const COOKIE_CSRF = 'XSRF-TOKEN'
const ENCABEZADO_CSRF = 'X-XSRF-TOKEN'
const METODOS_SEGUROS = ['GET', 'HEAD']

let alPerderSesion = null

/**
 * Registra qué hacer cuando el backend responde 401 a un pedido que necesitaba sesión (la sesión venció o se
 * cerró en otra pestaña). Devuelve la función para darlo de baja.
 */
export function alVencerSesion(manejador) {
  alPerderSesion = manejador
  return () => {
    if (alPerderSesion === manejador) {
      alPerderSesion = null
    }
  }
}

function leerCookie(nombre) {
  const prefijo = `${nombre}=`
  const cookie = document.cookie.split('; ').find((candidata) => candidata.startsWith(prefijo))
  return cookie ? decodeURIComponent(cookie.slice(prefijo.length)) : null
}

/**
 * El token CSRF de la cookie. Si todavía no hay (por ejemplo, justo después de cerrar sesión), un GET a una
 * ruta pública hace que el backend la mande.
 */
async function tokenCsrf() {
  const token = leerCookie(COOKIE_CSRF)
  if (token) {
    return token
  }
  try {
    await fetch(`${URL_BASE}/categorias`, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
  } catch {
    // Si no hay conexión, el pedido real va a fallar y explicarlo.
  }
  return leerCookie(COOKIE_CSRF)
}

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
 * Ejecuta una llamada a la API. La identidad viaja en la cookie de sesión, que el navegador manda solo.
 *
 * @param {string} ruta ruta relativa a la URL base, por ejemplo "/reclamos"
 * @param {object} opciones metodo, cuerpo, parametros de consulta y avisarSiNoHaySesion (false en las rutas de
 *   login, donde un 401 es una respuesta esperada y no una sesión vencida)
 */
export async function pedir(ruta, { metodo = 'GET', cuerpo, parametros, avisarSiNoHaySesion = true } = {}) {
  const encabezados = { Accept: 'application/json' }
  if (cuerpo !== undefined) {
    encabezados['Content-Type'] = 'application/json'
  }
  if (!METODOS_SEGUROS.includes(metodo)) {
    const token = await tokenCsrf()
    if (token) {
      encabezados[ENCABEZADO_CSRF] = token
    }
  }

  let respuesta
  try {
    respuesta = await fetch(armarUrl(ruta, parametros), {
      method: metodo,
      headers: encabezados,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      credentials: 'same-origin',
    })
  } catch {
    throw new ErrorApi({
      status: 0,
      codigo: 'SIN_CONEXION',
      mensaje: 'No se pudo conectar con el servidor. Verificá que el backend esté levantado.',
    })
  }

  if (!respuesta.ok) {
    const error = await leerError(respuesta)
    if (error.status === 401 && avisarSiNoHaySesion && alPerderSesion) {
      alPerderSesion(error)
    }
    throw error
  }
  return respuesta.status === 204 ? null : respuesta.json()
}
