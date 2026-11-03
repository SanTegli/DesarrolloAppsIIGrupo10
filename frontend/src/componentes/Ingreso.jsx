import { useState } from 'react'
import { iniciarSesion, registrarse } from '../api/sesion.js'
import { AvisoError } from './Aviso.jsx'

const MINIMO_CONTRASENA = 8

/**
 * Pantalla de entrada: login con email y contraseña, o registro de un ciudadano nuevo. Al registrarse, entra
 * directamente con la cuenta recién creada.
 */
export default function Ingreso({ aviso, onIngreso }) {
  const [modo, setModo] = useState('entrar')

  return (
    <section className="ingreso" aria-labelledby="titulo-ingreso">
      {modo === 'entrar' ? (
        <FormularioEntrada aviso={aviso} onIngreso={onIngreso} onRegistrarse={() => setModo('registrarse')} />
      ) : (
        <FormularioRegistro onIngreso={onIngreso} onVolver={() => setModo('entrar')} />
      )}
    </section>
  )
}

function FormularioEntrada({ aviso, onIngreso, onRegistrarse }) {
  const [email, setEmail] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(evento) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      onIngreso(await iniciarSesion(email.trim(), contrasena))
    } catch (fallo) {
      setError(fallo)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={entrar}>
      <h2 id="titulo-ingreso">Iniciar sesión</h2>
      {aviso && (
        <p className="aviso-ubicacion" role="status">
          {aviso}
        </p>
      )}
      <AvisoError error={error} />
      <label>
        <span>Correo electrónico</span>
        <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        <span>Contraseña</span>
        <input
          type="password"
          autoComplete="current-password"
          value={contrasena}
          onChange={(e) => setContrasena(e.target.value)}
          required
        />
      </label>
      <div className="acciones">
        <button type="submit" className="boton-primario" disabled={enviando}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>
        <button type="button" className="boton-secundario" onClick={onRegistrarse}>
          Crear una cuenta
        </button>
      </div>
    </form>
  )
}

function FormularioRegistro({ onIngreso, onVolver }) {
  const [datos, setDatos] = useState({ dni: '', nombre: '', apellido: '', email: '', contrasena: '' })
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  const cambiar = (campo) => (evento) => setDatos((anteriores) => ({ ...anteriores, [campo]: evento.target.value }))

  async function registrar(evento) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    const limpios = {
      dni: datos.dni.trim(),
      nombre: datos.nombre.trim(),
      apellido: datos.apellido.trim(),
      email: datos.email.trim(),
      contrasena: datos.contrasena,
    }
    try {
      await registrarse(limpios)
      onIngreso(await iniciarSesion(limpios.email, limpios.contrasena))
    } catch (fallo) {
      setError(fallo)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={registrar}>
      <h2 id="titulo-ingreso">Crear una cuenta</h2>
      <p className="ayuda-campo">Para vecinos que quieren presentar reclamos. Los agentes municipales ya tienen su cuenta.</p>
      <AvisoError error={error} />
      <label>
        <span>DNI</span>
        <input inputMode="numeric" pattern="\d{7,8}" title="7 u 8 dígitos, sin puntos" value={datos.dni} onChange={cambiar('dni')} required />
      </label>
      <label>
        <span>Nombre</span>
        <input autoComplete="given-name" maxLength={80} value={datos.nombre} onChange={cambiar('nombre')} required />
      </label>
      <label>
        <span>Apellido</span>
        <input autoComplete="family-name" maxLength={80} value={datos.apellido} onChange={cambiar('apellido')} required />
      </label>
      <label>
        <span>Correo electrónico</span>
        <input type="email" autoComplete="email" value={datos.email} onChange={cambiar('email')} required />
      </label>
      <label>
        <span>Contraseña</span>
        <input
          type="password"
          autoComplete="new-password"
          minLength={MINIMO_CONTRASENA}
          maxLength={72}
          value={datos.contrasena}
          onChange={cambiar('contrasena')}
          required
        />
        <small>Al menos {MINIMO_CONTRASENA} caracteres.</small>
      </label>
      <div className="acciones">
        <button type="submit" className="boton-primario" disabled={enviando}>
          {enviando ? 'Creando la cuenta…' : 'Crear cuenta y entrar'}
        </button>
        <button type="button" className="boton-secundario" onClick={onVolver}>
          Ya tengo cuenta
        </button>
      </div>
    </form>
  )
}
