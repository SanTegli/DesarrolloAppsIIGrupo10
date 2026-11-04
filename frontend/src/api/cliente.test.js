import { describe, expect, it, vi } from 'vitest'
import { SIN_SESION, TOKEN_CSRF, archivoFoto, simularApi } from '../pruebas/apiFalsa.js'
import { ErrorApi, alVencerSesion, pedir } from './cliente.js'
import { asignarArea, cambiarEstado, crearReclamo, listarAvisos, listarReclamos } from './reclamos.js'

describe('cliente HTTP', () => {
  it('pide JSON y manda la cookie de sesión, sin ningún encabezado de identidad', async () => {
    const llamadas = simularApi({ 'GET /api/reclamos': [] })

    await pedir('/reclamos')

    const encabezados = fetch.mock.calls[0][1].headers
    expect(encabezados.Accept).toBe('application/json')
    expect(encabezados).not.toHaveProperty('X-Usuario-Id')
    expect(llamadas[0].credenciales).toBe('same-origin')
  })

  it('manda el token CSRF de la cookie en POST y PATCH, y no en GET', async () => {
    const llamadas = simularApi({ 'GET /api/reclamos': [], 'POST /api/reclamos': { status: 201, cuerpo: {} }, 'PATCH /api/reclamos/REC-1/estado': {} })

    await pedir('/reclamos')
    await pedir('/reclamos', { metodo: 'POST', cuerpo: {} })
    await pedir('/reclamos/REC-1/estado', { metodo: 'PATCH', cuerpo: {} })

    expect(llamadas.map((llamada) => llamada.csrf)).toEqual([undefined, TOKEN_CSRF, TOKEN_CSRF])
  })

  it('si todavía no hay cookie CSRF, la pide con un GET público antes del POST', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    const llamadas = simularApi({
      'GET /api/categorias': () => {
        document.cookie = 'XSRF-TOKEN=token-nuevo; path=/'
        return []
      },
      'POST /api/auth/login': { id: 1 },
    })

    await pedir('/auth/login', { metodo: 'POST', cuerpo: {}, avisarSiNoHaySesion: false })

    expect(llamadas.map((llamada) => llamada.ruta)).toEqual(['/api/categorias', '/api/auth/login'])
    expect(llamadas[1].csrf).toBe('token-nuevo')
  })

  it('un 401 avisa que la sesión venció, salvo en las llamadas de login', async () => {
    simularApi({ 'GET /api/reclamos': SIN_SESION, 'GET /api/auth/yo': SIN_SESION })
    const vencida = vi.fn()
    const darDeBaja = alVencerSesion(vencida)

    await expect(pedir('/auth/yo', { avisarSiNoHaySesion: false })).rejects.toMatchObject({ status: 401 })
    expect(vencida).not.toHaveBeenCalled()
    await expect(pedir('/reclamos')).rejects.toMatchObject({ status: 401, codigo: 'NO_AUTENTICADO' })
    expect(vencida).toHaveBeenCalledTimes(1)

    darDeBaja()
    await expect(pedir('/reclamos')).rejects.toMatchObject({ status: 401 })
    expect(vencida).toHaveBeenCalledTimes(1)
  })

  it('arma la consulta solo con los filtros que tienen valor', async () => {
    const llamadas = simularApi({ 'GET /api/reclamos': [] })

    await listarReclamos({ estado: 'INGRESADO', areaId: '', ciudadanoId: undefined })

    expect(llamadas[0].consulta).toEqual({ estado: 'INGRESADO' })
  })

  it('crea un reclamo con POST multipart: el JSON del contrato en la parte reclamo y la foto', async () => {
    const llamadas = simularApi({ 'POST /api/reclamos': { status: 201, cuerpo: { numero: 'REC-1' } } })

    const creado = await crearReclamo({
      categoriaId: 1,
      barrioId: 2,
      descripcion: 'Farol apagado',
      direccion: 'Belgrano 450',
      foto: archivoFoto(),
    })

    expect(creado.numero).toBe('REC-1')
    expect(llamadas[0]).toMatchObject({
      metodo: 'POST',
      csrf: TOKEN_CSRF,
      cuerpo: {
        multipart: true,
        tipoReclamo: 'application/json',
        categoriaId: 1,
        barrioId: 2,
        descripcion: 'Farol apagado',
        direccion: 'Belgrano 450',
        foto: { nombre: 'farol.jpg', tipo: 'image/jpeg' },
      },
    })
    expect(fetch.mock.calls[0][1].body).toBeInstanceOf(FormData)
    // El navegador pone multipart/form-data con el separador: a mano lo rompería.
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
  })

  it('las demás escrituras siguen yendo en JSON', async () => {
    simularApi({ 'PATCH /api/reclamos/REC-1/estado': {} })

    await cambiarEstado('REC-1', 'CERRADO', '')

    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBe('application/json')
  })

  it('cambia el estado con PATCH y manda null cuando no hay observación', async () => {
    const llamadas = simularApi({ 'PATCH /api/reclamos/REC-1/estado': {} })

    await cambiarEstado('REC-1', 'EN_PROCESO', '')

    expect(llamadas[0]).toMatchObject({ metodo: 'PATCH', csrf: TOKEN_CSRF, cuerpo: { estado: 'EN_PROCESO', observacion: null } })
  })

  it('asigna el área con PATCH a /asignacion', async () => {
    const llamadas = simularApi({ 'PATCH /api/reclamos/REC-1/asignacion': {} })

    await asignarArea('REC-1', 2, 'Corresponde a Obras')

    expect(llamadas[0].cuerpo).toEqual({ areaId: 2, observacion: 'Corresponde a Obras' })
  })

  it('pide la bandeja de avisos del usuario de la sesión', async () => {
    const llamadas = simularApi({ 'GET /api/notificaciones': [] })

    await listarAvisos()

    expect(llamadas[0]).toMatchObject({ metodo: 'GET', ruta: '/api/notificaciones' })
  })

  it('convierte el error del contrato en ErrorApi con status, código, mensaje y detalles', async () => {
    simularApi({
      'POST /api/reclamos': {
        status: 400,
        cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'Los datos recibidos no son validos.', detalles: ['direccion: no debe estar vacío'] },
      },
    })

    const error = await crearReclamo({}).catch((fallo) => fallo)

    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({ status: 400, codigo: 'DATOS_INVALIDOS', message: 'Los datos recibidos no son validos.' })
    expect(error.detalles).toEqual(['direccion: no debe estar vacío'])
  })

  it('explica que el backend no responde cuando el proxy devuelve un 5xx sin JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 502, json: async () => { throw new SyntaxError('no es JSON') } })))

    const error = await pedir('/categorias').catch((fallo) => fallo)

    expect(error).toMatchObject({ status: 502, codigo: 'SIN_CONEXION' })
    expect(error.message).toMatch(/backend/)
  })

  it('explica el fallo de red cuando fetch no llega a conectar', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    const error = await pedir('/categorias').catch((fallo) => fallo)

    expect(error).toMatchObject({ status: 0, codigo: 'SIN_CONEXION' })
  })
})
