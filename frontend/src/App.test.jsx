import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import { AREAS, BARRIOS, CATEGORIAS, USUARIOS, reclamo, simularApi } from './pruebas/apiFalsa.js'

const CATALOGOS = {
  'GET /api/usuarios': USUARIOS,
  'GET /api/categorias': CATEGORIAS,
  'GET /api/barrios': BARRIOS,
  'GET /api/areas': AREAS,
}

const pestanas = () => within(screen.getByRole('navigation')).getAllByRole('button').map((boton) => boton.textContent)

describe('App', () => {
  it('arranca como el primer usuario y lista sus reclamos con su X-Usuario-Id', async () => {
    const llamadas = simularApi({ ...CATALOGOS, 'GET /api/reclamos': [reclamo()] })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Mis reclamos' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'REC-4F2A91BC' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Crear reclamo', 'Avisos'])
    expect(llamadas.find((llamada) => llamada.ruta === '/api/reclamos').usuarioId).toBe('1')
  })

  it('cambia las secciones y el X-Usuario-Id al elegir otro usuario', async () => {
    const llamadas = simularApi({ ...CATALOGOS, 'GET /api/reclamos': [] })
    const usuario = userEvent.setup()
    render(<App />)
    const selector = await screen.findByLabelText('Usar el sistema como')
    await screen.findByText('Todavía no creaste ningún reclamo.')

    await usuario.selectOptions(selector, 'Carla Gómez, Agente municipal de Alumbrado')
    expect(await screen.findByRole('heading', { name: 'Reclamos de mi área' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Avisos'])
    expect(await screen.findByText('Tu área no tiene reclamos asignados.')).toBeInTheDocument()

    await usuario.selectOptions(selector, 'Elena Ruiz, Administrador')
    expect(await screen.findByRole('heading', { name: 'Todos los reclamos' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Administración', 'Avisos'])

    const usados = llamadas.filter((llamada) => llamada.ruta === '/api/reclamos').map((llamada) => llamada.usuarioId)
    expect(usados).toEqual(['1', '3', '7'])
  })

  it('recuerda el usuario elegido al volver a abrir la aplicación', async () => {
    localStorage.setItem('reclamos.usuarioId', '7')
    simularApi({ ...CATALOGOS, 'GET /api/reclamos': [] })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Todos los reclamos' })).toBeInTheDocument()
    expect(screen.getByLabelText('Usar el sistema como')).toHaveValue('7')
  })

  it('el ciudadano crea un reclamo y llega al detalle con el aviso de asignación', async () => {
    const creado = reclamo({ accionesDisponibles: ['CANCELADO'] })
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'POST /api/reclamos': { status: 201, cuerpo: creado },
      'GET /api/reclamos/REC-4F2A91BC': creado,
      'GET /api/reclamos/REC-4F2A91BC/notificaciones': [],
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    expect(screen.getByText(/Plazo de atención: 48 horas/)).toBeInTheDocument()
    await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), '  Farol apagado frente a la plaza ')
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    expect(await screen.findByText('Reclamo creado y asignado a Alumbrado.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Cancelar reclamo' })).toBeInTheDocument()
    const alta = llamadas.find((llamada) => llamada.metodo === 'POST')
    expect(alta).toMatchObject({
      usuarioId: '1',
      cuerpo: { categoriaId: 1, barrioId: 2, direccion: 'Belgrano 450', descripcion: 'Farol apagado frente a la plaza' },
    })
  })

  it('el ciudadano busca la dirección en el mapa, elige una opción y la manda en el alta', async () => {
    const bernal = {
      direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires',
      latitud: -34.73614578,
      longitud: -58.3228998575,
    }
    const quilmes = { direccionNormalizada: 'GRL BELGRANO 450, Quilmes, Buenos Aires', latitud: -34.758, longitud: -58.297 }
    const creado = reclamo({ ubicacion: { estado: 'CONFIRMADA', ...bernal, proveedor: 'georef' } })
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'GET /api/ubicaciones/sugerencias': { disponible: true, sugerencias: [bernal, quilmes] },
      'POST /api/reclamos': { status: 201, cuerpo: creado },
      'GET /api/reclamos/REC-4F2A91BC': creado,
      'GET /api/reclamos/REC-4F2A91BC/notificaciones': [],
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    const buscar = screen.getByRole('button', { name: 'Buscar en el mapa' })
    expect(buscar).toBeDisabled()
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(buscar)

    const opcion = await screen.findByRole('radio', { name: bernal.direccionNormalizada })
    expect(screen.getByRole('radio', { name: /Ninguna de estas/ })).toBeChecked()
    expect(screen.getAllByRole('link', { name: 'Ver en el mapa' })).toHaveLength(2)
    await usuario.click(opcion)
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado frente a la plaza')
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    expect(await screen.findByRole('link', { name: 'Ver en Google Maps' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/ubicaciones/sugerencias').consulta).toEqual({
      direccion: 'Belgrano 450',
      barrioId: '2',
    })
    expect(llamadas.find((llamada) => llamada.metodo === 'POST').cuerpo.ubicacion).toEqual(bernal)
  })

  it('si el mapa no responde lo avisa, y cambiar la dirección borra la búsqueda', async () => {
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'GET /api/ubicaciones/sugerencias': { disponible: false, sugerencias: [] },
      'POST /api/reclamos': { status: 400, cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'Prueba', detalles: [] } },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(screen.getByRole('button', { name: 'Buscar en el mapa' }))

    expect(await screen.findByText(/El mapa no responde en este momento/)).toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Dirección'), '0')
    expect(screen.queryByText(/El mapa no responde en este momento/)).not.toBeInTheDocument()

    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))
    await screen.findByRole('alert')
    expect(llamadas.find((llamada) => llamada.metodo === 'POST').cuerpo).not.toHaveProperty('ubicacion')
  })

  it('muestra los errores de validación del backend en el formulario', async () => {
    simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'POST /api/reclamos': {
        status: 400,
        cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'Los datos recibidos no son validos.', detalles: ['descripcion: no debe estar vacío'] },
      },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'x')
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Los datos recibidos no son validos.')
    expect(alerta).toHaveTextContent('descripcion: no debe estar vacío')
    expect(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' })).toBeEnabled()
  })

  it('el administrador ve en Administración los reclamos sin asignar y la cobertura de las áreas', async () => {
    localStorage.setItem('reclamos.usuarioId', '7')
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': (peticion) => (peticion.consulta.estado === 'INGRESADO' ? [reclamo({ estado: 'INGRESADO', area: null })] : []),
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Administración' }))

    expect(await screen.findByRole('heading', { name: 'Reclamos sin asignar' })).toBeInTheDocument()
    expect(await screen.findByText('Sin asignar')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Obras Públicas' })).toBeInTheDocument()
    expect(llamadas.some((llamada) => llamada.consulta?.estado === 'INGRESADO' && llamada.usuarioId === '7')).toBe(true)
  })

  it('el agente ve en Avisos lo que le notificaron y abre el reclamo desde ahí', async () => {
    localStorage.setItem('reclamos.usuarioId', '3')
    const asignado = reclamo({ accionesDisponibles: ['EN_PROCESO'] })
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'GET /api/notificaciones': [
        { numeroReclamo: 'REC-4F2A91BC', canal: 'INTERNO', mensaje: 'El reclamo REC-4F2A91BC fue asignado a tu area Alumbrado.', fechaEnvio: '2026-10-05T10:00:00' },
      ],
      'GET /api/reclamos/REC-4F2A91BC': asignado,
      'GET /api/reclamos/REC-4F2A91BC/notificaciones': [],
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))

    expect(await screen.findByText('El reclamo REC-4F2A91BC fue asignado a tu area Alumbrado.')).toBeInTheDocument()
    expect(screen.getByText('05/10/2026 10:00')).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/notificaciones').usuarioId).toBe('3')

    await usuario.click(screen.getByRole('button', { name: 'REC-4F2A91BC' }))
    expect(await screen.findByRole('button', { name: 'Tomar reclamo' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Volver al listado' }))
    expect(await screen.findByRole('heading', { name: 'Avisos' })).toBeInTheDocument()
  })

  it('la bandeja vacía lo dice y un error del backend se muestra', async () => {
    simularApi({ ...CATALOGOS, 'GET /api/reclamos': [], 'GET /api/notificaciones': [] })
    const usuario = userEvent.setup()
    const { unmount } = render(<App />)
    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))
    expect(await screen.findByText('No tenés avisos.')).toBeInTheDocument()
    unmount()

    simularApi({
      ...CATALOGOS,
      'GET /api/reclamos': [],
      'GET /api/notificaciones': { status: 404, cuerpo: { status: 404, codigo: 'RECURSO_NO_ENCONTRADO', mensaje: 'No existe usuario con identificador 1', detalles: [] } },
    })
    render(<App />)
    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No existe usuario con identificador 1')
  })

  it('avisa cuando el backend no responde y permite reintentar', async () => {
    simularApi({
      'GET /api/usuarios': { status: 502, cuerpo: null },
      'GET /api/categorias': [],
      'GET /api/barrios': [],
      'GET /api/areas': [],
    })
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no responde')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
