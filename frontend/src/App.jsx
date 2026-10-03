import { useEffect, useState } from 'react'
import { listarAreas, listarBarrios, listarCategorias, listarUsuarios } from './api/catalogos.js'
import { AvisoError } from './componentes/Aviso.jsx'
import DetalleReclamo from './componentes/DetalleReclamo.jsx'
import Encabezado from './componentes/Encabezado.jsx'
import FormularioReclamo from './componentes/FormularioReclamo.jsx'
import ListaReclamos from './componentes/ListaReclamos.jsx'
import PanelAdministracion from './componentes/PanelAdministracion.jsx'
import { useCarga } from './hooks/useCarga.js'

const CLAVE_USUARIO = 'reclamos.usuarioId'

function leerUsuarioGuardado() {
  try {
    return Number(localStorage.getItem(CLAVE_USUARIO)) || null
  } catch {
    return null
  }
}

function guardarUsuario(id) {
  try {
    localStorage.setItem(CLAVE_USUARIO, String(id))
  } catch {
    // Sin almacenamiento disponible la elección dura lo que dure la pestaña.
  }
}

const TITULOS_LISTADO = {
  CIUDADANO: 'Mis reclamos',
  AGENTE_MUNICIPAL: 'Reclamos de mi área',
  ADMINISTRADOR: 'Todos los reclamos',
}

const VACIOS_LISTADO = {
  CIUDADANO: 'Todavía no creaste ningún reclamo.',
  AGENTE_MUNICIPAL: 'Tu área no tiene reclamos asignados.',
}

function pestanasPara(rol) {
  const pestanas = [{ id: 'reclamos', texto: 'Reclamos' }]
  if (rol === 'CIUDADANO') {
    pestanas.push({ id: 'crear', texto: 'Crear reclamo' })
  }
  if (rol === 'ADMINISTRADOR') {
    pestanas.push({ id: 'administracion', texto: 'Administración' })
  }
  return pestanas
}

export default function App() {
  const catalogos = useCarga(
    () =>
      Promise.all([listarUsuarios(), listarCategorias(), listarBarrios(), listarAreas()]).then(
        ([usuarios, categorias, barrios, areas]) => ({ usuarios, categorias, barrios, areas }),
      ),
    [],
  )
  const [usuarioId, setUsuarioId] = useState(leerUsuarioGuardado)
  const [vista, setVista] = useState({ nombre: 'reclamos' })

  const usuarios = catalogos.datos?.usuarios ?? []
  const usuario = usuarios.find((candidato) => candidato.id === usuarioId) ?? usuarios[0] ?? null

  useEffect(() => {
    if (usuario) {
      guardarUsuario(usuario.id)
    }
  }, [usuario])

  function cambiarUsuario(id) {
    setUsuarioId(id)
    setVista({ nombre: 'reclamos' })
  }

  const abrir = (numero, aviso) => setVista({ nombre: 'detalle', numero, aviso, origen: vista.nombre })
  const pestanas = usuario ? pestanasPara(usuario.rol) : []
  const pestanaActiva = vista.nombre === 'detalle' ? vista.origen : vista.nombre

  return (
    <>
      <Encabezado usuarios={usuarios} usuario={usuario} onCambiarUsuario={cambiarUsuario} />

      {usuario && (
        <nav className="navegacion" aria-label="Secciones">
          {pestanas.map((pestana) => (
            <button
              key={pestana.id}
              type="button"
              className={pestana.id === pestanaActiva ? 'activa' : ''}
              aria-current={pestana.id === pestanaActiva ? 'page' : undefined}
              onClick={() => setVista({ nombre: pestana.id })}
            >
              {pestana.texto}
            </button>
          ))}
        </nav>
      )}

      <main>
        <AvisoError error={catalogos.error} />
        {catalogos.error && (
          <button type="button" className="boton-secundario" onClick={catalogos.recargar}>
            Reintentar
          </button>
        )}
        {catalogos.cargando && <p className="estado-carga">Cargando…</p>}

        {usuario && vista.nombre === 'reclamos' && (
          <>
            <h2>{TITULOS_LISTADO[usuario.rol]}</h2>
            <ListaReclamos
              key={usuario.id}
              usuario={usuario}
              areas={catalogos.datos.areas}
              mensajeVacio={VACIOS_LISTADO[usuario.rol]}
              onAbrir={abrir}
            />
          </>
        )}

        {usuario && vista.nombre === 'crear' && usuario.rol === 'CIUDADANO' && (
          <FormularioReclamo
            usuario={usuario}
            categorias={catalogos.datos.categorias}
            barrios={catalogos.datos.barrios}
            onCreado={(reclamo) =>
              abrir(
                reclamo.numero,
                reclamo.area
                  ? `Reclamo creado y asignado a ${reclamo.area.nombre}.`
                  : 'Reclamo creado. Ningún área cubre esa categoría en ese barrio: un administrador lo va a asignar.',
              )
            }
          />
        )}

        {usuario && vista.nombre === 'administracion' && usuario.rol === 'ADMINISTRADOR' && (
          <PanelAdministracion usuario={usuario} areas={catalogos.datos.areas} onAbrir={abrir} />
        )}

        {usuario && vista.nombre === 'detalle' && (
          <DetalleReclamo
            key={`${usuario.id}-${vista.numero}`}
            usuario={usuario}
            numero={vista.numero}
            areas={catalogos.datos.areas}
            avisoInicial={vista.aviso}
            onVolver={() => setVista({ nombre: vista.origen === 'crear' ? 'reclamos' : vista.origen })}
          />
        )}
      </main>
    </>
  )
}
