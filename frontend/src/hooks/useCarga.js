import { useCallback, useEffect, useState } from 'react'

/**
 * Carga datos de la API y expone { datos, error, cargando, recargar }.
 * Descarta las respuestas que llegan después de que cambiaron las dependencias.
 */
export function useCarga(cargar, dependencias) {
  const [estado, setEstado] = useState({ datos: null, error: null, cargando: true })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let vigente = true
    setEstado((anterior) => ({ ...anterior, error: null, cargando: true }))
    cargar().then(
      (datos) => vigente && setEstado({ datos, error: null, cargando: false }),
      (error) => vigente && setEstado({ datos: null, error, cargando: false }),
    )
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencias, version])

  const recargar = useCallback(() => setVersion((v) => v + 1), [])
  return { ...estado, recargar }
}
