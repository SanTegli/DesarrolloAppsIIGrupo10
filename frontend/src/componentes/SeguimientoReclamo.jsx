import { obtenerSeguimiento } from '../api/reclamos.js'
import { ESTADOS, formatearFecha } from '../dominio/etiquetas.js'
import { useCarga } from '../hooks/useCarga.js'
import { AvisoError } from './Aviso.jsx'
import { InsigniaEstado } from './Insignias.jsx'
import { FotoReclamo } from './DetalleReclamo.jsx'

export default function SeguimientoReclamo({ numero, onVolver }) {
  const { datos, error, cargando } = useCarga(() => obtenerSeguimiento(numero), [numero])

  return (
    <section>
      <button type="button" className="enlace" onClick={onVolver}>Volver a avisos</button>
      <h2>Seguimiento del reclamo {numero}</h2>
      <AvisoError error={error} />
      {cargando && <p className="estado-carga">Cargando reclamo…</p>}
      {datos && (
        <>
          <p>Reclamo que apoyás · Solo lectura</p>
          <InsigniaEstado estado={datos.estado} />
          <p>{datos.categoria}</p>
          <p className="descripcion">{datos.descripcion}</p>
          <FotoReclamo foto={datos.foto} />
          <h3>Historial</h3>
          <ul>
            {datos.historial.map((cambio, indice) => (
              <li key={indice}>{formatearFecha(cambio.fecha)} · {ESTADOS[cambio.estadoNuevo]}</li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
