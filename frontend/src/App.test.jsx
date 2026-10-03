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
    expect(pestanas()).toEqual(['Reclamos', 'Crear reclamo'])
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
    expect(pestanas()).toEqual(['Reclamos'])
    expect(await screen.findByText('Tu área no tiene reclamos asignados.')).toBeInTheDocument()

    await usuario.selectOptions(selector, 'Elena Ruiz, Administrador')
    expect(await screen.findByRole('heading', { name: 'Todos los reclamos' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Administración'])

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
