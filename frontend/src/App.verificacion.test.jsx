import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import { AREAS, BARRIOS, CATEGORIAS, TOKEN_CSRF, USUARIOS, simularApi } from './pruebas/apiFalsa.js'

const [ana, , elena] = USUARIOS
const sinTelefono = { ...ana, telefonoVerificado: null }

const CATALOGOS = {
  'GET /api/categorias': CATEGORIAS,
  'GET /api/barrios': BARRIOS,
  'GET /api/areas': AREAS,
}

const principal = () => within(screen.getByRole('main'))

async function irACrear(usuario) {
  await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
}

describe('Teléfono verificado, bloqueo de cuentas', () => {
  it('sin teléfono verificado, Crear reclamo pide verificarlo; en modo simulado se confirma con el número', async () => {
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': sinTelefono,
      'GET /api/reclamos': [],
      'POST /api/verificacion-telefono': {
        status: 201,
        cuerpo: { codigo: 'K7M2Q9XP', enlace: null, vence: '2026-11-02T10:10:00', modo: 'simulado' },
      },
      'POST /api/verificacion-telefono/simulada': { ...ana, telefonoVerificado: '+54 •••••••0000' },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await irACrear(usuario)
    expect(await screen.findByRole('heading', { name: 'Verificá tu teléfono' })).toBeInTheDocument()
    expect(principal().queryByLabelText('Descripción')).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Pedir código de verificación' }))
    expect(await screen.findByText('K7M2Q9XP')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'abrir en Telegram' })).not.toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Teléfono'), ' +54 9 11 5555-0000 ')
    await usuario.click(screen.getByRole('button', { name: 'Confirmar teléfono' }))

    expect(await principal().findByRole('button', { name: 'Crear reclamo' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Verificá tu teléfono' })).not.toBeInTheDocument()
    const confirmacion = llamadas.find((llamada) => llamada.ruta === '/api/verificacion-telefono/simulada')
    expect(confirmacion).toMatchObject({ csrf: TOKEN_CSRF, cuerpo: { codigo: 'K7M2Q9XP', telefono: '+54 9 11 5555-0000' } })
  })

  it('con Telegram muestra el enlace al bot y entra al formulario cuando el bot confirma', async () => {
    let verificado = false
    simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': () => (verificado ? ana : sinTelefono),
      'GET /api/reclamos': [],
      'POST /api/verificacion-telefono': {
        status: 201,
        cuerpo: {
          codigo: 'K7M2Q9XP',
          enlace: 'https://t.me/ReclamosQuilmesBot?start=K7M2Q9XP',
          vence: '2026-11-02T10:10:00',
          modo: 'telegram',
        },
      },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await irACrear(usuario)
    await usuario.click(await screen.findByRole('button', { name: 'Pedir código de verificación' }))
    const enlace = await screen.findByRole('link', { name: 'abrir en Telegram' })
    expect(enlace).toHaveAttribute('href', 'https://t.me/ReclamosQuilmesBot?start=K7M2Q9XP')
    expect(screen.getByText('Esperando la confirmación de Telegram…')).toBeInTheDocument()
    expect(screen.queryByLabelText('Teléfono')).not.toBeInTheDocument()

    verificado = true // el ciudadano compartió su contacto con el bot
    expect(await principal().findByRole('button', { name: 'Crear reclamo' }, { timeout: 4500 })).toBeInTheDocument()
  }, 8000)

  it('si ya pidió demasiados códigos, muestra el límite', async () => {
    simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': sinTelefono,
      'GET /api/reclamos': [],
      'POST /api/verificacion-telefono': {
        status: 429,
        cuerpo: { status: 429, codigo: 'LIMITE_EXCEDIDO', mensaje: 'Ya pediste 3 códigos en la última hora.', detalles: [] },
      },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await irACrear(usuario)
    await usuario.click(await screen.findByRole('button', { name: 'Pedir código de verificación' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya pediste 3 códigos en la última hora.')
  })

  it('una cuenta bloqueada ve sus reclamos pero no el formulario de alta', async () => {
    simularApi({ ...CATALOGOS, 'GET /api/auth/yo': { ...ana, bloqueado: true }, 'GET /api/reclamos': [] })
    const usuario = userEvent.setup()
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Mis reclamos' })).toBeInTheDocument()
    await irACrear(usuario)
    expect(await screen.findByRole('alert')).toHaveTextContent('Tu cuenta está bloqueada')
    expect(principal().queryByRole('button', { name: 'Crear reclamo' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Verificá tu teléfono' })).not.toBeInTheDocument()
  })

  it('el administrador bloquea la cuenta de un ciudadano desde Administración', async () => {
    let cuentas = USUARIOS
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': elena,
      'GET /api/reclamos': [],
      'GET /api/usuarios': () => cuentas,
      'PATCH /api/usuarios/1/bloqueo': (peticion) => {
        cuentas = cuentas.map((cuenta) => (cuenta.id === 1 ? { ...cuenta, bloqueado: peticion.cuerpo.bloqueado } : cuenta))
        return cuentas[0]
      },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Administración' }))
    expect(await screen.findByRole('heading', { name: 'Cuentas de ciudadanos' })).toBeInTheDocument()
    const fila = (await screen.findByRole('cell', { name: 'Ana Pérez' })).closest('tr')
    expect(within(fila).getByText('+54 •••••••0001')).toBeInTheDocument()
    expect(screen.queryByRole('cell', { name: 'Carla Gómez' })).not.toBeInTheDocument()

    await usuario.click(within(fila).getByRole('button', { name: 'Bloquear a Ana Pérez' }))

    expect(await screen.findByText('Ana Pérez: cuenta bloqueada.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Desbloquear a Ana Pérez' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.metodo === 'PATCH')).toMatchObject({ csrf: TOKEN_CSRF, cuerpo: { bloqueado: true } })
  })
})
