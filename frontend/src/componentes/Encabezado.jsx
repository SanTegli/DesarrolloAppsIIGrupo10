import { describirUsuario } from '../dominio/etiquetas.js'

/** Barra superior: la marca y, con sesión iniciada, quién está usando el sistema y el botón para salir. */
export default function Encabezado({ usuario, onSalir }) {
  return (
    <header className="encabezado">
      <div className="encabezado-contenido">
        <div className="marca">
          <svg className="marca-icono" viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M5 26V12L12 8V26M12 26V5H22V26M22 14H28V26M3 27H30M16 10H18M16 15H18M16 20H18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          <div>
            <strong>Reclamos Urbanos</strong>
            <span>Municipio de Quilmes</span>
          </div>
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
