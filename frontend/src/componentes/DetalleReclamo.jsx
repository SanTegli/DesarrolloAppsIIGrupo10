import { useEffect, useState } from 'react'
import { asignarArea, cambiarEstado, listarNotificaciones, obtenerReclamo, recategorizar } from '../api/reclamos.js'
import {
  ESTADOS,
  describirPrioridad,
  enlaceMapa,
  esAccionDefinitiva,
  formatearFecha,
  nombreDeAccion,
  ordenarCategorias,
} from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError, AvisoExito } from './Aviso.jsx'
import { InsigniaEstado, InsigniaPrioridad, InsigniaVencido } from './Insignias.jsx'

/**
 * Mientras la IA revisa la prioridad, o sugiere la categoría de un reclamo de "Otro problema", en segundo plano,
 * el detalle se consulta cada 5 s durante un minuto (suele tardar menos de 20 s) y se recarga cuando terminó.
 */
export const REVISION_IA = { cada: 5000, intentos: 12 }

/**
 * Detalle de un reclamo con sus acciones. Los botones salen de accionesDisponibles, que calcula
 * el backend para el usuario actual: la interfaz no repite las reglas de transición.
 */
export default function DetalleReclamo({
  usuario,
  numero,
  areas,
  categorias = [],
  avisoInicial,
  onVolver,
  revisionIA = REVISION_IA,
}) {
  const [observacion, setObservacion] = useState('')
  const [areaId, setAreaId] = useState('')
  // null: todavía no tocó la lista, y se ofrece la categoría que sugirió la IA.
  const [categoriaNueva, setCategoriaNueva] = useState(null)
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

  const esOtra = (reclamo) => categorias.some((categoria) => categoria.otra && categoria.id === reclamo.categoria.id)
  const esperaCategoria = (reclamo) => esOtra(reclamo) && !reclamo.motivoSugerencia
  const esperandoIA =
    Boolean(datos) && (datos.reclamo.origenPrioridad === 'PENDIENTE_IA' || esperaCategoria(datos.reclamo))
  useEffect(() => {
    if (!esperandoIA) return undefined
    let vigente = true
    let intentos = 0
    let temporizador
    // Solo mira el reclamo; si la IA terminó, recarga todo (también los avisos, que traen el cambio).
    const programar = () => {
      temporizador = setTimeout(async () => {
        intentos += 1
        try {
          const actual = await obtenerReclamo(numero)
          if (vigente && actual.origenPrioridad !== 'PENDIENTE_IA' && !esperaCategoria(actual)) {
            recargar()
            return
          }
        } catch {
          // Un error pasajero no corta la espera: se vuelve a probar en el próximo turno.
        }
        if (vigente && intentos < revisionIA.intentos) programar()
      }, revisionIA.cada)
    }
    programar()
    return () => {
      vigente = false
      clearTimeout(temporizador)
    }
    // esperaCategoria depende solo de las categorías, que no cambian mientras el detalle está abierto.
  }, [esperandoIA, numero, recargar, revisionIA])

  async function ejecutar(operacion, mensajeExito) {
    setErrorAccion(null)
    setAviso('')
    setEnviando(true)
    try {
      await operacion()
      setObservacion('')
      setAreaId('')
      setCategoriaNueva(null)
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
  // Solo las áreas del municipio del reclamo: el backend rechaza las de otro.
  const areasElegibles = areas.filter(
    (area) => area.activa && area.id !== reclamo.area?.id && area.municipio?.id === reclamo.municipio.id,
  )
  const verboAsignar = reclamo.area ? 'Reasignar' : 'Asignar'
  // Recategorizar: solo un administrador y antes de asignar un área (el backend lo vuelve a validar).
  const puedeRecategorizar = usuario.rol === 'ADMINISTRADOR' && reclamo.estado === 'INGRESADO'
  const categoriasElegibles = ordenarCategorias(categorias).filter(
    (categoria) => !categoria.otra && categoria.id !== reclamo.categoria.id,
  )
  const categoriaElegida =
    categoriaNueva ?? (reclamo.categoriaSugerida ? String(reclamo.categoriaSugerida.id) : '')
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
            {reclamo.categoriaPropuesta && (
              <>
                <dt>Tipo de problema</dt>
                <dd>“{reclamo.categoriaPropuesta}”, en palabras del vecino</dd>
              </>
            )}
            <dt>Prioridad</dt>
            <dd>{describirPrioridad(reclamo)}</dd>
            <dt>Ubicación</dt>
            <dd>
              <span>
                {reclamo.direccion}, {reclamo.municipio.nombre}
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

          {puedeRecategorizar && (
            <form
              className="panel-acciones recategorizacion"
              onSubmit={(evento) => {
                evento.preventDefault()
                const nueva = categorias.find((categoria) => String(categoria.id) === categoriaElegida)
                ejecutar(
                  () => recategorizar(reclamo.numero, Number(categoriaElegida), observacion.trim()),
                  `El reclamo ahora es de ${nueva?.nombre ?? 'la categoría elegida'}.`,
                )
              }}
            >
              <h3>Categoría</h3>
              <SugerenciaIA reclamo={reclamo} esperando={esperaCategoria(reclamo)} />
              <label>
                <span>Cambiar la categoría</span>
                <select
                  value={categoriaElegida}
                  onChange={(evento) => setCategoriaNueva(evento.target.value)}
                  required
                >
                  <option value="">Elegí una categoría</option>
                  {categoriasElegibles.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                      {categoria.id === reclamo.categoriaSugerida?.id
                        ? `${categoria.nombre} (sugerida por la IA)`
                        : categoria.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <p className="ayuda">
                Con la categoría nueva cambia el plazo de atención, y el reclamo se asigna solo si hay un área de{' '}
                {reclamo.municipio.nombre} que la atienda. La observación de arriba también queda en el historial.
              </p>
              <div className="acciones">
                <button type="submit" className="boton-primario" disabled={enviando}>
                  Recategorizar
                </button>
              </div>
            </form>
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

/** Lo que dijo la IA sobre la categoría de un reclamo de "Otro problema": la ve el administrador al recategorizar. */
function SugerenciaIA({ reclamo, esperando }) {
  if (esperando) {
    return (
      <p className="sugerencia-ia" role="status">
        La IA todavía está analizando qué categoría corresponde.
      </p>
    )
  }
  if (!reclamo.motivoSugerencia) {
    return null
  }
  return (
    <p className="sugerencia-ia">
      {reclamo.categoriaSugerida ? (
        <>
          La IA sugiere <strong>{reclamo.categoriaSugerida.nombre}</strong>: {reclamo.motivoSugerencia}
        </>
      ) : (
        <>La IA no sugirió ninguna categoría: {reclamo.motivoSugerencia}</>
      )}
    </p>
  )
}

/** La foto del problema. Los reclamos de antes de la foto obligatoria no tienen. */
export function FotoReclamo({ foto }) {
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
