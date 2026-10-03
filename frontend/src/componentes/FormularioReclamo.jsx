import { useState } from 'react'
import { crearReclamo } from '../api/reclamos.js'
import { AvisoError } from './Aviso.jsx'

const MAXIMO_DESCRIPCION = 1000
const MAXIMO_DIRECCION = 200

/** Alta de un reclamo. El área no se elige: la asigna el backend con la estrategia activa. */
export default function FormularioReclamo({ usuario, categorias, barrios, onCreado }) {
  const [categoriaId, setCategoriaId] = useState('')
  const [barrioId, setBarrioId] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [direccion, setDireccion] = useState('')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  const categoria = categorias.find((candidata) => String(candidata.id) === categoriaId)

  async function enviar(evento) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const reclamo = await crearReclamo(usuario.id, {
        categoriaId: Number(categoriaId),
        barrioId: Number(barrioId),
        descripcion: descripcion.trim(),
        direccion: direccion.trim(),
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
        <select value={barrioId} onChange={(evento) => setBarrioId(evento.target.value)} required>
          <option value="">Elegí un barrio</option>
          {barrios.map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre}
            </option>
          ))}
        </select>
      </label>

      <label>
        <span>Dirección</span>
        <input
          type="text"
          value={direccion}
          onChange={(evento) => setDireccion(evento.target.value)}
          maxLength={MAXIMO_DIRECCION}
          placeholder="Calle y altura, o esquina"
          required
        />
      </label>

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

      <div className="acciones">
        <button type="submit" className="boton-primario" disabled={enviando}>
          {enviando ? 'Creando…' : 'Crear reclamo'}
        </button>
      </div>
    </form>
  )
}
