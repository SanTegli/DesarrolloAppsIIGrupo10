import { useEffect, useState } from 'react'
import { crearReclamo } from '../api/reclamos.js'
import { sugerirUbicaciones } from '../api/ubicaciones.js'
import { enlaceMapa } from '../dominio/etiquetas.js'
import { AvisoError } from './Aviso.jsx'

const MAXIMO_DESCRIPCION = 1000
const MAXIMO_DIRECCION = 200
const MINIMO_BUSQUEDA = 3
const NINGUNA = ''
// Igual que el backend: JPEG o PNG, hasta 5 MB. El backend vuelve a validar el contenido real del archivo.
export const TIPOS_FOTO = ['image/jpeg', 'image/png']
export const MAXIMO_FOTO = 5 * 1024 * 1024

/**
 * Alta de un reclamo. El área no se elige: la asigna el backend con la estrategia activa.
 * La dirección se puede buscar en el mapa: si hay varias interpretaciones, el ciudadano elige una. Es
 * opcional: sin elegir, el reclamo se crea igual y la ubicación queda pendiente.
 * La foto es obligatoria: es la prueba del problema.
 */
export default function FormularioReclamo({ categorias, barrios, onCreado }) {
  const [categoriaId, setCategoriaId] = useState('')
  const [barrioId, setBarrioId] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [direccion, setDireccion] = useState('')
  const [busqueda, setBusqueda] = useState(null)
  const [buscando, setBuscando] = useState(false)
  const [elegida, setElegida] = useState(NINGUNA)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [foto, setFoto] = useState(null)
  const [errorFoto, setErrorFoto] = useState('')
  const vistaPrevia = useVistaPrevia(foto)

  function elegirFoto(evento) {
    const archivo = evento.target.files?.[0] ?? null
    setErrorFoto('')
    if (archivo && !TIPOS_FOTO.includes(archivo.type)) {
      setErrorFoto('La foto tiene que ser JPEG o PNG.')
      evento.target.value = ''
      setFoto(null)
      return
    }
    if (archivo && archivo.size > MAXIMO_FOTO) {
      setErrorFoto('La foto pesa más de 5 MB. Sacala con menos resolución o recortala.')
      evento.target.value = ''
      setFoto(null)
      return
    }
    setFoto(archivo)
  }

  const categoria = categorias.find((candidata) => String(candidata.id) === categoriaId)
  const barrio = barrios.find((candidato) => String(candidato.id) === barrioId)
  const puedeBuscar = barrioId !== '' && direccion.trim().length >= MINIMO_BUSQUEDA && !buscando

  // Una búsqueda vale para la dirección y el barrio con que se hizo.
  function olvidarBusqueda() {
    setBusqueda(null)
    setElegida(NINGUNA)
  }

  async function buscar() {
    setBuscando(true)
    olvidarBusqueda()
    try {
      setBusqueda(await sugerirUbicaciones(direccion.trim(), Number(barrioId)))
    } catch (fallo) {
      setBusqueda({ error: fallo })
    } finally {
      setBuscando(false)
    }
  }

  async function enviar(evento) {
    evento.preventDefault()
    if (!foto) {
      setErrorFoto('Elegí una foto del problema: es obligatoria.')
      return
    }
    setError(null)
    setEnviando(true)
    const sugerencia = elegida === NINGUNA ? null : busqueda?.sugerencias?.[Number(elegida)]
    try {
      const reclamo = await crearReclamo({
        categoriaId: Number(categoriaId),
        barrioId: Number(barrioId),
        descripcion: descripcion.trim(),
        direccion: direccion.trim(),
        foto,
        ubicacion: sugerencia
          ? {
              latitud: sugerencia.latitud,
              longitud: sugerencia.longitud,
              direccionNormalizada: sugerencia.direccionNormalizada,
            }
          : undefined,
      })
      onCreado(reclamo)
    } catch (fallo) {
      setError(fallo)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <h2>Crear reclamo</h2>
      <AvisoError error={error} />

      <label>
        <span>Categoría</span>
        <select value={categoriaId} onChange={(evento) => setCategoriaId(evento.target.value)} required>
          <option value="">Elegí una categoría</option>
          {categorias.map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre}
            </option>
          ))}
        </select>
        {categoria && (
          <small>
            {categoria.descripcion}. Plazo de atención: {categoria.slaHoras} horas.
          </small>
        )}
      </label>

      <label>
        <span>Barrio</span>
        <select
          value={barrioId}
          onChange={(evento) => {
            setBarrioId(evento.target.value)
            olvidarBusqueda()
          }}
          required
        >
          <option value="">Elegí un barrio</option>
          {barrios.map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre}
            </option>
          ))}
        </select>
      </label>

      <div className="campo-direccion">
        <label>
          <span>Dirección</span>
          <input
            type="text"
            value={direccion}
            onChange={(evento) => {
              setDireccion(evento.target.value)
              olvidarBusqueda()
            }}
            maxLength={MAXIMO_DIRECCION}
            placeholder="Calle y altura, o esquina"
            required
          />
        </label>
        <button type="button" className="boton-secundario" onClick={buscar} disabled={!puedeBuscar}>
          {buscando ? 'Buscando…' : 'Buscar en el mapa'}
        </button>
      </div>
      {!busqueda && (
        <small className="ayuda-campo">
          Opcional: buscá la dirección para que el reclamo quede marcado en el mapa. Elegí antes el barrio.
        </small>
      )}
      <ResultadoBusqueda
        busqueda={busqueda}
        municipio={barrio?.municipio}
        elegida={elegida}
        onElegir={setElegida}
      />

      <label>
        <span>Descripción del problema</span>
        <textarea
          value={descripcion}
          onChange={(evento) => setDescripcion(evento.target.value)}
          maxLength={MAXIMO_DESCRIPCION}
          rows={5}
          required
        />
        <small>
          {descripcion.length} de {MAXIMO_DESCRIPCION} caracteres
        </small>
      </label>

      <div className="campo-foto">
        <label>
          <span>Foto del problema</span>
          {/* Sin "required": la falta de foto se avisa al enviar, con un mensaje propio. */}
          <input type="file" accept={TIPOS_FOTO.join(',')} onChange={elegirFoto} aria-required="true" />
        </label>
        <small className="ayuda-campo">
          JPEG o PNG, hasta 5 MB. No guardamos la ubicación ni los demás datos que trae la foto. Si podés, que no
          se vean caras ni patentes.
        </small>
        {errorFoto && (
          <p className="error-campo" role="alert">
            {errorFoto}
          </p>
        )}
        {vistaPrevia && <img className="vista-previa" src={vistaPrevia} alt="Vista previa de la foto elegida" />}
      </div>

      <div className="acciones">
        <button type="submit" className="boton-primario" disabled={enviando}>
          {enviando ? 'Creando…' : 'Crear reclamo'}
        </button>
      </div>
    </form>
  )
}

