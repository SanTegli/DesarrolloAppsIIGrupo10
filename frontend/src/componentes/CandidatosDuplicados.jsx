import { useState } from 'react'
import { apoyar, retirarApoyo } from '../api/reclamos.js'
import { ESTADOS, formatearFecha } from '../dominio/etiquetas.js'
import { AvisoError } from './Aviso.jsx'

/**
 * Antes de crear el reclamo: los reclamos abiertos a pocos metros. Desde el PR 27, la IA compara el texto del
 * reclamo nuevo con los cercanos de cualquier categoría; si encuentra el mismo problema, va primero y marcado.
 * El vecino se asocia ("Asociarme" = apoyar) en lugar de crear otro, y desde ahí recibe los avisos de ese
 * reclamo. Las tarjetas no muestran quién lo reclamó.
 */
export default function CandidatosDuplicados({ resultado, onApoyado, onContinuar, enviando }) {
  const [lista, setLista] = useState(resultado.candidatos)
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(null)
  const masParecido = lista.find((candidato) => candidato.masParecido)

  async function cambiarApoyo(candidato, quiereApoyar) {
    setError(null)
    setOcupado(candidato.numero)
    try {
      const estado = quiereApoyar ? await apoyar(candidato.numero) : await retirarApoyo(candidato.numero)
      setLista((anterior) =>
        anterior.map((item) =>
          item.numero === candidato.numero
            ? { ...item, apoyadoPorMi: estado.apoyado, cantidadApoyos: estado.cantidadApoyos }
            : item,
        ),
      )
      if (quiereApoyar) {
        onApoyado(candidato.numero)
      }
    } catch (fallo) {
      setError(fallo)
    } finally {
      setOcupado(null)
    }
  }

  return (
    <section className="duplicados" aria-labelledby="titulo-duplicados">
      <h3 id="titulo-duplicados">¿Es alguno de estos?</h3>
      <Explicacion resultado={resultado} masParecido={masParecido} cantidad={lista.length} />
      <AvisoError error={error} />
      <ul className="tarjetas-duplicados">
        {lista.map((candidato) => (
          <li key={candidato.numero} className={`tarjeta-duplicado${candidato.masParecido ? ' mas-parecido' : ''}`}>
            {candidato.foto ? (
              <img src={candidato.foto} alt={`Foto del reclamo ${candidato.numero}`} loading="lazy" />
            ) : (
              <div className="sin-foto">Sin foto</div>
            )}
            <div className="tarjeta-cuerpo">
              {candidato.masParecido && <span className="insignia-ia">El mismo problema, según la IA</span>}
              <p className="tarjeta-titulo">
                <strong>{candidato.numero}</strong> · {ESTADOS[candidato.estado]} · a {candidato.distanciaMetros} m
              </p>
              {candidato.categoria && <p className="secundario">{candidato.categoria.nombre}</p>}
              <p>{candidato.descripcion}</p>
              <p className="secundario">
                {candidato.direccion}. Creado el {formatearFecha(candidato.fechaCreacion)}.{' '}
                {textoApoyos(candidato.cantidadApoyos)}
              </p>
              {candidato.propio ? (
                <p className="secundario">Es un reclamo tuyo.</p>
              ) : candidato.apoyadoPorMi ? (
                <div className="acciones">
                  <span className="insignia-apoyo">Ya estás asociado</span>
                  <button
                    type="button"
                    className="enlace"
                    disabled={ocupado !== null}
                    onClick={() => cambiarApoyo(candidato, false)}
                  >
                    Quitar asociación
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className={candidato.masParecido || !masParecido ? 'boton-primario' : 'boton-secundario'}
                  disabled={ocupado !== null || enviando}
                  onClick={() => cambiarApoyo(candidato, true)}
                  aria-label={`Es el mismo: asociarme a ${candidato.numero}`}
                >
                  Es el mismo: asociarme
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
      <button type="button" className="boton-secundario" disabled={ocupado !== null || enviando} onClick={onContinuar}>
        {enviando ? 'Creando…' : 'Es otro problema: continuar'}
      </button>
    </section>
  )
}

/** Qué encontró la búsqueda: con la IA, el más parecido y por qué; sin ella, los de la misma categoría. */
function Explicacion({ resultado, masParecido, cantidad }) {
  const asociarse =
    'Si es el mismo problema, asociate: sumás tu apoyo, te avisamos cada vez que cambie y no se crea un reclamo repetido.'
  if (masParecido) {
    return (
      <>
        <p className="sugerencia-ia">
          La IA cree que el reclamo {masParecido.numero} es el mismo problema que el tuyo: {resultado.motivo}
        </p>
        <p className="ayuda">{asociarse}</p>
      </>
    )
  }
  const cuantos = cantidad === 1 ? 'un reclamo abierto' : `${cantidad} reclamos abiertos`
  return (
    <p className="ayuda">
      {resultado.comparadoPorIA
        ? `La IA no encontró el mismo problema, pero hay ${cuantos} de la misma categoría a pocos metros. `
        : `Ya hay ${cuantos} de la misma categoría a pocos metros. `}
      {asociarse}
    </p>
  )
}

export function textoApoyos(cantidad) {
  if (!cantidad) {
    return 'Nadie lo apoyó todavía.'
  }
  return cantidad === 1 ? '1 vecino lo apoya.' : `${cantidad} vecinos lo apoyan.`
}
