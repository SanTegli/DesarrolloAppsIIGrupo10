import { useState } from 'react'
import { asignarArea, cambiarEstado, listarNotificaciones, obtenerReclamo } from '../api/reclamos.js'
import {
  ESTADOS,
  describirPrioridad,
  enlaceMapa,
  esAccionDefinitiva,
  formatearFecha,
  nombreDeAccion,
} from '../dominio/etiquetas.js'
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
      Promise.all([obtenerReclamo(numero), listarNotificaciones(numero)]).then(
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
  // Reabrir exige explicar por qué: el backend responde 400 sin motivo, y la interfaz lo pide antes.
  const puedeReabrir = reclamo.estado === 'RESUELTO' && cambios.includes('EN_PROCESO')
  const faltaMotivo = puedeReabrir && observacion.trim() === ''

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
          <FotoReclamo foto={reclamo.foto} />
          {reclamo.cantidadApoyos > 0 && (
            <p className="apoyos">
              {reclamo.cantidadApoyos === 1
                ? '1 vecino apoya este reclamo: le pasa lo mismo.'
                : `${reclamo.cantidadApoyos} vecinos apoyan este reclamo: les pasa lo mismo.`}
            </p>
          )}
          <dl className="datos">
            <dt>Categoría</dt>
            <dd>{reclamo.categoria.nombre}</dd>
            <dt>Prioridad</dt>
            <dd>{describirPrioridad(reclamo)}</dd>
            <dt>Ubicación</dt>
            <dd>
              <span>
                {reclamo.direccion}, {reclamo.barrio.nombre}
              </span>
              <UbicacionEnMapa ubicacion={reclamo.ubicacion} />
            </dd>
            <dt>Ciudadano</dt>
            <dd>{reclamo.ciudadano.nombreCompleto}</dd>
            <dt>Área responsable</dt>
            <dd>
              {reclamo.area
                ? reclamo.area.nombre
                : reclamo.asignacionPendiente
                  ? 'Sin asignar: el servicio de cobertura no respondió'
                  : 'Sin asignar'}
            </dd>
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
                <span>{puedeReabrir ? 'Observación (para reabrir, contá por qué sigue el problema)' : 'Observación (opcional)'}</span>
                <input
                  type="text"
                  value={observacion}
                  onChange={(evento) => setObservacion(evento.target.value)}
                  maxLength={1000}
                />
              </label>

              {puedeReabrir && (
                <p className="ayuda">
                  Un reclamo se puede reabrir una sola vez y dentro de los 7 días de resuelto. Si el problema vuelve
                  después, creá un reclamo nuevo.
                </p>
              )}

              {cambios.length > 0 && (
                <div className="acciones">
                  {cambios.map((destino) => {
                    const nombre = nombreDeAccion(reclamo.estado, destino)
                    return (
                      <button
                        key={destino}
                        type="button"
                        className={esAccionDefinitiva(destino) ? 'boton-peligro' : 'boton-primario'}
                        disabled={enviando || (destino === 'EN_PROCESO' && faltaMotivo)}
                        title={destino === 'EN_PROCESO' && faltaMotivo ? 'Escribí el motivo para reabrir' : undefined}
                        onClick={() =>
                          ejecutar(
                            () => cambiarEstado(reclamo.numero, destino, observacion.trim()),
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
                      () => asignarArea(reclamo.numero, Number(areaId), observacion.trim()),
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

/** La foto del problema. Los reclamos de antes de la foto obligatoria no tienen. */
function FotoReclamo({ foto }) {
  if (!foto) {
    return <p className="secundario">Este reclamo no tiene foto.</p>
  }
  return (
    <a className="foto-reclamo" href={foto} target="_blank" rel="noopener noreferrer">
      <img src={foto} alt="Foto del problema" loading="lazy" />
    </a>
  )
}

/** Si el ciudadano eligió la dirección en el mapa: cómo la normalizó el geocodificador y el enlace a Maps. */
function UbicacionEnMapa({ ubicacion }) {
  const enlace = ubicacion?.estado === 'CONFIRMADA' ? enlaceMapa(ubicacion.latitud, ubicacion.longitud) : null
  if (!enlace) {
    return <small className="secundario ubicacion-mapa">Sin marcar en el mapa</small>
  }
  return (
    <small className="ubicacion-mapa">
      {ubicacion.direccionNormalizada}.{' '}
      <a href={enlace} target="_blank" rel="noopener noreferrer">
        Ver en Google Maps
      </a>
    </small>
  )
}
