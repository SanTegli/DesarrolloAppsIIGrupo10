import { useState } from 'react'
import { tableroGestion, tableroPublico } from '../api/tablero.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError } from './Aviso.jsx'

/** "05/10/2026" a partir de "2026-10-05". */
export function formatearDia(iso) {
  if (!iso) {
    return ''
  }
  const [anio, mes, dia] = iso.split('-')
  return `${dia}/${mes}/${anio}`
}

/** Un porcentaje con un decimal, o "sin casos" cuando no hay sobre qué calcularlo. */
export function textoPorcentaje(valor) {
  return valor === null || valor === undefined ? 'sin casos' : `${valor.toLocaleString('es-AR')} %`
}

function Cifra({ titulo, valor, detalle }) {
  return (
    <div className="cifra">
      <span className="cifra-titulo">{titulo}</span>
      <strong className="cifra-valor">{valor}</strong>
      {detalle && <span className="cifra-detalle">{detalle}</span>}
    </div>
  )
}

/** Pestaña Tablero: para agentes y administradores, primero la gestión; para todos, el público. */
export default function Tablero({ usuario, barrios, categorias }) {
  return (
    <div className="tablero">
      {usuario && usuario.rol !== 'CIUDADANO' && <TableroGestion usuario={usuario} />}
      <TableroPublico barrios={barrios} categorias={categorias} />
    </div>
  )
}

/** Cifras públicas, sin datos de personas. No necesita sesión. */
export function TableroPublico({ barrios, categorias }) {
  const [filtros, setFiltros] = useState({ desde: '', hasta: '', barrioId: '', categoriaId: '' })
  const { datos, error, cargando } = useCarga(
    () => tableroPublico(filtros),
    [filtros.desde, filtros.hasta, filtros.barrioId, filtros.categoriaId],
  )
  const cambiar = (campo) => (evento) => setFiltros((anteriores) => ({ ...anteriores, [campo]: evento.target.value }))

  return (
    <section aria-labelledby="titulo-publico">
      <h2 id="titulo-publico">Tablero público</h2>
      <p className="ayuda">Cuántos reclamos hay y cómo se están atendiendo. Sin datos de las personas.</p>
      <div className="filtros-tablero">
        <label>
          <span>Desde</span>
          <input type="date" value={filtros.desde} onChange={cambiar('desde')} />
        </label>
        <label>
          <span>Hasta</span>
          <input type="date" value={filtros.hasta} onChange={cambiar('hasta')} />
        </label>
        <label>
          <span>Barrio</span>
          <select value={filtros.barrioId} onChange={cambiar('barrioId')}>
            <option value="">Todos</option>
            {barrios.map((barrio) => (
              <option key={barrio.id} value={barrio.id}>
                {barrio.nombre}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Categoría</span>
          <select value={filtros.categoriaId} onChange={cambiar('categoriaId')}>
            <option value="">Todas</option>
            {categorias.map((categoria) => (
              <option key={categoria.id} value={categoria.id}>
                {categoria.nombre}
              </option>
            ))}
          </select>
        </label>
      </div>
      <AvisoError error={error} />
      {cargando && !datos && <p className="estado-carga">Cargando cifras…</p>}
      {datos && (
        <>
          <div className="cifras">
            <Cifra
              titulo="Creados en el período"
              valor={datos.creadosEnPeriodo}
              detalle={`Del ${formatearDia(datos.desde)} al ${formatearDia(datos.hasta)}`}
            />
            <Cifra titulo="Pendientes hoy" valor={datos.pendientes} detalle="Ingresados, asignados o en proceso" />
            <Cifra titulo="Resueltos" valor={datos.resueltos} detalle="Esperan la confirmación del vecino" />
            <Cifra titulo="Cerrados" valor={datos.cerrados} />
            <Cifra
              titulo="Atendidos"
              valor={textoPorcentaje(datos.porcentajeAtendidos)}
              detalle="Resueltos y cerrados sobre el total, sin cancelados ni rechazados"
            />
          </div>
          <p className="secundario">
            Además, {datos.rechazados} rechazados y {datos.cancelados} cancelados por los vecinos.
          </p>
        </>
      )}
    </section>
  )
}

/** Para el municipio: pendientes y vencidos por área y barrio. El agente ve solo su área. */
export function TableroGestion({ usuario }) {
  const { datos, error, cargando } = useCarga(tableroGestion, [usuario.id])

  return (
    <section aria-labelledby="titulo-gestion">
      <h2 id="titulo-gestion">Tablero de gestión{datos?.area ? `: ${datos.area.nombre}` : ''}</h2>
      <AvisoError error={error} />
      {cargando && !datos && <p className="estado-carga">Cargando cifras…</p>}
      {datos && (
        <>
          <div className="cifras">
            <Cifra titulo="Pendientes" valor={datos.pendientes} />
            <Cifra
              titulo="Vencidos"
              valor={datos.vencidos}
              detalle={`${textoPorcentaje(datos.porcentajeVencidos)} de los pendientes`}
            />
            {datos.sinAsignar !== null && datos.sinAsignar !== undefined && (
              <Cifra titulo="Sin asignar" valor={datos.sinAsignar} detalle="Ningún área los cubre todavía" />
            )}
          </div>
          {datos.filas.length === 0 ? (
            <p className="vacio">No hay reclamos pendientes.</p>
          ) : (
            <div className="tabla-contenedor">
              <table>
                <thead>
                  <tr>
                    <th>Área</th>
                    <th>Barrio</th>
                    <th className="numero">Pendientes</th>
                    <th className="numero">Vencidos</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.filas.map((fila) => (
                    <tr key={`${fila.area?.id ?? 'sin-area'}-${fila.barrio.id}`}>
                      <td>{fila.area ? fila.area.nombre : 'Sin área'}</td>
                      <td>{fila.barrio.nombre}</td>
                      <td className="numero">{fila.pendientes}</td>
                      <td className={fila.vencidos > 0 ? 'numero vencidos' : 'numero'}>{fila.vencidos}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}
