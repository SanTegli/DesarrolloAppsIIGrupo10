import { describe, expect, it, vi } from 'vitest'
import { simularApi } from '../pruebas/apiFalsa.js'
import { ErrorApi, pedir } from './cliente.js'
import { asignarArea, cambiarEstado, crearReclamo, listarAvisos, listarReclamos } from './reclamos.js'

describe('cliente HTTP', () => {
  it('envía X-Usuario-Id y pide JSON', async () => {
    const llamadas = simularApi({ 'GET /api/reclamos': [] })

    await pedir('/reclamos', { usuarioId: 3 })

    expect(llamadas[0].usuarioId).toBe('3')
    expect(fetch.mock.calls[0][1].headers.Accept).toBe('application/json')
  })

  it('no envía X-Usuario-Id en las consultas de apoyo', async () => {
    const llamadas = simularApi({ 'GET /api/usuarios': [] })

    await pedir('/usuarios')

    expect(llamadas[0].usuarioId).toBeUndefined()
  })

  it('arma la consulta solo con los filtros que tienen valor', async () => {
    const llamadas = simularApi({ 'GET /api/reclamos': [] })

    await listarReclamos(7, { estado: 'INGRESADO', areaId: '', ciudadanoId: undefined })

    expect(llamadas[0].consulta).toEqual({ estado: 'INGRESADO' })
  })

  it('crea un reclamo con POST y el cuerpo del contrato', async () => {
    const llamadas = simularApi({ 'POST /api/reclamos': { status: 201, cuerpo: { numero: 'REC-1' } } })

    const creado = await crearReclamo(1, { categoriaId: 1, barrioId: 2, descripcion: 'Farol apagado', direccion: 'Belgrano 450' })

    expect(creado.numero).toBe('REC-1')
    expect(llamadas[0]).toMatchObject({
      metodo: 'POST',
      usuarioId: '1',
      cuerpo: { categoriaId: 1, barrioId: 2, descripcion: 'Farol apagado', direccion: 'Belgrano 450' },
    })
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBe('application/json')
  })

  it('cambia el estado con PATCH y manda null cuando no hay observación', async () => {
    const llamadas = simularApi({ 'PATCH /api/reclamos/REC-1/estado': {} })

    await cambiarEstado(3, 'REC-1', 'EN_PROCESO', '')

    expect(llamadas[0]).toMatchObject({ metodo: 'PATCH', usuarioId: '3', cuerpo: { estado: 'EN_PROCESO', observacion: null } })
  })

  it('asigna el área con PATCH a /asignacion', async () => {
    const llamadas = simularApi({ 'PATCH /api/reclamos/REC-1/asignacion': {} })

    await asignarArea(7, 'REC-1', 2, 'Corresponde a Obras')

    expect(llamadas[0].cuerpo).toEqual({ areaId: 2, observacion: 'Corresponde a Obras' })
  })

  it('pide la bandeja de avisos con el X-Usuario-Id del usuario', async () => {
    const llamadas = simularApi({ 'GET /api/notificaciones': [] })

    await listarAvisos(3)

    expect(llamadas[0]).toMatchObject({ metodo: 'GET', ruta: '/api/notificaciones', usuarioId: '3' })
  })

  it('convierte el error del contrato en ErrorApi con status, código, mensaje y detalles', async () => {
    simularApi({
      'POST /api/reclamos': {
        status: 400,
        cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'Los datos recibidos no son validos.', detalles: ['direccion: no debe estar vacío'] },
      },
    })

    const error = await crearReclamo(1, {}).catch((fallo) => fallo)

    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({ status: 400, codigo: 'DATOS_INVALIDOS', message: 'Los datos recibidos no son validos.' })
    expect(error.detalles).toEqual(['direccion: no debe estar vacío'])
  })

  it('explica que el backend no responde cuando el proxy devuelve un 5xx sin JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 502, json: async () => { throw new SyntaxError('no es JSON') } })))

    const error = await pedir('/usuarios').catch((fallo) => fallo)

    expect(error).toMatchObject({ status: 502, codigo: 'SIN_CONEXION' })
    expect(error.message).toMatch(/backend/)
  })

  it('explica el fallo de red cuando fetch no llega a conectar', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    const error = await pedir('/usuarios').catch((fallo) => fallo)

    expect(error).toMatchObject({ status: 0, codigo: 'SIN_CONEXION' })
  })
})
