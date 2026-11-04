import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../App.jsx'
import { AREAS, BARRIOS, CATEGORIAS, SIN_SESION, USUARIOS, simularApi } from '../pruebas/apiFalsa.js'
import Tablero, { formatearDia, textoPorcentaje } from './Tablero.jsx'

const [ana, carla, elena] = USUARIOS

const PUBLICO = {
  desde: '2026-10-05',
  hasta: '2026-11-03',
  barrioId: null,
  categoriaId: null,
  creadosEnPeriodo: 5,
  pendientes: 3,
  resueltos: 1,
  cerrados: 1,
  rechazados: 0,
  cancelados: 1,
  porcentajeAtendidos: 40,
}

const GESTION = {
  fecha: '2026-11-03T15:00:00',
  area: null,
  pendientes: 3,
  vencidos: 2,
  sinAsignar: 1,
  porcentajeVencidos: 66.7,
  filas: [
    { area: { id: 1, nombre: 'Alumbrado' }, barrio: { id: 2, nombre: 'Bernal' }, pendientes: 2, vencidos: 1 },
    { area: null, barrio: { id: 2, nombre: 'Bernal' }, pendientes: 1, vencidos: 0 },
  ],
}

const cifra = (titulo) => screen.getByText(titulo, { selector: '.cifra-titulo' }).closest('.cifra')

describe('Tablero', () => {
  it('el ciudadano ve solo el público, con el período y el porcentaje', async () => {
    const llamadas = simularApi({ 'GET /api/dashboard/publico': PUBLICO })
    render(<Tablero usuario={ana} barrios={BARRIOS} categorias={CATEGORIAS} />)

    expect(await screen.findByText('Creados en el período')).toBeInTheDocument()
    expect(cifra('Creados en el período')).toHaveTextContent('5Del 05/10/2026 al 03/11/2026')
    expect(cifra('Pendientes hoy')).toHaveTextContent('3')
    expect(cifra('Atendidos')).toHaveTextContent('40 %')
    expect(screen.getByText(/0 rechazados y 1 cancelados/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Tablero de gestión/ })).not.toBeInTheDocument()
    expect(llamadas.map((llamada) => llamada.ruta)).toEqual(['/api/dashboard/publico'])
  })

  it('sin casos muestra "sin casos" en lugar de un porcentaje', async () => {
    simularApi({ 'GET /api/dashboard/publico': { ...PUBLICO, porcentajeAtendidos: null } })
    render(<Tablero usuario={ana} barrios={BARRIOS} categorias={CATEGORIAS} />)

    expect(await screen.findByText('sin casos')).toBeInTheDocument()
  })

  it('los filtros vuelven a pedir las cifras con barrio, categoría y fechas', async () => {
    const llamadas = simularApi({ 'GET /api/dashboard/publico': PUBLICO })
    const usuario = userEvent.setup()
    render(<Tablero usuario={ana} barrios={BARRIOS} categorias={CATEGORIAS} />)
    await screen.findByText('Creados en el período')

    await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
    await usuario.selectOptions(screen.getByLabelText('Categoría'), 'Luminaria rota')
    await usuario.type(screen.getByLabelText('Desde'), '2026-10-01')

    const ultima = llamadas.at(-1)
    expect(ultima.consulta).toEqual({ barrioId: '2', categoriaId: '1', desde: '2026-10-01' })
  })

  it('el administrador ve la gestión: vencidos con su porcentaje, sin asignar y la tabla por área y barrio', async () => {
    simularApi({ 'GET /api/dashboard/publico': PUBLICO, 'GET /api/dashboard/gestion': GESTION })
    render(<Tablero usuario={elena} barrios={BARRIOS} categorias={CATEGORIAS} />)

    expect(await screen.findByRole('heading', { name: 'Tablero de gestión' })).toBeInTheDocument()
    expect(cifra('Vencidos')).toHaveTextContent('266,7 % de los pendientes')
    expect(cifra('Sin asignar')).toHaveTextContent('1')
    const filas = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(filas.map((fila) => fila.textContent)).toEqual(['AlumbradoBernal21', 'Sin áreaBernal10'])
  })

  it('el agente ve su área y no la cifra de sin asignar', async () => {
    simularApi({
      'GET /api/dashboard/publico': PUBLICO,
      'GET /api/dashboard/gestion': {
        ...GESTION,
        area: { id: 1, nombre: 'Alumbrado' },
        sinAsignar: null,
        filas: [],
        pendientes: 0,
        vencidos: 0,
        porcentajeVencidos: null,
      },
    })
    render(<Tablero usuario={carla} barrios={BARRIOS} categorias={CATEGORIAS} />)

    expect(await screen.findByRole('heading', { name: 'Tablero de gestión: Alumbrado' })).toBeInTheDocument()
    expect(screen.queryByText('Sin asignar')).not.toBeInTheDocument()
    expect(screen.getByText('No hay reclamos pendientes.')).toBeInTheDocument()
    expect(cifra('Vencidos')).toHaveTextContent('sin casos de los pendientes')
  })

  it('sin sesión, el tablero público se ve desde la pantalla de ingreso', async () => {
    simularApi({
      'GET /api/categorias': CATEGORIAS,
      'GET /api/barrios': BARRIOS,
      'GET /api/areas': AREAS,
      'GET /api/auth/yo': SIN_SESION,
      'GET /api/dashboard/publico': PUBLICO,
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Ver el tablero público, sin iniciar sesión' }))

    expect(await screen.findByRole('heading', { name: 'Tablero público' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('formatea días y porcentajes', () => {
    expect(formatearDia('2026-10-05')).toBe('05/10/2026')
    expect(textoPorcentaje(null)).toBe('sin casos')
    expect(textoPorcentaje(100)).toBe('100 %')
  })
})
