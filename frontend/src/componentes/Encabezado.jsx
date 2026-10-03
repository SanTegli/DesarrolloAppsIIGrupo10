import { ROLES, describirUsuario } from '../dominio/etiquetas.js'

const GRUPOS = ['CIUDADANO', 'AGENTE_MUNICIPAL', 'ADMINISTRADOR']

/** Barra superior con el selector de usuario. Reemplaza al login: define el X-Usuario-Id. */
export default function Encabezado({ usuarios, usuario, onCambiarUsuario }) {
  return (
    <header className="encabezado">
      <div className="encabezado-contenido">
        <div className="marca">
          <strong>Reclamos Urbanos</strong>
          <span>Municipio de Quilmes</span>
        </div>
        <label className="selector-usuario">
          <span>Usar el sistema como</span>
          <select
            value={usuario?.id ?? ''}
            onChange={(evento) => onCambiarUsuario(Number(evento.target.value))}
            disabled={usuarios.length === 0}
          >
            {GRUPOS.map((rol) => (
              <optgroup key={rol} label={ROLES[rol]}>
                {usuarios
                  .filter((candidato) => candidato.rol === rol)
                  .map((candidato) => (
                    <option key={candidato.id} value={candidato.id}>
                      {describirUsuario(candidato)}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
        </label>
      </div>
    </header>
  )
}
