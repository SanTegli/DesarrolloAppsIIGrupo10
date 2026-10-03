import ListaReclamos from './ListaReclamos.jsx'

/** Vista del administrador: reclamos que esperan asignación manual y cobertura de cada área. */
export default function PanelAdministracion({ usuario, areas, onAbrir }) {
  return (
    <div className="administracion">
      <h2>Reclamos sin asignar</h2>
      <p className="ayuda">
        Ningún área activa atiende su categoría en ese barrio. Abrí cada reclamo para asignarle un área o
        rechazarlo.
      </p>
      <ListaReclamos
        usuario={usuario}
        estadoFijo="INGRESADO"
        mensajeVacio="No hay reclamos esperando asignación."
        onAbrir={onAbrir}
      />

      <h2>Cobertura de las áreas</h2>
      <div className="tabla-contenedor">
        <table>
          <thead>
            <tr>
              <th>Área</th>
              <th>Categorías que atiende</th>
              <th>Barrios con jurisdicción</th>
              <th>Activa</th>
            </tr>
          </thead>
          <tbody>
            {areas.map((area) => (
              <tr key={area.id}>
                <td>{area.nombre}</td>
                <td>{area.categorias.map((categoria) => categoria.nombre).join(', ')}</td>
                <td>{area.barrios.map((barrio) => barrio.nombre).join(', ')}</td>
                <td>{area.activa ? 'Sí' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
