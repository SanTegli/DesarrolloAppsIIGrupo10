import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import {
  AREAS,
  MUNICIPIOS,
  CATEGORIAS,
  SIN_SESION,
  TOKEN_CSRF,
  USUARIOS,
  archivoFoto,
  reclamo,
  simularApi,
} from './pruebas/apiFalsa.js'

const [ana, carla, elena] = USUARIOS

const CATALOGOS = {
  'GET /api/categorias': CATEGORIAS,
  'GET /api/municipios': MUNICIPIOS,
  'GET /api/areas': AREAS,
}

/** Los catálogos y una sesión ya iniciada: /api/auth/yo devuelve al usuario. */
const conSesion = (usuario) => ({ ...CATALOGOS, 'GET /api/auth/yo': usuario })

const pestanas = () => within(screen.getByRole('navigation')).getAllByRole('button').map((boton) => boton.textContent)

describe('App', () => {
  it('sin sesión muestra el login, entra con email y contraseña y lista los reclamos', async () => {
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': SIN_SESION,
      'POST /api/auth/login': ana,
      'GET /api/reclamos': [reclamo()],
    })
    const usuario = userEvent.setup()
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Correo electrónico'), ' ana@example.org ')
    await usuario.type(screen.getByLabelText('Contraseña'), 'demo1234')
    await usuario.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Mis reclamos' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'REC-4F2A91BC' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Crear reclamo', 'Avisos', 'Tablero'])
    expect(screen.getByText('Ana Pérez, Ciudadano')).toBeInTheDocument()
    const login = llamadas.find((llamada) => llamada.ruta === '/api/auth/login')
    expect(login).toMatchObject({
      metodo: 'POST',
      csrf: TOKEN_CSRF,
      credenciales: 'same-origin',
      cuerpo: { email: 'ana@example.org', contrasena: 'demo1234' },
    })
    expect(llamadas.every((llamada) => llamada.ruta !== '/api/usuarios')).toBe(true)
  })

  it('con la sesión ya iniciada entra directo, con las secciones del rol', async () => {
    simularApi({ ...conSesion(carla), 'GET /api/reclamos': [] })
    const { unmount } = render(<App />)

    expect(await screen.findByRole('heading', { name: 'Reclamos de mi área' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Avisos', 'Tablero'])
    expect(await screen.findByText('Tu área no tiene reclamos asignados.')).toBeInTheDocument()
    unmount()

    simularApi({ ...conSesion(elena), 'GET /api/reclamos': [] })
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Todos los reclamos' })).toBeInTheDocument()
    expect(pestanas()).toEqual(['Reclamos', 'Administración', 'Avisos', 'Tablero'])
  })

  it('un login fallido muestra el error y deja la pantalla de ingreso', async () => {
    simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': SIN_SESION,
      'POST /api/auth/login': {
        status: 401,
        cuerpo: { status: 401, codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'El correo o la contraseña no son correctos.', detalles: [] },
      },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.type(await screen.findByLabelText('Correo electrónico'), 'ana@example.org')
    await usuario.type(screen.getByLabelText('Contraseña'), 'otra-clave')
    await usuario.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El correo o la contraseña no son correctos.')
    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('un vecino nuevo se registra y entra con su cuenta', async () => {
    const laura = { id: 8, nombreCompleto: 'Laura Martínez', rol: 'CIUDADANO', area: null }
    const llamadas = simularApi({
      ...CATALOGOS,
      'GET /api/auth/yo': SIN_SESION,
      'POST /api/auth/registro': { status: 201, cuerpo: laura },
      'POST /api/auth/login': laura,
      'GET /api/reclamos': [],
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear una cuenta' }))
    await usuario.type(screen.getByLabelText('DNI'), '33444555')
    await usuario.type(screen.getByLabelText('Nombre'), 'Laura')
    await usuario.type(screen.getByLabelText('Apellido'), 'Martínez')
    await usuario.type(screen.getByLabelText('Correo electrónico'), 'laura@example.org')
    await usuario.type(screen.getByLabelText(/Contraseña/), 'una-clave-larga')
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta y entrar' }))

    expect(await screen.findByRole('heading', { name: 'Mis reclamos' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/auth/registro').cuerpo).toEqual({
      dni: '33444555',
      nombre: 'Laura',
      apellido: 'Martínez',
      email: 'laura@example.org',
      contrasena: 'una-clave-larga',
    })
    expect(llamadas.find((llamada) => llamada.ruta === '/api/auth/login').cuerpo).toEqual({
      email: 'laura@example.org',
      contrasena: 'una-clave-larga',
    })
  })

  it('Salir cierra la sesión en el backend y vuelve al login', async () => {
    const llamadas = simularApi({ ...conSesion(ana), 'GET /api/reclamos': [], 'POST /api/auth/logout': { status: 204 } })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Salir' }))

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/auth/logout')).toMatchObject({ metodo: 'POST', csrf: TOKEN_CSRF })
    expect(screen.queryByRole('button', { name: 'Salir' })).not.toBeInTheDocument()
  })

  it('si la sesión vence, vuelve al login y lo explica', async () => {
    simularApi({ ...conSesion(ana), 'GET /api/reclamos': SIN_SESION })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.getByText(/Tu sesión venció o se cerró/)).toBeInTheDocument()
  })

  it('el ciudadano crea un reclamo y llega al detalle con el aviso de asignación', async () => {
    const creado = reclamo({ accionesDisponibles: ['CANCELADO'] })
    const llamadas = simularApi({
      ...conSesion(ana),
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
    await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), '  Farol apagado frente a la plaza ')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    expect(await screen.findByText('Reclamo creado y asignado a Alumbrado.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Cancelar reclamo' })).toBeInTheDocument()
    const alta = llamadas.find((llamada) => llamada.metodo === 'POST')
    expect(alta).toMatchObject({
      csrf: TOKEN_CSRF,
      cuerpo: {
        multipart: true,
        tipoReclamo: 'application/json',
        categoriaId: 1,
        municipioId: 1,
        direccion: 'Belgrano 450',
        descripcion: 'Farol apagado frente a la plaza',
        foto: { nombre: 'farol.jpg', tipo: 'image/jpeg', tamano: 2048 },
      },
    })
  })

  it('el ciudadano busca la dirección en el mapa, elige una opción y la manda en el alta', async () => {
    const bernal = {
      direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires',
      latitud: -34.73614578,
      longitud: -58.3228998575,
    }
    const wilde = {
      direccionNormalizada: 'BELGRANO 450, Wilde, Avellaneda, Buenos Aires',
      latitud: -34.701,
      longitud: -58.318,
      municipio: { id: 2, nombre: 'Avellaneda' },
    }
    const { direccionNormalizada, latitud, longitud } = bernal
    const creado = reclamo({ ubicacion: { estado: 'CONFIRMADA', ...bernal, proveedor: 'georef' } })
    const llamadas = simularApi({
      ...conSesion(ana),
      'GET /api/reclamos': [],
      'GET /api/ubicaciones/sugerencias': {
        disponible: true,
        sugerencias: [{ ...bernal, municipio: { id: 1, nombre: 'Quilmes' } }, wilde],
      },
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
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(buscar)

    // Se busca en todos los municipios: cada opción dice de cuál es, y elegirla define el del reclamo.
    const opcion = await screen.findByRole('radio', { name: /Bernal Oeste, Quilmes, Buenos Aires.*·.*Quilmes/ })
    expect(screen.getByRole('radio', { name: /Wilde, Avellaneda, Buenos Aires.*·.*Avellaneda/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Ninguna de estas/ })).toBeChecked()
    expect(screen.getAllByRole('link', { name: 'Ver en el mapa' })).toHaveLength(2)
    await usuario.click(opcion)
    expect(screen.queryByLabelText('Municipio')).not.toBeInTheDocument()
    expect(screen.getByText(/según la dirección elegida/)).toHaveTextContent('Municipio: Quilmes')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado frente a la plaza')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    expect(await screen.findByRole('link', { name: 'Ver en Google Maps' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/ubicaciones/sugerencias').consulta).toEqual({
      direccion: 'Belgrano 450',
    })
    const alta = llamadas.find((llamada) => llamada.ruta === '/api/reclamos' && llamada.metodo === 'POST').cuerpo
    expect(alta.ubicacion).toEqual({ direccionNormalizada, latitud, longitud })
    expect(alta.municipioId).toBe(1)
  })

  it('si el mapa no responde lo avisa, y cambiar la dirección borra la búsqueda', async () => {
    const llamadas = simularApi({
      ...conSesion(ana),
      'GET /api/reclamos': [],
      'GET /api/ubicaciones/sugerencias': { disponible: false, sugerencias: [] },
      'POST /api/reclamos': { status: 400, cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'Prueba', detalles: [] } },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(screen.getByRole('button', { name: 'Buscar en el mapa' }))

    expect(await screen.findByText(/El mapa no responde en este momento/)).toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Dirección'), '0')
    expect(screen.queryByText(/El mapa no responde en este momento/)).not.toBeInTheDocument()

    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))
    await screen.findByRole('alert')
    expect(llamadas.find((llamada) => llamada.metodo === 'POST').cuerpo).not.toHaveProperty('ubicacion')
  })

  it('muestra los errores de validación del backend en el formulario', async () => {
    simularApi({
      ...conSesion(ana),
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
    await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'x')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Los datos recibidos no son validos.')
    expect(alerta).toHaveTextContent('descripcion: no debe estar vacío')
    expect(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' })).toBeEnabled()
  })

  it('si el problema ya estaba reclamado cerca, el vecino lo apoya y vuelve a sus reclamos con el aviso', async () => {
    const bernal = {
      direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste',
      latitud: -34.7361,
      longitud: -58.3229,
      municipio: { id: 1, nombre: 'Quilmes' },
    }
    simularApi({
      ...conSesion(ana),
      'GET /api/reclamos': [],
      'GET /api/ubicaciones/sugerencias': { disponible: true, sugerencias: [bernal] },
      'POST /api/reclamos/candidatos-duplicados': {
        comparadoPorIA: true,
        motivo: 'Los dos hablan del mismo farol.',
        candidatos: [
        {
          numero: 'REC-AAAA1111',
          estado: 'EN_PROCESO',
          fechaCreacion: '2026-11-01T09:30:00',
          descripcion: 'El farol no prende',
          direccion: bernal.direccionNormalizada,
          foto: null,
          distanciaMetros: 12,
          cantidadApoyos: 0,
          apoyadoPorMi: false,
          propio: false,
          categoria: { id: 1, nombre: 'Luminaria rota' },
          masParecido: true,
        },
        ],
      },
      'POST /api/reclamos/REC-AAAA1111/apoyo': { numero: 'REC-AAAA1111', cantidadApoyos: 1, apoyado: true },
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Crear reclamo' }))
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(screen.getByRole('button', { name: 'Buscar en el mapa' }))
    await usuario.click(await screen.findByRole('radio', { name: /GRL BELGRANO 450, Bernal Oeste/ }))
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(within(screen.getByRole('main')).getByRole('button', { name: 'Crear reclamo' }))
    await usuario.click(await screen.findByRole('button', { name: 'Es el mismo: asociarme a REC-AAAA1111' }))

    expect(await screen.findByRole('heading', { name: 'Mis reclamos' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(
      'Te asociaste al reclamo REC-AAAA1111: te vamos a avisar cada vez que cambie.',
    )
  })

  it('el administrador ve en Administración los reclamos sin asignar y la cobertura de las áreas', async () => {
    const llamadas = simularApi({
      ...conSesion(elena),
      'GET /api/reclamos': (peticion) => (peticion.consulta.estado === 'INGRESADO' ? [reclamo({ estado: 'INGRESADO', area: null })] : []),
      'GET /api/usuarios': USUARIOS,
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Administración' }))

    expect(await screen.findByRole('heading', { name: 'Reclamos sin asignar' })).toBeInTheDocument()
    expect(await screen.findByText('Sin asignar')).toBeInTheDocument()
    // Cada municipio tiene sus áreas: hay dos "Obras Públicas", cada una con su municipio.
    expect(screen.getAllByRole('cell', { name: 'Obras Públicas' })).toHaveLength(2)
    expect(screen.getByRole('cell', { name: 'Avellaneda' })).toBeInTheDocument()
    expect(llamadas.some((llamada) => llamada.consulta?.estado === 'INGRESADO')).toBe(true)
  })

  it('el agente ve en Avisos lo que le notificaron y abre el reclamo desde ahí', async () => {
    const asignado = reclamo({ accionesDisponibles: ['EN_PROCESO'] })
    const llamadas = simularApi({
      ...conSesion(carla),
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
    expect(llamadas.find((llamada) => llamada.ruta === '/api/notificaciones').metodo).toBe('GET')

    await usuario.click(screen.getByRole('button', { name: 'REC-4F2A91BC' }))
    expect(await screen.findByRole('button', { name: 'Tomar reclamo' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Volver al listado' }))
    expect(await screen.findByRole('heading', { name: 'Avisos' })).toBeInTheDocument()
  })

  it('los avisos de un reclamo que el vecino apoya se leen pero no abren el detalle', async () => {
    simularApi({
      ...conSesion(ana),
      'GET /api/reclamos': [],
      'GET /api/notificaciones': [
        { numeroReclamo: 'REC-AAAA1111', canal: 'INTERNO', mensaje: 'El reclamo REC-AAAA1111 que apoyaste fue resuelto.', fechaEnvio: '2026-11-02T10:00:00', abrible: false },
        { numeroReclamo: 'REC-4F2A91BC', canal: 'INTERNO', mensaje: 'Tu reclamo REC-4F2A91BC fue ingresado.', fechaEnvio: '2026-11-01T10:00:00', abrible: true },
      ],
    })
    const usuario = userEvent.setup()
    render(<App />)

    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))

    expect(await screen.findByText('El reclamo REC-AAAA1111 que apoyaste fue resuelto.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'REC-AAAA1111' })).not.toBeInTheDocument()
    expect(screen.getByText('(lo apoyás)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'REC-4F2A91BC' })).toBeInTheDocument()
  })

  it('la bandeja vacía lo dice y un error del backend se muestra', async () => {
    simularApi({ ...conSesion(ana), 'GET /api/reclamos': [], 'GET /api/notificaciones': [] })
    const usuario = userEvent.setup()
    const { unmount } = render(<App />)
    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))
    expect(await screen.findByText('No tenés avisos.')).toBeInTheDocument()
    unmount()

    simularApi({
      ...conSesion(ana),
      'GET /api/reclamos': [],
      'GET /api/notificaciones': { status: 500, cuerpo: { status: 500, codigo: 'ERROR_INTERNO', mensaje: 'Ocurrio un error interno. Intentalo nuevamente.', detalles: [] } },
    })
    render(<App />)
    await usuario.click(await screen.findByRole('button', { name: 'Avisos' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ocurrio un error interno.')
  })

  it('avisa cuando el backend no responde y permite reintentar', async () => {
    simularApi({
      'GET /api/auth/yo': ana,
      'GET /api/categorias': { status: 502, cuerpo: null },
      'GET /api/municipios': [],
      'GET /api/areas': [],
    })
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no responde')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
