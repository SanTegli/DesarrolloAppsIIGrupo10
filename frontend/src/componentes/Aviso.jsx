/** Muestra un error de la API: el mensaje y, si los hay, los detalles por campo. */
export function AvisoError({ error }) {
  if (!error) {
    return null
  }
  return (
    <div className="aviso aviso-error" role="alert">
      <p>{error.message}</p>
      {error.detalles?.length > 0 && (
        <ul>
          {error.detalles.map((detalle) => (
            <li key={detalle}>{detalle}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function AvisoExito({ children }) {
  return children ? (
    <div className="aviso aviso-exito" role="status">
      <p>{children}</p>
    </div>
  ) : null
}