/** URL local para mostrar la foto antes de subirla; se libera al cambiarla o al salir. */
function useVistaPrevia(archivo) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    if (!archivo || typeof URL.createObjectURL !== 'function') {
      setUrl(null)
      return undefined
    }
    const nueva = URL.createObjectURL(archivo)
    setUrl(nueva)
    return () => URL.revokeObjectURL(nueva)
  }, [archivo])
  return url
}

/** Las sugerencias del mapa, o por qué no hay. */
function ResultadoBusqueda({ busqueda, municipio, elegida, onElegir }) {
  if (!busqueda) {
    return null
  }
  if (busqueda.error) {
    return <AvisoError error={busqueda.error} />
  }
  if (!busqueda.disponible) {
    return (
      <p className="aviso-ubicacion" role="status">
        El mapa no responde en este momento. Podés crear el reclamo igual: la ubicación queda pendiente.
      </p>
    )
  }
  if (busqueda.sugerencias.length === 0) {
    return (
      <p className="aviso-ubicacion" role="status">
        No encontramos esa dirección{municipio ? ` en ${municipio}` : ''}. Revisá la calle y la altura, o creá el
        reclamo sin marcarlo en el mapa.
      </p>
    )
  }
  return (
    <fieldset className="sugerencias">
      <legend>¿Cuál es la ubicación?</legend>
      <ul>
        {busqueda.sugerencias.map((sugerencia, indice) => (
          <li key={sugerencia.direccionNormalizada}>
            <label>
              <input
                type="radio"
                name="ubicacion"
                value={indice}
                checked={elegida === String(indice)}
                onChange={(evento) => onElegir(evento.target.value)}
              />
              <span>{sugerencia.direccionNormalizada}</span>
            </label>
            <a
              href={enlaceMapa(sugerencia.latitud, sugerencia.longitud)}
              target="_blank"
              rel="noopener noreferrer"
            >
              Ver en el mapa
            </a>
          </li>
        ))}
        <li>
          <label>
            <input
              type="radio"
              name="ubicacion"
              value={NINGUNA}
              checked={elegida === NINGUNA}
              onChange={() => onElegir(NINGUNA)}
            />
            <span>Ninguna de estas: crear sin marcarlo en el mapa</span>
          </label>
        </li>
      </ul>
    </fieldset>
  )
}
