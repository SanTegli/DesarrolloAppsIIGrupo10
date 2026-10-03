import { useState } from 'react'
import { asignarArea, cambiarEstado, listarNotificaciones, obtenerReclamo } from '../api/reclamos.js'
import { ESTADOS, esAccionDefinitiva, formatearFecha, nombreDeAccion } from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError, AvisoExito } from './Aviso.jsx'
import { InsigniaEstado, InsigniaPrioridad, InsigniaVencido } from './Insignias.jsx'

/**
 * Detalle de un reclamo con sus acciones. Los botones salen de accionesDisponibles, que calcula
 * el backend para el usuario actual: la interfaz no repite las reglas de transición.
 */
export default function DetalleReclamo({ usuario, numero, areas, avisoInicial, onVolver }) {
  const [observacion, setObservacion] = useState('')
  const [areaId, setAreaId] = useState('')
  const [errorAccion, setErrorAccion] = useState(null)
  const [aviso, setAviso] = useState(avisoInicial ?? '')
  const [enviando, setEnviando] = useState(false)

  const { datos, error, cargando, recargar } = useCarga(
    () =>
      Promise.all([obtenerReclamo(usuario.id, numero), listarNotificaciones(usuario.id, numero)]).then(
        ([reclamo, notificaciones]) => ({ reclamo, notificaciones }),
      ),
    [usuario.id, numero],
  )

  async function ejecutar(operacion, mensajeExito) {
    setErrorAccion(null)
    setAviso('')
    setEnviando(true)
    try {
      await operacion()
      setObservacion('')
      setAreaId('')
      setAviso(mensajeExito)
      recargar()
    } catch (fallo) {
      setErrorAccion(fallo)
    } finally {
      setEnviando(false)
    }
  }

  if (!datos) {
    return (
      <section>
        <button type="button" className="enlace" onClick={onVolver}>
          Volver al listado
        </button>
        <AvisoError error={error} />
        {cargando && <p className="estado-carga">Cargando reclamo…</p>}
      </section>
    )
  }

  const { reclamo, notificaciones } = datos
  const cambios = reclamo.accionesDisponibles.filter((destino) => destino !== 'ASIGNADO')
  const puedeAsignar = reclamo.accionesDisponibles.includes('ASIGNADO')
  const areasElegibles = areas.filter((area) => area.activa && area.id !== reclamo.area?.id)
  const verboAsignar = reclamo.area ? 'Reasignar' : 'Asignar'

  return (
    <section className="detalle">
      <button type="button" className="enlace" onClick={onVolver}>
        Volver al listado
      </button>

      <div className="detalle-titulo">
        <h2>{reclamo.numero}</h2>
        <InsigniaEstado estado={reclamo.estado} />
        <InsigniaPrioridad prioridad={reclamo.prioridad} />
        <InsigniaVencido vencido={reclamo.vencido} />
      </div>

      <AvisoExito>{aviso}</AvisoExito>
      <AvisoError error={errorAccion} />

      <div className="detalle-cuerpo">
        <div>
          <p className="descripcion">{reclamo.descripcion}</p>
          <dl className="datos">
            <dt>Categoría</dt>
            <dd>{reclamo.categoria.nombre}</dd>
            <dt>Ubicación</dt>
            <dd>
              {reclamo.direccion}, {reclamo.barrio.nombre}
            </dd>
            <dt>Ciudadano</dt>
            <dd>{reclamo.ciudadano.nombreCompleto}</dd>
            <dt>Área responsable</dt>
            <dd>{reclamo.area ? reclamo.area.nombre : 'Sin asignar'}</dd>
            <dt>Agente a cargo</dt>
            <dd>{reclamo.agente ? reclamo.agente.nombreCompleto : 'Ninguno todavía'}</dd>
            <dt>Creado</dt>
            <dd>{formatearFecha(reclamo.fechaCreacion)}</dd>
            <dt>Fecha límite</dt>
            <dd>{formatearFecha(reclamo.fechaLimite)}</dd>
          </dl>

          {(cambios.length > 0 || puedeAsignar) && (
            <div className="panel-acciones">
              <h3>Acciones</h3>
              <label>
                <span>Observación (opcional)</span>
                <input
                  type="text"
                  value={observacion}
                  onChange={(evento) => setObservacion(evento.target.value)}
                  maxLength={1000}
                />
              </label>

              {cambios.length > 0 && (
                <div className="acciones">
                  {cambios.map((destino) => {
                    const nombre = nombreDeAccion(reclamo.estado, destino)
                    return (
                      <button
                        key={destino}
                        type="button"
                        className={esAccionDefinitiva(destino) ? 'boton-peligro' : 'boton-primario'}
                        disabled={enviando}
                        onClick={() =>
                          ejecutar(
                            () => cambiarEstado(usuario.id, reclamo.numero, destino, observacion.trim()),
                            `El reclamo pasó a ${ESTADOS[destino]}.`,
                          )
                        }
                      >
                        {nombre}
                      </button>
                    )
                  })}
                </div>
              )}

              {puedeAsignar && (
                <form
                  className="asignacion"
                  onSubmit={(evento) => {
                    evento.preventDefault()
                    const area = areas.find((candidata) => String(candidata.id) === areaId)
                    ejecutar(
                      () => asignarArea(usuario.id, reclamo.numero, Number(areaId), observacion.trim()),
                      `El reclamo quedó asignado a ${area?.nombre ?? 'el área elegida'}.`,
                    )
                  }}
                >
                  <label>
                    <span>{verboAsignar} área</span>
                    <select value={areaId} onChange={(evento) => setAreaId(evento.target.value)} required>
                      <option value="">Elegí un área</option>
                      {areasElegibles.map((area) => (
                        <option key={area.id} value={area.id}>
                          {area.nombre}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button type="submit" className="boton-primario" disabled={enviando}>
                    {verboAsignar}
                  </button>
                </form>
              )}
            </div>
          )}
        </div>

        <div>
          <h3>Historial</h3>
          <ol className="historial">
            {reclamo.historial.map((cambio, indice) => (
              <li key={indice}>
                <InsigniaEstado estado={cambio.estadoNuevo} />
                <span className="secundario">{formatearFecha(cambio.fecha)}</span>
                <p>
                  {cambio.usuario ? cambio.usuario.nombreCompleto : 'Sistema'}
                  {cambio.observacion ? `: ${cambio.observacion}` : ''}
                </p>
              </li>
            ))}
          </ol>

          <h3>Avisos enviados</h3>
          {notificaciones.length === 0 ? (
            <p className="vacio">Este reclamo todavía no generó avisos.</p>
          ) : (
            <ul className="avisos-enviados">
              {notificaciones.map((notificacion, indice) => (
                <li key={indice}>
                  <span className="secundario">
                    {formatearFecha(notificacion.fechaEnvio)}, para {notificacion.destinatario.nombreCompleto}
                  </span>
                  <p>{notificacion.mensaje}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  )
}
