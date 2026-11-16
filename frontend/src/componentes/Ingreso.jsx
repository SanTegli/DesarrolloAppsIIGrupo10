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
      <div className="ingreso-presentacion">
        <span className="sobre-titulo">QUILMES · ATENCIÓN CIUDADANA</span>
        <h1>Tu barrio,<br />cada día mejor.</h1>
        <p>Un espacio para avisar lo que pasa, seguir tus reclamos y acompañar las mejoras de tu ciudad.</p>
        <svg className="ciudad-ilustracion" viewBox="0 0 520 210" fill="none" aria-hidden="true">
          <circle cx="410" cy="46" r="25" fill="#e6c77e" />
          <path d="M12 180H508M24 194H496" stroke="#8cbaa4" strokeWidth="2" />
          <path d="M74 179V90H141V179M154 179V48H231V179M247 179V105H318V179M337 179V74H402V179" fill="#36745f" stroke="#a5ceba" strokeWidth="2" />
          <path d="M164 48L192 23L221 48M257 105L282 83L309 105" stroke="#a5ceba" strokeWidth="2" />
          <path d="M91 108H103M114 108H126M91 131H103M114 131H126M173 70H186M200 70H213M173 94H186M200 94H213M173 118H186M200 118H213M264 125H277M290 125H303M353 95H366M379 95H391M353 119H366M379 119H391" stroke="#d3e8d8" strokeWidth="6" />
          <path d="M184 179V145H202V179M274 179V150H291V179M360 179V146H378V179M42 179V133M450 179V126" stroke="#a5ceba" strokeWidth="3" />
          <circle cx="42" cy="121" r="23" fill="#91b89a" />
          <circle cx="450" cy="110" r="31" fill="#91b89a" />
          <path d="M42 143V179M450 135V179" stroke="#d3e8d8" strokeWidth="3" />
        </svg>
        <div className="ingreso-pasos"><span>01 <strong>Contanos qué pasa</strong></span><span>02 <strong>Seguí la solución</strong></span></div>
      </div>
      <div className="ingreso-formulario">
        {modo === 'entrar' ? (
          <FormularioEntrada aviso={aviso} onIngreso={onIngreso} onRegistrarse={() => setModo('registrarse')} />
        ) : (
          <FormularioRegistro onIngreso={onIngreso} onVolver={() => setModo('entrar')} />
        )}
      </div>
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
