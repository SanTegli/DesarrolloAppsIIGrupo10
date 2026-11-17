import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MUNICIPIOS, CATEGORIAS, TOKEN_CSRF, archivoFoto, reclamo, simularApi } from '../pruebas/apiFalsa.js'
import FormularioReclamo from './FormularioReclamo.jsx'

function mostrar(rutas = {}) {
  const llamadas = simularApi(rutas)
  const onCreado = vi.fn()
  render(<FormularioReclamo categorias={CATEGORIAS} municipios={MUNICIPIOS} onCreado={onCreado} />)
  return { llamadas, onCreado, usuario: userEvent.setup({ applyAccept: false }) }
}

async function completar(usuario) {
  await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
  await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
  await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
  await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
}

describe('FormularioReclamo: foto', () => {
  it('la foto es obligatoria y acepta solo JPEG o PNG', () => {
    mostrar()

    const campo = screen.getByLabelText('Foto del problema')
    expect(campo).toHaveAttribute('aria-required', 'true')
    expect(campo).toHaveAttribute('accept', 'image/jpeg,image/png')
    expect(screen.getByText(/No guardamos la ubicación/)).toBeInTheDocument()
  })

  it('manda el reclamo y la foto en un solo formulario multipart, sin Content-Type a mano', async () => {
    const { llamadas, onCreado, usuario } = mostrar({ 'POST /api/reclamos': { status: 201, cuerpo: reclamo() } })

    await completar(usuario)
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto({ nombre: 'poste.png', tipo: 'image/png' }))
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    const [alta] = llamadas
    expect(alta).toMatchObject({
      metodo: 'POST',
      csrf: TOKEN_CSRF,
      cuerpo: { multipart: true, categoriaId: 1, foto: { nombre: 'poste.png', tipo: 'image/png' } },
    })
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
  })

  it('sin foto no envía nada y pide elegir una', async () => {
    const { llamadas, usuario } = mostrar()

    await completar(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Elegí una foto del problema: es obligatoria.')
    expect(llamadas).toHaveLength(0)
  })

  it('rechaza otros formatos antes de subirlos', async () => {
    const { usuario } = mostrar()

    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto({ nombre: 'farol.gif', tipo: 'image/gif' }))

    expect(screen.getByRole('alert')).toHaveTextContent('La foto tiene que ser JPEG o PNG.')
    expect(screen.getByLabelText('Foto del problema').files).toHaveLength(0)
  })

  it('rechaza una foto de más de 5 MB antes de subirla', async () => {
    const { usuario } = mostrar()

    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto({ tamano: 5 * 1024 * 1024 + 1 }))

    expect(screen.getByRole('alert')).toHaveTextContent('La foto pesa más de 5 MB')
  })

  it('muestra el error del backend si la foto no es una imagen real', async () => {
    const { usuario } = mostrar({
      'POST /api/reclamos': {
        status: 400,
        cuerpo: { status: 400, codigo: 'DATOS_INVALIDOS', mensaje: 'El archivo no es una imagen JPEG o PNG válida.', detalles: [] },
      },
    })

    await completar(usuario)
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El archivo no es una imagen JPEG o PNG válida.')
    expect(screen.getByRole('button', { name: 'Crear reclamo' })).toBeEnabled()
  })
})

describe('FormularioReclamo: municipio', () => {
  it('la dirección elegida en el mapa define el municipio, aunque sea otro', async () => {
    const wilde = {
      direccionNormalizada: 'BELGRANO 450, Wilde, Avellaneda, Buenos Aires',
      latitud: -34.701,
      longitud: -58.318,
      municipio: { id: 2, nombre: 'Avellaneda' },
    }
    const { llamadas, onCreado, usuario } = mostrar({
      'GET /api/ubicaciones/sugerencias': { disponible: true, sugerencias: [wilde] },
      'POST /api/reclamos/candidatos-duplicados': { comparadoPorIA: false, motivo: null, candidatos: [] },
      'POST /api/reclamos': { status: 201, cuerpo: reclamo() },
    })

    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.click(screen.getByRole('button', { name: 'Buscar en el mapa' }))
    await usuario.click(await screen.findByRole('radio', { name: /Wilde/ }))
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Farol apagado')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    expect(llamadas.find((llamada) => llamada.ruta === '/api/reclamos').cuerpo.municipioId).toBe(2)
  })

  it('si no se elige una dirección del mapa, el municipio se elige de la lista y es obligatorio', async () => {
    const { usuario } = mostrar()

    const municipio = screen.getByLabelText('Municipio')
    expect(municipio).toBeRequired()
    expect(screen.getAllByRole('option').map((opcion) => opcion.textContent)).toEqual(
      expect.arrayContaining(['Quilmes', 'Avellaneda']),
    )
    await usuario.selectOptions(municipio, 'Avellaneda')
    expect(municipio).toHaveValue('2')
  })
})

describe('FormularioReclamo: otro problema', () => {
  it('"Otro problema" va al final de la lista', () => {
    mostrar()

    const opciones = within(screen.getByLabelText(/Categoría/)).getAllByRole('option').map((opcion) => opcion.textContent)
    expect(opciones).toEqual(['Elegí una categoría', 'Luminaria rota', 'Bache', 'Vereda rota', 'Otro problema'])
  })

  it('con "Otro problema" pide qué tipo de problema es y lo manda en el alta', async () => {
    const { llamadas, onCreado, usuario } = mostrar({ 'POST /api/reclamos': { status: 201, cuerpo: reclamo() } })

    expect(screen.queryByLabelText('¿Qué tipo de problema es?')).not.toBeInTheDocument()
    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Otro problema')
    const propuesta = screen.getByLabelText('¿Qué tipo de problema es?')
    expect(propuesta).toBeRequired()
    expect(propuesta).toHaveAttribute('maxLength', '80')
    await usuario.type(propuesta, '  Baldosas levantadas ')
    await usuario.selectOptions(screen.getByLabelText('Municipio'), 'Quilmes')
    await usuario.type(screen.getByLabelText('Dirección'), 'Belgrano 450')
    await usuario.type(screen.getByLabelText(/Descripción del problema/), 'Baldosas sueltas')
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    expect(llamadas[0].cuerpo).toMatchObject({ categoriaId: 8, categoriaPropuesta: 'Baldosas levantadas' })
  })

  it('con otra categoría no manda propuesta', async () => {
    const { llamadas, onCreado, usuario } = mostrar({ 'POST /api/reclamos': { status: 201, cuerpo: reclamo() } })

    await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Otro problema')
    await usuario.type(screen.getByLabelText('¿Qué tipo de problema es?'), 'Algo')
    await completar(usuario)
    await usuario.upload(screen.getByLabelText('Foto del problema'), archivoFoto())
    await usuario.click(screen.getByRole('button', { name: 'Crear reclamo' }))

    await vi.waitFor(() => expect(onCreado).toHaveBeenCalled())
    expect(llamadas[0].cuerpo.categoriaPropuesta).toBeUndefined()
  })
})

