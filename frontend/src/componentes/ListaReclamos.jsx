import { useState } from 'react'
import { listarReclamos } from '../api/reclamos.js'
import { ESTADOS, formatearFecha } from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError } from './Aviso.jsx'
import { InsigniaEstado, InsigniaPrioridad, InsigniaVencido } from './Insignias.jsx'

/**
 * Tabla de reclamos visibles para el usuario. El backend filtra por rol; acá solo se eligen
 * los filtros opcionales. Con estadoFijo la tabla muestra un único estado y oculta ese filtro.
 */
export default function ListaReclamos({ usuario, areas = [], estadoFijo, mensajeVacio, onAbrir }) {
  const [estado, setEstado] = useState('')
  const [areaId, setAreaId] = useState('')
  const esAdministrador = usuario.rol === 'ADMINISTRADOR'
  const estadoConsulta = estadoFijo ?? estado
  const areaConsulta = esAdministrador && !estadoFijo ? areaId : ''

  const { datos, error, cargando, recargar } = useCarga(
    () => listarReclamos({ estado: estadoConsulta, areaId: areaConsulta }),
    [usuario.id, estadoConsulta, areaConsulta],
  )
  const reclamos = datos ?? []
  const pendientes = reclamos.filter((reclamo) => ['INGRESADO', 'ASIGNADO', 'EN_PROCESO'].includes(reclamo.estado)).length
  const finalizados = reclamos.filter((reclamo) => ['RESUELTO', 'CERRADO'].includes(reclamo.estado)).length

  return (
    <section>
      {datos && !error && !cargando && !estadoFijo && (
        <div className="resumen-reclamos" aria-label="Resumen del listado filtrado">
          <div className="resumen-item"><span className="resumen-punto punto-total" /><div><span>En este listado</span><strong>{reclamos.length}</strong></div></div>
          <div className="resumen-item"><span className="resumen-punto punto-pendiente" /><div><span>En seguimiento</span><strong>{pendientes}</strong></div></div>
          <div className="resumen-item"><span className="resumen-punto punto-resuelto" /><div><span>Con solución</span><strong>{finalizados}</strong></div></div>
        </div>
      )}
      <div className="filtros">
        {!estadoFijo && (
          <label>
            <span>Estado</span>
            <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
              <option value="">Todos</option>
              {Object.entries(ESTADOS).map(([valor, etiqueta]) => (
                <option key={valor} value={valor}>
                  {etiqueta}
                </option>
              ))}
            </select>
          </label>
        )}
        {esAdministrador && !estadoFijo && (
          <label>
            <span>Área</span>
            <select value={areaId} onChange={(evento) => setAreaId(evento.target.value)}>
              <option value="">Todas</option>
              {areas.map((area) => (
                <option key={area.id} value={area.id}>
                  {area.nombre}
                </option>
              ))}
            </select>
          </label>
        )}
        <button type="button" className="boton-secundario" onClick={recargar}>
          Actualizar
        </button>
      </div>

      <AvisoError error={error} />
      {cargando && <p className="estado-carga">Cargando reclamos…</p>}
      {!cargando && !error && reclamos.length === 0 && (
        <p className="vacio">{mensajeVacio ?? 'No hay reclamos con esos filtros.'}</p>
      )}

      {reclamos.length > 0 && (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Número</th>
                <th>Categoría</th>
                <th>Barrio y dirección</th>
                <th>Estado</th>
                <th>Prioridad</th>
                <th>Área</th>
                <th>Vence</th>
              </tr>
            </thead>
            <tbody>
              {reclamos.map((reclamo) => (
                <tr key={reclamo.numero}>
                  <td>
                    <button type="button" className="enlace numero-reclamo" onClick={() => onAbrir(reclamo.numero)}>
                      {reclamo.numero}
                    </button>
                  </td>
                  <td>{reclamo.categoria.nombre}</td>
                  <td>
                    {reclamo.barrio.nombre}
                    <span className="secundario">{reclamo.direccion}</span>
                  </td>
                  <td>
                    <InsigniaEstado estado={reclamo.estado} />
                  </td>
                  <td>
                    <InsigniaPrioridad prioridad={reclamo.prioridad} />
                  </td>
                  <td>{reclamo.area ? reclamo.area.nombre : <span className="secundario">Sin asignar</span>}</td>
                  <td>
                    {formatearFecha(reclamo.fechaLimite)} <InsigniaVencido vencido={reclamo.vencido} />
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
