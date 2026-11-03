import { pedir } from './cliente.js'

// Login con sesión del servidor. Ninguna de estas llamadas avisa "sesión vencida": un 401 acá es la respuesta.
const SIN_AVISO = { avisarSiNoHaySesion: false }

/** GET /api/auth/yo. El usuario de la sesión, o null si no hay sesión. */
export async function usuarioActual() {
  try {
    return await pedir('/auth/yo', SIN_AVISO)
  } catch (error) {
    if (error.status === 401) {
      return null
    }
    throw error
  }
}

/** POST /api/auth/login. Devuelve el usuario; 401 CREDENCIALES_INVALIDAS si el email o la contraseña no son correctos. */
export function iniciarSesion(email, contrasena) {
  return pedir('/auth/login', { ...SIN_AVISO, metodo: 'POST', cuerpo: { email, contrasena } })
}

/** POST /api/auth/registro. Crea la cuenta de un ciudadano; después hay que iniciar sesión. */
export function registrarse({ dni, nombre, apellido, email, contrasena }) {
  return pedir('/auth/registro', { ...SIN_AVISO, metodo: 'POST', cuerpo: { dni, nombre, apellido, email, contrasena } })
}

/** POST /api/auth/logout. */
export function cerrarSesion() {
  return pedir('/auth/logout', { ...SIN_AVISO, metodo: 'POST' })
}
