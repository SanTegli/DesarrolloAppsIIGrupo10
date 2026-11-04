import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import {
  BARRIOS,
  CATEGORIAS,
  TOKEN_CSRF,
  archivoFoto,
  reclamo,
  simularApi,
} from '../pruebas/apiFalsa.js'
import FormularioReclamo from './FormularioReclamo.jsx'

const BERNAL = {
  direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires',
  latitud: -34.7361,
  longitud: -58.3229,
}

function candidato(cambios = {}) {
  return {
    numero: 'REC-AAAA1111',
    estado: 'ASIGNADO',
    fechaCreacion: '2026-11-01T09:30:00',
    descripcion: 'El farol de la esquina no prende',
    direccion: 'GRL BELGRANO 450, Bernal Oeste',
    foto: '/api/reclamos/REC-AAAA1111/foto',
    distanciaMetros: 18,
    cantidadApoyos: 2,
    apoyadoPorMi: false,
    propio: false,
    ...cambios,
  }
}

function mostrar(rutas) {
  const llamadas = simularApi({
    'GET /api/ubicaciones/sugerencias': { disponible: true, sugerencias: [BERNAL] },
    ...rutas,
  })
  const onCreado = vi.fn()
  const onApoyado = vi.fn()
  render(<FormularioReclamo categorias={CATEGORIAS} barrios={BARRIOS} onCreado={onCreado} onApoyado={onApoyado} />)
  return { llamadas, onCreado, onApoyado, usuario: userEvent.setup() }
}

async function completarConElMapa(usuario) {
  await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
  await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
  await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
  await usuario.click(screen.getByRole('button', { name: 'Buscar en el mapa' }))
  await usuario.click(await screen.findByRole('radio', { name: BERNAL.direccionNormalizada }))
  await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
  await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
}

const crearBoton = () => screen.getByRole('button', { name: 'Crear reclamo' })

describe('Posibles duplicados en el alta', () => {
  it('antes de crear muestra los reclamos parecidos cercanos, sin el nombre del autor', async () => {
    const { llamadas, onCreado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': [candidato()],
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())

    const panel = await screen.findByRole('region', { name: '¿Es alguno de estos?' })
    expect(within(panel).getByText('REC-AAAA1111').closest('p')).toHaveTextContent('REC-AAAA1111 · Asignado · a 18 m')
    expect(within(panel).getByText('El farol de la esquina no prende')).toBeInTheDocument()
    expect(within(panel).getByText(/2 vecinos lo apoyan/)).toBeInTheDocument()
    expect(within(panel).getByRole('img', { name: 'Foto del reclamo REC-AAAA1111' })).toHaveAttribute(
      'src',
      '/api/reclamos/REC-AAAA1111/foto',
    )
    expect(crearBoton()).toBeDisabled()
    expect(onCreado).not.toHaveBeenCalled()
    expect(llamadas.find((llamada) => llamada.ruta === '/api/reclamos/candidatos-duplicados').consulta).toEqual({
      categoriaId: '1',
      latitud: String(BERNAL.latitud),
      longitud: String(BERNAL.longitud),
    })
    expect(llamadas.some((llamada) => llamada.metodo === 'POST')).toBe(false)
  })

  it('"Es el mismo: apoyar" suma el apoyo y no crea un reclamo', async () => {
    const { llamadas, onApoyado, onCreado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': [candidato()],
      'POST /api/reclamos/REC-AAAA1111/apoyo': { numero: 'REC-AAAA1111', cantidadApoyos: 3, apoyado: true },
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())
    await usuario.click(await screen.findByRole('button', { name: 'Es el mismo: apoyar REC-AAAA1111' }))

    expect(onApoyado).toHaveBeenCalledWith('REC-AAAA1111')
    expect(onCreado).not.toHaveBeenCalled()
    const apoyo = llamadas.find((llamada) => llamada.ruta === '/api/reclamos/REC-AAAA1111/apoyo')
    expect(apoyo).toMatchObject({ metodo: 'POST', csrf: TOKEN_CSRF })
    expect(llamadas.some((llamada) => llamada.ruta === '/api/reclamos' && llamada.metodo === 'POST')).toBe(false)
  })

  it('"Es otro problema: continuar" crea el reclamo y no vuelve a preguntar', async () => {
    const { llamadas, onCreado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': [candidato()],
      'POST /api/reclamos': { status: 201, cuerpo: reclamo() },
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())
    await usuario.click(await screen.findByRole('button', { name: 'Es otro problema: continuar' }))

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    const alta = llamadas.find((llamada) => llamada.ruta === '/api/reclamos' && llamada.metodo === 'POST')
    expect(alta.cuerpo.ubicacion).toEqual(BERNAL)
    expect(llamadas.filter((llamada) => llamada.ruta === '/api/reclamos/candidatos-duplicados')).toHaveLength(1)
  })

  it('un reclamo propio no se puede apoyar y uno ya apoyado se puede quitar', async () => {
    const { llamadas, onApoyado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': [
        candidato({ numero: 'REC-PROPIO01', propio: true, foto: null, cantidadApoyos: 0 }),
        candidato({ apoyadoPorMi: true, cantidadApoyos: 1 }),
      ],
      'DELETE /api/reclamos/REC-AAAA1111/apoyo': { numero: 'REC-AAAA1111', cantidadApoyos: 0, apoyado: false },
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())

    const panel = await screen.findByRole('region', { name: '¿Es alguno de estos?' })
    expect(within(panel).getByText('Es un reclamo tuyo.')).toBeInTheDocument()
    expect(within(panel).getByText('Sin foto')).toBeInTheDocument()
    expect(within(panel).getByText(/Nadie lo apoyó todavía/)).toBeInTheDocument()
    expect(within(panel).queryAllByRole('button', { name: /Es el mismo: apoyar/ })).toHaveLength(0)
    expect(within(panel).getByText('Ya lo apoyás')).toBeInTheDocument()

    await usuario.click(within(panel).getByRole('button', { name: 'Quitar apoyo' }))

    expect(await within(panel).findByRole('button', { name: 'Es el mismo: apoyar REC-AAAA1111' })).toBeInTheDocument()
    expect(llamadas.find((llamada) => llamada.metodo === 'DELETE')).toMatchObject({ csrf: TOKEN_CSRF })
    expect(onApoyado).not.toHaveBeenCalled()
  })

  it('sin punto en el mapa, o sin candidatos, crea directo', async () => {
    const { llamadas, onCreado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': [],
      'POST /api/reclamos': { status: 201, cuerpo: reclamo() },
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: '¿Es alguno de estos?' })).not.toBeInTheDocument()
    expect(llamadas.filter((llamada) => llamada.ruta === '/api/reclamos/candidatos-duplicados')).toHaveLength(1)
  })

  it('si la búsqueda de duplicados falla, el alta sigue', async () => {
    const { onCreado, usuario } = mostrar({
      'GET /api/reclamos/candidatos-duplicados': { status: 500, cuerpo: { status: 500, codigo: 'ERROR_INTERNO', mensaje: 'x', detalles: [] } },
      'POST /api/reclamos': { status: 201, cuerpo: reclamo() },
    })

    await completarConElMapa(usuario)
    await usuario.click(crearBoton())

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
  })
})
