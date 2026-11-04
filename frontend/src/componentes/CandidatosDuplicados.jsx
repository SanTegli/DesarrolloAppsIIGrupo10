import { useState } from 'react'
import { apoyar, retirarApoyo } from '../api/reclamos.js'
import { ESTADOS, formatearFecha } from '../dominio/etiquetas.js'
import { AvisoError } from './Aviso.jsx'

/**
 * Antes de crear el reclamo: los reclamos abiertos de la misma categoría a pocos metros. Si alguno es el mismo
 * problema, el vecino lo apoya en lugar de crear otro. Las tarjetas no muestran quién lo reclamó.
 */
export default function CandidatosDuplicados({ candidatos, onApoyado, onContinuar, enviando }) {
  const [lista, setLista] = useState(candidatos)
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(null)

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
      <p className="ayuda">
        Ya hay {lista.length === 1 ? 'un reclamo abierto' : `${lista.length} reclamos abiertos`} de la misma
        categoría a pocos metros. Si es el mismo problema, apoyalo: suma a ese reclamo en lugar de crear otro.
      </p>
      <AvisoError error={error} />
      <ul className="tarjetas-duplicados">
        {lista.map((candidato) => (
          <li key={candidato.numero} className="tarjeta-duplicado">
            {candidato.foto ? (
              <img src={candidato.foto} alt={`Foto del reclamo ${candidato.numero}`} loading="lazy" />
            ) : (
              <div className="sin-foto">Sin foto</div>
            )}
            <div className="tarjeta-cuerpo">
              <p className="tarjeta-titulo">
                <strong>{candidato.numero}</strong> · {ESTADOS[candidato.estado]} · a {candidato.distanciaMetros} m
              </p>
              <p>{candidato.descripcion}</p>
              <p className="secundario">
                {candidato.direccion}. Creado el {formatearFecha(candidato.fechaCreacion)}.{' '}
                {textoApoyos(candidato.cantidadApoyos)}
              </p>
              {candidato.propio ? (
                <p className="secundario">Es un reclamo tuyo.</p>
              ) : candidato.apoyadoPorMi ? (
                <div className="acciones">
                  <span className="insignia-apoyo">Ya lo apoyás</span>
                  <button
                    type="button"
                    className="enlace"
                    disabled={ocupado !== null}
                    onClick={() => cambiarApoyo(candidato, false)}
                  >
                    Quitar apoyo
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="boton-primario"
                  disabled={ocupado !== null || enviando}
                  onClick={() => cambiarApoyo(candidato, true)}
                  aria-label={`Es el mismo: apoyar ${candidato.numero}`}
                >
                  Es el mismo: apoyar
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

export function textoApoyos(cantidad) {
  if (!cantidad) {
    return 'Nadie lo apoyó todavía.'
  }
  return cantidad === 1 ? '1 vecino lo apoya.' : `${cantidad} vecinos lo apoyan.`
}
