import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { BARRIOS, CATEGORIAS, TOKEN_CSRF, archivoFoto, reclamo, simularApi } from '../pruebas/apiFalsa.js'
import FormularioReclamo from './FormularioReclamo.jsx'

function mostrar(rutas = {}) {
  const llamadas = simularApi(rutas)
  const onCreado = vi.fn()
  render(<FormularioReclamo categorias={CATEGORIAS} barrios={BARRIOS} onCreado={onCreado} />)
  return { llamadas, onCreado, usuario: userEvent.setup({ applyAccept: false }) }
}

async function completar(usuario) {
  await usuario.selectOptions(screen.getByLabelText(/Categoría/), 'Luminaria rota')
  await usuario.selectOptions(screen.getByLabelText('Barrio'), 'Bernal')
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
