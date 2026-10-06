import { listarAvisos } from '../api/reclamos.js'
import { formatearFecha } from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError } from './Aviso.jsx'

/**
 * Bandeja de avisos del usuario actual. Es donde un agente se entera de que a su área le
 * asignaron un reclamo, y donde el ciudadano ve las novedades de los suyos.
 */
export default function BandejaAvisos({ usuario, onAbrir }) {
  const { datos, error, cargando, recargar } = useCarga(() => listarAvisos(usuario.id), [usuario.id])
  const avisos = datos ?? []

  return (
    <section>
      <h2>Avisos</h2>
      <div className="filtros">
        <button type="button" className="boton-secundario" onClick={recargar}>
          Actualizar
        </button>
      </div>

      <AvisoError error={error} />
      {cargando && <p className="estado-carga">Cargando avisos…</p>}
      {!cargando && !error && avisos.length === 0 && <p className="vacio">No tenés avisos.</p>}

      {avisos.length > 0 && (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Aviso</th>
                <th>Reclamo</th>
              </tr>
            </thead>
            <tbody>
              {avisos.map((aviso, indice) => (
                <tr key={indice}>
                  <td className="sin-corte">{formatearFecha(aviso.fechaEnvio)}</td>
                  <td>{aviso.mensaje}</td>
                  <td>
                    <button type="button" className="enlace" onClick={() => onAbrir(aviso.numeroReclamo)}>
                      {aviso.numeroReclamo}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
