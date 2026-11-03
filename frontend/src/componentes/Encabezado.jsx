import { describirUsuario } from '../dominio/etiquetas.js'

/** Barra superior: la marca y, con sesión iniciada, quién está usando el sistema y el botón para salir. */
export default function Encabezado({ usuario, onSalir }) {
  return (
    <header className="encabezado">
      <div className="encabezado-contenido">
        <div className="marca">
          <strong>Reclamos Urbanos</strong>
          <span>Municipio de Quilmes</span>
        </div>
        {usuario && (
          <div className="sesion-usuario">
            <span>{describirUsuario(usuario)}</span>
            <button type="button" className="boton-encabezado" onClick={onSalir}>
              Salir
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
