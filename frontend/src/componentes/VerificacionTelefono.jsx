import { useEffect, useState } from 'react'
import { usuarioActual } from '../api/sesion.js'
import { confirmarVerificacionSimulada, pedirCodigoVerificacion } from '../api/verificacion.js'
import { formatearFecha } from '../dominio/etiquetas.js'
import { AvisoError } from './Aviso.jsx'

/** Cada cuánto se pregunta al backend si el bot ya confirmó el código. */
export const INTERVALO_CONSULTA_MS = 3000

/**
 * Paso previo al primer reclamo: verificar el teléfono. Un número verificado vale para una sola cuenta.
 *
 * Con Telegram, el ciudadano abre el enlace, el bot le pide compartir su contacto y confirma el código; mientras
 * tanto la pantalla consulta /auth/yo hasta ver el teléfono verificado. En modo simulado (sin bot) el número se
 * escribe en un formulario.
 */
export default function VerificacionTelefono({ onVerificado }) {
  const [emitido, setEmitido] = useState(null)
  const [telefono, setTelefono] = useState('')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  const esperandoBot = emitido?.modo === 'telegram'

  useEffect(() => {
    if (!esperandoBot) {
      return undefined
    }
    const temporizador = setInterval(async () => {
      try {
        const actual = await usuarioActual()
        if (actual?.telefonoVerificado) {
          onVerificado(actual)
        }
      } catch {
        // Un corte momentáneo no interrumpe la espera: se vuelve a consultar en el próximo intervalo.
      }
    }, INTERVALO_CONSULTA_MS)
    return () => clearInterval(temporizador)
  }, [esperandoBot, onVerificado])

  async function pedirCodigo() {
    setError(null)
    setEnviando(true)
    try {
      setEmitido(await pedirCodigoVerificacion())
    } catch (fallo) {
      setError(fallo)
    } finally {
      setEnviando(false)
    }
  }

  async function confirmar(evento) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      onVerificado(await confirmarVerificacionSimulada(emitido.codigo, telefono.trim()))
    } catch (fallo) {
      setError(fallo)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="verificacion">
      <h2>Verificá tu teléfono</h2>
      <p className="ayuda">
        Para crear o reabrir reclamos necesitamos verificar tu teléfono. Cada número se puede usar en una sola
        cuenta. Lo guardamos para que el municipio pueda contactarte: no se muestra completo ni se comparte.
      </p>
      <AvisoError error={error} />

      {!emitido && (
        <button type="button" className="boton-primario" disabled={enviando} onClick={pedirCodigo}>
          {enviando ? 'Pidiendo código…' : 'Pedir código de verificación'}
        </button>
      )}

      {emitido && (
        <div className="codigo-verificacion">
          <p>
            Tu código es <strong className="codigo">{emitido.codigo}</strong>. Vence el {formatearFecha(emitido.vence)}.
          </p>

          {esperandoBot ? (
            <>
              <ol className="pasos">
                <li>
                  Abrí el bot de Telegram:{' '}
                  <a href={emitido.enlace} target="_blank" rel="noopener noreferrer">
                    abrir en Telegram
                  </a>
                  .
                </li>
                <li>Tocá Iniciar y después Compartir mi teléfono.</li>
                <li>Volvé a esta pantalla: se actualiza sola cuando el bot confirma.</li>
              </ol>
              <p className="estado-carga" role="status">
                Esperando la confirmación de Telegram…
              </p>
            </>
          ) : (
            <form className="formulario" onSubmit={confirmar}>
              <p className="ayuda">
                Modo simulado: el sistema no está conectado a Telegram. Escribí tu número con el código de país
                para confirmar el código.
              </p>
              <label>
                <span>Teléfono</span>
                <input
                  type="tel"
                  value={telefono}
                  onChange={(evento) => setTelefono(evento.target.value)}
                  placeholder="+54 9 11 5555-0000"
                  required
                  maxLength={25}
                />
              </label>
              <button type="submit" className="boton-primario" disabled={enviando}>
                Confirmar teléfono
              </button>
            </form>
          )}

          <button type="button" className="enlace" disabled={enviando} onClick={pedirCodigo}>
            Pedir otro código
          </button>
        </div>
      )}
    </section>
  )
}
