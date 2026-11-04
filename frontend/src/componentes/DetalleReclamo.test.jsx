import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AREAS, TOKEN_CSRF, USUARIOS, reclamo, simularApi } from '../pruebas/apiFalsa.js'
import DetalleReclamo from './DetalleReclamo.jsx'

const [ana, carla, elena] = USUARIOS
const RUTA = '/api/reclamos/REC-4F2A91BC'

function mostrar(usuario, rutas) {
  const llamadas = simularApi({ [`GET ${RUTA}/notificaciones`]: [], ...rutas })
  render(<DetalleReclamo usuario={usuario} numero="REC-4F2A91BC" areas={AREAS} onVolver={vi.fn()} />)
  return llamadas
}

describe('DetalleReclamo', () => {
  it('muestra los datos, el historial y los avisos del reclamo', async () => {
    mostrar(ana, {
      [`GET ${RUTA}`]: reclamo(),
      [`GET ${RUTA}/notificaciones`]: [
        { canal: 'INTERNO', destinatario: { id: 1, nombreCompleto: 'Ana Pérez' }, mensaje: 'Tu reclamo fue ingresado.', fechaEnvio: '2026-10-05T10:00:01' },
      ],
    })

    expect(await screen.findByRole('heading', { name: 'REC-4F2A91BC' })).toBeInTheDocument()
    expect(screen.getByText('Farol apagado frente a la plaza')).toBeInTheDocument()
    expect(screen.getByText('Belgrano 450, Bernal')).toBeInTheDocument()
    const historial = screen.getAllByRole('listitem').filter((item) => item.closest('.historial'))
    expect(historial).toHaveLength(2)
    expect(within(historial[1]).getByText('Sistema: Asignación automática')).toBeInTheDocument()
    expect(screen.getByText('Tu reclamo fue ingresado.')).toBeInTheDocument()
  })

  it('con la ubicación confirmada muestra la dirección normalizada y el enlace a Google Maps', async () => {
    mostrar(ana, {
      [`GET ${RUTA}`]: reclamo({
        ubicacion: {
          estado: 'CONFIRMADA',
          latitud: -34.73614578,
          longitud: -58.3228998575,
          direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires',
          proveedor: 'georef',
        },
      }),
    })

    const enlace = await screen.findByRole('link', { name: 'Ver en Google Maps' })
    expect(enlace).toHaveAttribute('href', 'https://www.google.com/maps/search/?api=1&query=-34.73614578%2C-58.3228998575')
    expect(enlace).toHaveAttribute('target', '_blank')
    expect(screen.getByText(/GRL BELGRANO 450, Bernal Oeste/)).toBeInTheDocument()
    expect(screen.getByText('Belgrano 450, Bernal')).toBeInTheDocument()
  })

  it('muestra la foto del reclamo con enlace para verla completa', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo() })

    const foto = await screen.findByRole('img', { name: 'Foto del problema' })
    expect(foto).toHaveAttribute('src', '/api/reclamos/REC-4F2A91BC/foto')
    expect(foto.closest('a')).toHaveAttribute('href', '/api/reclamos/REC-4F2A91BC/foto')
  })

  it('muestra cuántos vecinos apoyan el reclamo', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo({ cantidadApoyos: 3 }) })

    expect(await screen.findByText('3 vecinos apoyan este reclamo: les pasa lo mismo.')).toBeInTheDocument()
  })

  it('un reclamo anterior a la foto obligatoria lo dice', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo({ foto: null }) })

    expect(await screen.findByText('Este reclamo no tiene foto.')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('con la ubicación pendiente avisa que no está en el mapa', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo() })

    expect(await screen.findByText('Sin marcar en el mapa')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Ver en Google Maps' })).not.toBeInTheDocument()
  })

  it('muestra de dónde salió la prioridad y si la cobertura no respondió', async () => {
    mostrar(elena, {
      [`GET ${RUTA}`]: reclamo({
        estado: 'INGRESADO',
        area: null,
        prioridad: 'ALTA',
        origenPrioridad: 'PALABRAS_CLAVE',
        motivoPrioridad: 'La descripción menciona "peligro".',
        asignacionPendiente: true,
      }),
    })

    expect(await screen.findByText('Alta, por palabras clave. La descripción menciona "peligro".')).toBeInTheDocument()
    expect(screen.getByText('Sin asignar: el servicio de cobertura no respondió')).toBeInTheDocument()
  })

  it('no ofrece acciones cuando el backend no devuelve ninguna', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo({ estado: 'EN_PROCESO', accionesDisponibles: [] }) })

    await screen.findByRole('heading', { name: 'REC-4F2A91BC' })
    expect(screen.queryByRole('heading', { name: 'Acciones' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tomar|Cancelar|Rechazar|Asignar/ })).not.toBeInTheDocument()
  })

  it('muestra un botón por cada acción disponible con el nombre que entiende el usuario', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo({ estado: 'RESUELTO', accionesDisponibles: ['CERRADO', 'EN_PROCESO'] }) })

    expect(await screen.findByRole('button', { name: 'Confirmar solución' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reabrir' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tomar reclamo' })).not.toBeInTheDocument()
  })

  it('para reabrir pide el motivo: sin él el botón queda deshabilitado, y con él lo manda', async () => {
    let estado = reclamo({ estado: 'RESUELTO', accionesDisponibles: ['CERRADO', 'EN_PROCESO'] })
    const llamadas = mostrar(ana, {
      [`GET ${RUTA}`]: () => estado,
      [`PATCH ${RUTA}/estado`]: () => {
        estado = reclamo({ estado: 'EN_PROCESO', accionesDisponibles: [] })
        return estado
      },
    })
    const usuario = userEvent.setup()

    const reabrir = await screen.findByRole('button', { name: 'Reabrir' })
    expect(reabrir).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Confirmar solución' })).toBeEnabled()
    expect(screen.getByText(/una sola vez y dentro de los 7 días/)).toBeInTheDocument()
    await usuario.type(screen.getByLabelText(/para reabrir, contá por qué/), 'Sigue apagado')
    await usuario.click(reabrir)

    expect(await screen.findByText('El reclamo pasó a En proceso.')).toBeInTheDocument()
    const cambio = llamadas.find((llamada) => llamada.metodo === 'PATCH')
    expect(cambio.cuerpo).toEqual({ estado: 'EN_PROCESO', observacion: 'Sigue apagado' })
  })

  it('si ya no se puede reabrir, el backend no lo ofrece y solo queda confirmar', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: reclamo({ estado: 'RESUELTO', accionesDisponibles: ['CERRADO'] }) })

    expect(await screen.findByRole('button', { name: 'Confirmar solución' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reabrir' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Observación (opcional)')).toBeInTheDocument()
  })

  it('el agente toma el reclamo: envía el PATCH con la observación y avisa el resultado', async () => {
    let estado = reclamo({ accionesDisponibles: ['EN_PROCESO'] })
    const llamadas = mostrar(carla, {
      [`GET ${RUTA}`]: () => estado,
      [`PATCH ${RUTA}/estado`]: () => {
        estado = reclamo({ estado: 'EN_PROCESO', agente: { id: 3, nombreCompleto: 'Carla Gómez' }, accionesDisponibles: ['RESUELTO'] })
        return estado
      },
    })
    const usuario = userEvent.setup()

    await usuario.type(await screen.findByLabelText('Observación (opcional)'), 'Cuadrilla en camino')
    await usuario.click(screen.getByRole('button', { name: 'Tomar reclamo' }))

    expect(await screen.findByText('El reclamo pasó a En proceso.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Marcar como resuelto' })).toBeInTheDocument()
    const cambio = llamadas.find((llamada) => llamada.metodo === 'PATCH')
    expect(cambio).toMatchObject({ csrf: TOKEN_CSRF, cuerpo: { estado: 'EN_PROCESO', observacion: 'Cuadrilla en camino' } })
  })

  it('muestra el mensaje del backend cuando la acción es rechazada y no cambia el estado', async () => {
    mostrar(carla, {
      [`GET ${RUTA}`]: reclamo({ accionesDisponibles: ['EN_PROCESO'] }),
      [`PATCH ${RUTA}/estado`]: {
        status: 409,
        cuerpo: { status: 409, codigo: 'REGLA_NEGOCIO', mensaje: 'Un reclamo en estado CANCELADO no puede pasar a EN_PROCESO.', detalles: [] },
      },
    })
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Tomar reclamo' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Un reclamo en estado CANCELADO no puede pasar a EN_PROCESO.')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('el administrador asigna un área a un reclamo sin asignar', async () => {
    let estado = reclamo({ estado: 'INGRESADO', area: null, accionesDisponibles: ['ASIGNADO', 'RECHAZADO'] })
    const llamadas = mostrar(elena, {
      [`GET ${RUTA}`]: () => estado,
      [`PATCH ${RUTA}/asignacion`]: () => {
        estado = reclamo({ area: { id: 2, nombre: 'Obras Públicas' }, accionesDisponibles: ['ASIGNADO'] })
        return estado
      },
    })
    const usuario = userEvent.setup()

    const selector = await screen.findByLabelText('Asignar área')
    expect(screen.getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    expect(within(selector).queryByRole('option', { name: 'Higiene Urbana' })).not.toBeInTheDocument() // inactiva
    await usuario.selectOptions(selector, 'Obras Públicas')
    await usuario.click(screen.getByRole('button', { name: 'Asignar' }))

    expect(await screen.findByText('El reclamo quedó asignado a Obras Públicas.')).toBeInTheDocument()
    const asignacion = llamadas.find((llamada) => llamada.ruta.endsWith('/asignacion'))
    expect(asignacion).toMatchObject({ metodo: 'PATCH', csrf: TOKEN_CSRF, cuerpo: { areaId: 2, observacion: null } })
  })

  it('al reasignar no ofrece el área actual', async () => {
    mostrar(elena, { [`GET ${RUTA}`]: reclamo({ accionesDisponibles: ['ASIGNADO'] }) })

    const selector = await screen.findByLabelText('Reasignar área')
    expect(within(selector).queryByRole('option', { name: 'Alumbrado' })).not.toBeInTheDocument()
    expect(within(selector).getByRole('option', { name: 'Obras Públicas' })).toBeInTheDocument()
  })

  it('muestra el error cuando el usuario no puede consultar el reclamo', async () => {
    mostrar(carla, {
      [`GET ${RUTA}`]: { status: 403, cuerpo: { status: 403, codigo: 'ACCESO_DENEGADO', mensaje: 'El usuario no puede consultar este reclamo.', detalles: [] } },
      [`GET ${RUTA}/notificaciones`]: { status: 403, cuerpo: { status: 403, codigo: 'ACCESO_DENEGADO', mensaje: 'El usuario no puede consultar este reclamo.', detalles: [] } },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('El usuario no puede consultar este reclamo.')
  })
})
