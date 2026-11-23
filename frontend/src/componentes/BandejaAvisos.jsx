import { listarAvisos } from '../api/reclamos.js'
import { formatearFecha } from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError } from './Aviso.jsx'

/**
 * Bandeja de avisos del usuario actual. Es donde un agente se entera de que a su área le
 * asignaron un reclamo, y donde el ciudadano ve las novedades de los suyos y de los que apoya.
 */
export default function BandejaAvisos({ usuario, onAbrir, onSeguir }) {
  const { datos, error, cargando, recargar } = useCarga(() => listarAvisos(), [usuario.id])
  const avisos = datos ?? []

  return (
    <section>
      <div className="cabecera-pagina cabecera-compacta">
        <div><span className="antetitulo">MANTENETE AL DÍA</span><h2>Avisos</h2><p>Las novedades de tus reclamos, en un solo lugar.</p></div>
      </div>
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
                    {aviso.abrible === false ? (
                      <span>{aviso.numeroReclamo}</span>
                    ) : (
                      <button type="button" className="enlace"
                        onClick={() => (aviso.seguimiento ? onSeguir : onAbrir)(aviso.numeroReclamo)}>
                        {aviso.numeroReclamo}{aviso.seguimiento && ' (lo apoyás)'}
                      </button>
                    )}
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
