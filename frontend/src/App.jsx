import { useCallback, useEffect, useState } from 'react'
import { listarAreas, listarBarrios, listarCategorias } from './api/catalogos.js'
import { alVencerSesion } from './api/cliente.js'
import { cerrarSesion, usuarioActual } from './api/sesion.js'
import { AvisoError } from './componentes/Aviso.jsx'
import BandejaAvisos from './componentes/BandejaAvisos.jsx'
import DetalleReclamo from './componentes/DetalleReclamo.jsx'
import Encabezado from './componentes/Encabezado.jsx'
import FormularioReclamo from './componentes/FormularioReclamo.jsx'
import Ingreso from './componentes/Ingreso.jsx'
import ListaReclamos from './componentes/ListaReclamos.jsx'
import PanelAdministracion from './componentes/PanelAdministracion.jsx'
import VerificacionTelefono from './componentes/VerificacionTelefono.jsx'
import { useCarga } from './hooks/useCarga.js'

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
  pestanas.push({ id: 'avisos', texto: 'Avisos' })
  return pestanas
}

const SESION_VENCIDA = 'Tu sesión venció o se cerró. Volvé a entrar para seguir.'

export default function App() {
  // estado: 'cargando' mientras se consulta /auth/yo, 'sin-sesion' para mostrar el login, 'activa' con usuario.
  const [sesion, setSesion] = useState({ estado: 'cargando' })
  const catalogos = useCarga(
    () =>
      Promise.all([listarCategorias(), listarBarrios(), listarAreas()]).then(([categorias, barrios, areas]) => ({
        categorias,
        barrios,
        areas,
      })),
    [],
  )
  const [vista, setVista] = useState({ nombre: 'reclamos' })

  useEffect(() => {
    let vigente = true
    usuarioActual().then(
      (actual) => vigente && setSesion(actual ? { estado: 'activa', usuario: actual } : { estado: 'sin-sesion' }),
      (error) => vigente && setSesion({ estado: 'error', error }),
    )
    return () => {
      vigente = false
    }
  }, [])

  // Si un pedido responde 401, la sesión ya no existe: se vuelve al login con un aviso.
  useEffect(
    () =>
      alVencerSesion(() => {
        setSesion({ estado: 'sin-sesion', aviso: SESION_VENCIDA })
        setVista({ nombre: 'reclamos' })
      }),
    [],
  )

  // Al verificar el teléfono cambia el usuario de la sesión, no la vista.
  const actualizarUsuario = useCallback((actualizado) => setSesion({ estado: 'activa', usuario: actualizado }), [])

  function entrar(usuarioNuevo) {
    setSesion({ estado: 'activa', usuario: usuarioNuevo })
    setVista({ nombre: 'reclamos' })
  }

  async function salir() {
    try {
      await cerrarSesion()
    } finally {
      setSesion({ estado: 'sin-sesion' })
      setVista({ nombre: 'reclamos' })
    }
  }

  const usuario = sesion.estado === 'activa' && catalogos.datos ? sesion.usuario : null
  const abrir = (numero, aviso) => setVista({ nombre: 'detalle', numero, aviso, origen: vista.nombre })
  const pestanas = usuario ? pestanasPara(usuario.rol) : []
  const pestanaActiva = vista.nombre === 'detalle' ? vista.origen : vista.nombre

  return (
    <>
      <Encabezado usuario={sesion.estado === 'activa' ? sesion.usuario : null} onSalir={salir} />

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
        <AvisoError error={catalogos.error ?? sesion.error} />
        {catalogos.error && (
          <button type="button" className="boton-secundario" onClick={catalogos.recargar}>
            Reintentar
          </button>
        )}
        {(catalogos.cargando || sesion.estado === 'cargando') && <p className="estado-carga">Cargando…</p>}

        {sesion.estado === 'sin-sesion' && <Ingreso aviso={sesion.aviso} onIngreso={entrar} />}

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

        {usuario && vista.nombre === 'crear' && usuario.rol === 'CIUDADANO' && usuario.bloqueado && (
          <div className="aviso aviso-error" role="alert">
            <p>
              Tu cuenta está bloqueada: podés consultar tus reclamos, pero no crear ni reabrir. Comunicate con el
              municipio.
            </p>
          </div>
        )}

        {usuario && vista.nombre === 'crear' && usuario.rol === 'CIUDADANO' && !usuario.bloqueado &&
          !usuario.telefonoVerificado && <VerificacionTelefono onVerificado={actualizarUsuario} />}

        {usuario && vista.nombre === 'crear' && usuario.rol === 'CIUDADANO' && !usuario.bloqueado &&
          usuario.telefonoVerificado && (
          <FormularioReclamo
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

        {usuario && vista.nombre === 'avisos' && (
          <BandejaAvisos key={usuario.id} usuario={usuario} onAbrir={abrir} />
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
