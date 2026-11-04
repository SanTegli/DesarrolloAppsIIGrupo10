import { useState } from 'react'
import { cambiarBloqueo, listarUsuarios } from '../api/usuarios.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError, AvisoExito } from './Aviso.jsx'

/**
 * Cuentas de ciudadanos para el administrador. Una cuenta bloqueada puede entrar y consultar sus reclamos, pero
 * no crear, reabrir ni verificar el teléfono.
 */
export default function CuentasCiudadanos() {
  const { datos, error, cargando, recargar } = useCarga(listarUsuarios, [])
  const [errorAccion, setErrorAccion] = useState(null)
  const [aviso, setAviso] = useState('')
  const [enviando, setEnviando] = useState(null)

  async function alternar(cuenta) {
    setErrorAccion(null)
    setAviso('')
    setEnviando(cuenta.id)
    try {
      const actualizada = await cambiarBloqueo(cuenta.id, !cuenta.bloqueado)
      setAviso(`${actualizada.nombreCompleto}: cuenta ${actualizada.bloqueado ? 'bloqueada' : 'desbloqueada'}.`)
      recargar()
    } catch (fallo) {
      setErrorAccion(fallo)
    } finally {
      setEnviando(null)
    }
  }

  const ciudadanos = (datos ?? []).filter((cuenta) => cuenta.rol === 'CIUDADANO')

  return (
    <>
      <h2>Cuentas de ciudadanos</h2>
      <AvisoError error={error ?? errorAccion} />
      <AvisoExito>{aviso}</AvisoExito>
      {cargando && !datos && <p className="estado-carga">Cargando cuentas…</p>}
      {datos && (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Ciudadano</th>
                <th>Teléfono</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {ciudadanos.map((cuenta) => (
                <tr key={cuenta.id}>
                  <td>{cuenta.nombreCompleto}</td>
                  <td>{cuenta.telefonoVerificado ?? 'Sin verificar'}</td>
                  <td>{cuenta.bloqueado ? 'Bloqueada' : 'Activa'}</td>
                  <td>
                    <button
                      type="button"
                      className={cuenta.bloqueado ? 'boton-secundario' : 'boton-peligro'}
                      disabled={enviando !== null}
                      onClick={() => alternar(cuenta)}
                      aria-label={`${cuenta.bloqueado ? 'Desbloquear' : 'Bloquear'} a ${cuenta.nombreCompleto}`}
                    >
                      {cuenta.bloqueado ? 'Desbloquear' : 'Bloquear'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
