import { useEffect, useState } from 'react'
import { buscarDuplicados, crearReclamo } from '../api/reclamos.js'
import { sugerirUbicaciones } from '../api/ubicaciones.js'
import { enlaceMapa, ordenarCategorias } from '../dominio/etiquetas.js'
import { AvisoError } from './Aviso.jsx'
import CandidatosDuplicados from './CandidatosDuplicados.jsx'

const MAXIMO_DESCRIPCION = 1000
const MAXIMO_DIRECCION = 200
const MAXIMO_PROPUESTA = 80
const MINIMO_BUSQUEDA = 3
const NINGUNA = ''
// Igual que el backend: JPEG o PNG, hasta 5 MB. El backend vuelve a validar el contenido real del archivo.
export const TIPOS_FOTO = ['image/jpeg', 'image/png']
export const MAXIMO_FOTO = 5 * 1024 * 1024

/**
 * Alta de un reclamo. El área no se elige: la asigna el backend con la estrategia activa.
 * La dirección se busca en el mapa en todos los municipios: si hay varias interpretaciones, el ciudadano elige
 * una, y con eso queda elegido el municipio del reclamo. Es opcional: sin elegir (o si el mapa no responde), el
 * ciudadano elige el municipio de la lista, el reclamo se crea igual y la ubicación queda pendiente.
 * La foto es obligatoria: es la prueba del problema.
 * Si ninguna categoría sirve, el vecino elige "Otro problema" y escribe qué tipo de problema es; la IA sugiere
 * la categoría y un administrador lo recategoriza.
 * Si el ciudadano marcó el punto en el mapa, antes de crear se buscan reclamos abiertos parecidos a pocos
 * metros: si es el mismo problema, lo apoya en lugar de crear otro.
 */
export default function FormularioReclamo({ categorias, municipios, onCreado, onApoyado = () => {} }) {
  const [categoriaId, setCategoriaId] = useState('')
  const [propuesta, setPropuesta] = useState('')
  const [municipioManual, setMunicipioManual] = useState('')
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
  // Posibles duplicados para una categoría y un punto; "revisada" es la combinación que el vecino ya descartó.
  const [duplicados, setDuplicados] = useState(null)
  const [revisada, setRevisada] = useState(null)

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
  const puedeBuscar = direccion.trim().length >= MINIMO_BUSQUEDA && !buscando

  // Una búsqueda vale para la dirección con que se hizo.
  function olvidarBusqueda() {
    setBusqueda(null)
    setElegida(NINGUNA)
  }

  async function buscar() {
    setBuscando(true)
    olvidarBusqueda()
    try {
      setBusqueda(await sugerirUbicaciones(direccion.trim()))
    } catch (fallo) {
      setBusqueda({ error: fallo })
    } finally {
      setBuscando(false)
    }
  }

  const sugerencia = elegida === NINGUNA ? null : busqueda?.sugerencias?.[Number(elegida)]
  // La sugerencia elegida trae su municipio; sin sugerencia, el que el vecino elige de la lista.
  const municipioId = sugerencia ? sugerencia.municipio.id : Number(municipioManual)
  const clave = sugerencia ? `${categoriaId}|${sugerencia.latitud}|${sugerencia.longitud}` : null
  const candidatosVisibles = duplicados && duplicados.clave === clave ? duplicados.candidatos : null

  async function enviar(evento) {
    evento.preventDefault()
    if (!foto) {
      setErrorFoto('Elegí una foto del problema: es obligatoria.')
      return
    }
    setError(null)
    setEnviando(true)
    if (clave && clave !== revisada) {
      const candidatos = await candidatosPara(sugerencia)
      if (candidatos.length > 0) {
        setDuplicados({ clave, candidatos })
        setEnviando(false)
        return
      }
    }
    await crear()
  }

  // Si la búsqueda de duplicados falla, no frena el alta: es una ayuda, no un requisito.
  async function candidatosPara(punto) {
    try {
      return await buscarDuplicados({
        categoriaId: Number(categoriaId),
        latitud: punto.latitud,
        longitud: punto.longitud,
      })
    } catch {
      return []
    }
  }

  async function continuar() {
    setRevisada(clave)
    setDuplicados(null)
    setError(null)
    setEnviando(true)
    await crear()
  }

  async function crear() {
    try {
      const reclamo = await crearReclamo({
        categoriaId: Number(categoriaId),
        municipioId,
        descripcion: descripcion.trim(),
        direccion: direccion.trim(),
        foto,
        categoriaPropuesta: categoria?.otra ? propuesta.trim() : undefined,
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
          {ordenarCategorias(categorias).map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre}
            </option>
          ))}
        </select>
        {categoria && !categoria.otra && (
          <small>
            {categoria.descripcion}. Plazo de atención: {categoria.slaHoras} horas.
          </small>
        )}
      </label>

      {categoria?.otra && (
        <div className="campo-propuesta">
          <label>
            <span>¿Qué tipo de problema es?</span>
            <input
              type="text"
              value={propuesta}
              onChange={(evento) => setPropuesta(evento.target.value)}
              maxLength={MAXIMO_PROPUESTA}
              placeholder="Por ejemplo: baldosas levantadas"
              required
            />
          </label>
          <small className="ayuda-campo">
            En pocas palabras. Con esto, el municipio le asigna la categoría que corresponde y el área que lo atiende.
          </small>
        </div>
      )}

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
          Buscá la dirección para que el reclamo quede marcado en el mapa: la buscamos en todos los municipios.
        </small>
      )}
      <ResultadoBusqueda busqueda={busqueda} elegida={elegida} onElegir={setElegida} />

      {sugerencia ? (
        <p className="municipio-elegido">
          Municipio: <strong>{sugerencia.municipio.nombre}</strong>, según la dirección elegida.
        </p>
      ) : (
        <div className="campo-municipio">
          <label>
            <span>Municipio</span>
            <select value={municipioManual} onChange={(evento) => setMunicipioManual(evento.target.value)} required>
              <option value="">Elegí el municipio</option>
              {municipios.map((opcion) => (
                <option key={opcion.id} value={opcion.id}>
                  {opcion.nombre}
                </option>
              ))}
            </select>
          </label>
          <small className="ayuda-campo">Si elegís la dirección en el mapa, el municipio sale de ahí.</small>
        </div>
      )}

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

      {candidatosVisibles && (
        <CandidatosDuplicados
          key={clave}
          candidatos={candidatosVisibles}
          onApoyado={onApoyado}
          onContinuar={continuar}
          enviando={enviando}
        />
      )}

      <div className="acciones">
        <button type="submit" className="boton-primario" disabled={enviando || candidatosVisibles !== null}>
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
function ResultadoBusqueda({ busqueda, elegida, onElegir }) {
  if (!busqueda) {
    return null
  }
  if (busqueda.error) {
    return <AvisoError error={busqueda.error} />
  }
  if (!busqueda.disponible) {
    return (
      <p className="aviso-ubicacion" role="status">
        El mapa no responde en este momento. Elegí el municipio de la lista y creá el reclamo igual: la ubicación
        queda pendiente.
      </p>
    )
  }
  if (busqueda.sugerencias.length === 0) {
    return (
      <p className="aviso-ubicacion" role="status">
        No encontramos esa dirección en ningún municipio. Revisá la calle y la altura, o elegí el municipio de la
        lista y creá el reclamo sin marcarlo en el mapa.
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
              <span>
                {sugerencia.direccionNormalizada}
                <small className="municipio-sugerencia"> · {sugerencia.municipio.nombre}</small>
              </span>
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
