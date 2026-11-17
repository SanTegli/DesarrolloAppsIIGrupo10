import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AREAS, CATEGORIAS, TOKEN_CSRF, USUARIOS, reclamo, simularApi } from '../pruebas/apiFalsa.js'
import DetalleReclamo from './DetalleReclamo.jsx'

const [ana, carla, elena] = USUARIOS
const RUTA = '/api/reclamos/REC-4F2A91BC'

function mostrar(usuario, rutas) {
  const llamadas = simularApi({ [`GET ${RUTA}/notificaciones`]: [], ...rutas })
  render(
    <DetalleReclamo usuario={usuario} numero="REC-4F2A91BC" areas={AREAS} categorias={CATEGORIAS} onVolver={vi.fn()} />,
  )
  return llamadas
}

/** Un reclamo de "Otro problema" recién ingresado, con lo que escribió Ana. */
function deOtroProblema(cambios = {}) {
  return reclamo({
    estado: 'INGRESADO',
    area: null,
    categoria: { id: 8, nombre: 'Otro problema' },
    categoriaPropuesta: 'Baldosas levantadas',
    accionesDisponibles: ['ASIGNADO', 'RECHAZADO'],
    ...cambios,
  })
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
    expect(screen.getByText('Belgrano 450, Quilmes')).toBeInTheDocument()
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
    expect(screen.getByText('Belgrano 450, Quilmes')).toBeInTheDocument()
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

  it('mientras la IA revisa la prioridad, consulta de nuevo y se actualiza cuando terminó', async () => {
    const pendiente = reclamo({
      prioridad: 'MEDIA',
      origenPrioridad: 'PENDIENTE_IA',
      motivoPrioridad: 'La IA no respondió a tiempo.',
    })
    const revisado = reclamo({ prioridad: 'CRITICA', origenPrioridad: 'IA', motivoPrioridad: 'Cables expuestos.' })
    const respuestas = [pendiente, pendiente, revisado]
    const llamadas = simularApi({
      [`GET ${RUTA}`]: () => respuestas.shift() ?? revisado,
      [`GET ${RUTA}/notificaciones`]: [],
    })
    render(
      <DetalleReclamo usuario={ana} numero="REC-4F2A91BC" areas={AREAS} onVolver={vi.fn()} revisionIA={{ cada: 10, intentos: 5 }} />,
    )

    expect(
      await screen.findByText('Media, por categoría (la IA lo está revisando). La IA no respondió a tiempo.'),
    ).toBeInTheDocument()
    expect(await screen.findByText('Crítica, sugerida por IA. Cables expuestos.')).toBeInTheDocument()
    // Carga inicial, dos consultas de la espera (la segunda ve el cambio) y la recarga completa.
    expect(llamadas.filter((l) => l.ruta === RUTA)).toHaveLength(4)
  })

  it('deja de consultar después de los intentos previstos si la IA no termina', async () => {
    const pendiente = reclamo({ origenPrioridad: 'PENDIENTE_IA' })
    const llamadas = simularApi({ [`GET ${RUTA}`]: pendiente, [`GET ${RUTA}/notificaciones`]: [] })
    render(
      <DetalleReclamo usuario={ana} numero="REC-4F2A91BC" areas={AREAS} onVolver={vi.fn()} revisionIA={{ cada: 5, intentos: 2 }} />,
    )

    expect(await screen.findByText(/la IA lo está revisando/)).toBeInTheDocument()
    await new Promise((listo) => setTimeout(listo, 80))
    expect(llamadas.filter((l) => l.ruta === RUTA)).toHaveLength(3)
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
    // Solo las áreas del municipio del reclamo: la Obras Públicas de Avellaneda no aparece.
    expect(within(selector).getAllByRole('option', { name: 'Obras Públicas' })).toHaveLength(1)
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

  it('el vecino ve el tipo de problema que escribió, sin la parte de recategorizar', async () => {
    mostrar(ana, { [`GET ${RUTA}`]: deOtroProblema({ accionesDisponibles: ['CANCELADO'], motivoSugerencia: 'Es la vereda.' }) })

    expect(await screen.findByText('“Baldosas levantadas”, en palabras del vecino')).toBeInTheDocument()
    expect(screen.queryByLabelText('Cambiar la categoría')).not.toBeInTheDocument()
  })

  it('el administrador ve la sugerencia de la IA ya elegida y recategoriza con un clic', async () => {
    let estado = deOtroProblema({
      categoriaSugerida: { id: 7, nombre: 'Vereda rota' },
      motivoSugerencia: 'Las baldosas sueltas son de la vereda.',
    })
    const llamadas = mostrar(elena, {
      [`GET ${RUTA}`]: () => estado,
      [`PATCH ${RUTA}/categoria`]: () => {
        estado = reclamo({ categoria: { id: 7, nombre: 'Vereda rota' }, area: { id: 2, nombre: 'Obras Públicas' } })
        return estado
      },
    })
    const usuario = userEvent.setup()

    expect(await screen.findByText(/La IA sugiere/)).toHaveTextContent(
      'La IA sugiere Vereda rota: Las baldosas sueltas son de la vereda.',
    )
    const selector = screen.getByLabelText('Cambiar la categoría')
    expect(selector).toHaveValue('7')
    expect(within(selector).getByRole('option', { name: 'Vereda rota (sugerida por la IA)' })).toBeInTheDocument()
    // Ni "Otro problema" ni la categoría actual.
    expect(within(selector).queryByRole('option', { name: 'Otro problema' })).not.toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Observación (opcional)'), 'Como sugirió la IA')
    await usuario.click(screen.getByRole('button', { name: 'Recategorizar' }))

    expect(await screen.findByText('El reclamo ahora es de Vereda rota.')).toBeInTheDocument()
    const cambio = llamadas.find((llamada) => llamada.ruta.endsWith('/categoria'))
    expect(cambio).toMatchObject({
      metodo: 'PATCH',
      csrf: TOKEN_CSRF,
      cuerpo: { categoriaId: 7, observacion: 'Como sugirió la IA' },
    })
    expect(screen.queryByLabelText('Cambiar la categoría')).not.toBeInTheDocument()
  })

  it('si la IA no sugirió ninguna, el administrador elige y lo dice', async () => {
    mostrar(elena, { [`GET ${RUTA}`]: deOtroProblema({ motivoSugerencia: 'No se parece a ninguna.' }) })

    expect(await screen.findByText('La IA no sugirió ninguna categoría: No se parece a ninguna.')).toBeInTheDocument()
    expect(screen.getByLabelText('Cambiar la categoría')).toHaveValue('')
  })

  it('mientras la IA analiza la categoría, consulta de nuevo y muestra la sugerencia cuando llega', async () => {
    const esperando = deOtroProblema()
    const sugerido = deOtroProblema({ categoriaSugerida: { id: 7, nombre: 'Vereda rota' }, motivoSugerencia: 'Vereda.' })
    const respuestas = [esperando, esperando]
    simularApi({ [`GET ${RUTA}`]: () => respuestas.shift() ?? sugerido, [`GET ${RUTA}/notificaciones`]: [] })
    render(
      <DetalleReclamo
        usuario={elena}
        numero="REC-4F2A91BC"
        areas={AREAS}
        categorias={CATEGORIAS}
        onVolver={vi.fn()}
        revisionIA={{ cada: 10, intentos: 5 }}
      />,
    )

    expect(await screen.findByText('La IA todavía está analizando qué categoría corresponde.')).toBeInTheDocument()
    expect(await screen.findByText(/La IA sugiere/)).toBeInTheDocument()
    expect(screen.getByLabelText('Cambiar la categoría')).toHaveValue('7')
  })

  it('con el reclamo ya asignado no se ofrece recategorizar', async () => {
    mostrar(elena, { [`GET ${RUTA}`]: reclamo({ accionesDisponibles: ['ASIGNADO'] }) })

    expect(await screen.findByLabelText('Reasignar área')).toBeInTheDocument()
    expect(screen.queryByLabelText('Cambiar la categoría')).not.toBeInTheDocument()
  })
})
