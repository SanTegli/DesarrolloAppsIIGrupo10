// Capturas de pantalla del sistema real para las evidencias (docs/evidencias/sistema-real/).
//
// Recorre la interfaz con los usuarios de la semilla, como en el guion de la demo, y saca una captura en cada
// paso. También captura las consolas web de los otros servicios: Swagger de REST 2, el WSDL, Mailpit y RabbitMQ.
// Al terminar escribe indice.md con cada captura, el caso de prueba que ilustra y la hora.
//
// Con la demo levantada (docker compose up --build) y la clave de Gemini en .env, desde scripts/capturas/:
//   npm install
//   npx playwright install chromium
//   node capturas-evidencias.mjs
//
// Opciones: --app http://localhost:5173 (la interfaz), --salida <carpeta> (por defecto docs/evidencias/sistema-real),
// --ver (abre el navegador en pantalla, para seguir el recorrido).
//
// Crea datos de prueba: dos reclamos de Ana, uno de Bruno y una cuenta nueva por corrida. Bloquea a Bruno y lo
// desbloquea al final. Cada sección corre aparte: si una falla, se guarda error-<sección>.png y sigue la próxima.
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ = resolve(AQUI, '..', '..')
export const FOTO = join(RAIZ, 'docs', 'evidencias', 'fotos', 'farol-ejemplo.jpg')
const CONTRASENA = 'demo1234'
const DIRECCION = 'Belgrano 450'

export function configuracion(argumentos = process.argv.slice(2), entorno = process.env) {
  const opcion = (nombre) => {
    const indice = argumentos.indexOf(`--${nombre}`)
    return indice >= 0 ? argumentos[indice + 1] : undefined
  }
  const sinBarra = (url) => url.replace(/\/$/, '')
  return {
    app: sinBarra(opcion('app') ?? entorno.CAPTURAS_APP ?? 'http://localhost:5173'),
    prioridad: sinBarra(opcion('prioridad') ?? entorno.CAPTURAS_PRIORIDAD ?? 'http://localhost:8082'),
    cobertura: sinBarra(opcion('cobertura') ?? entorno.CAPTURAS_COBERTURA ?? 'http://localhost:8081'),
    mailpit: sinBarra(opcion('mailpit') ?? entorno.CAPTURAS_MAILPIT ?? 'http://localhost:8025'),
    rabbit: sinBarra(opcion('rabbit') ?? entorno.CAPTURAS_RABBIT ?? 'http://localhost:15672'),
    rabbitUsuario: entorno.RABBITMQ_USER ?? 'reclamos',
    rabbitContrasena: entorno.RABBITMQ_PASSWORD ?? 'reclamos_dev',
    salida: resolve(opcion('salida') ?? join(RAIZ, 'docs', 'evidencias', 'sistema-real')),
    ver: argumentos.includes('--ver'),
  }
}

/** Lo que muestra cada captura y qué caso de docs/evidencias/README.md ilustra. */
export const CAPTURAS = {
  '01-ingreso': ['CP41', 'Pantalla de ingreso del sistema real'],
  '02-login-fallido': ['CP42', 'Login con contraseña incorrecta: el mismo error que con un email inexistente'],
  '03-registro': ['CP45', 'Registro de un vecino nuevo, antes de enviar'],
  '04-verificacion-codigo': ['CP47', 'Cuenta nueva: el código de verificación en modo simulado'],
  '05-verificacion-confirmada': ['CP47', 'Teléfono confirmado: ya puede crear reclamos'],
  '06-alta-sugerencias-georef': ['CP27, CP53', 'Alta con las sugerencias reales de Georef y la vista previa de la foto'],
  '07-alta-foto-rechazada': ['CP54', 'Un archivo que no es JPEG ni PNG se rechaza con 400'],
  '08-detalle-ia-maps-foto': ['CP23, CP28, CP53', 'Reclamo creado: prioridad con origen IA, enlace a Google Maps y foto'],
  '09-posible-duplicado': ['CP58', 'Bruno ya reclamó lo mismo: el alta de Ana lo muestra sin datos del autor'],
  '10-apoyo-registrado': ['CP60', 'Ana apoya el reclamo de Bruno en lugar de crear otro'],
  '11-apoyo-contador': ['CP60', 'Detalle del reclamo de Bruno con el contador de apoyos'],
  '12-agente-listado': ['CP12', 'Carla ve solo los reclamos de su área'],
  '13-agente-en-proceso': ['CP08', 'Carla tomó el reclamo y escribe la observación para resolverlo'],
  '14-ciudadano-reabre': ['CP52', 'Ana reabre el reclamo resuelto con un motivo'],
  '15-ciudadano-cerrado': ['CP08, CP52', 'Ana confirma la segunda solución: historial completo y la reapertura ya no se ofrece'],
  '16-avisos': ['CP14', 'Avisos que recibió Ana'],
  '17-admin-sin-asignar': ['CP06, CP18', 'Luminaria en Avellaneda: ningún área de ese municipio la cubre y queda para la administración'],
  '18-admin-asignacion-manual': ['CP06', 'Elena asigna el área a mano'],
  '19-admin-cuenta-bloqueada': ['CP51', 'Elena bloquea la cuenta de Bruno'],
  '20-ciudadano-bloqueado': ['CP51', 'Bruno, bloqueado, no puede crear reclamos'],
  '21-tablero-publico': ['CP63', 'Tablero público, sin iniciar sesión'],
  '22-tablero-gestion': ['CP65', 'Tablero de gestión de la administradora'],
  '23-swagger-rest2-gemini': ['CP22', 'Swagger de REST 2: una evaluación real con Gemini'],
  '24-wsdl': ['CP16', 'WSDL del servicio de cobertura'],
  '25-mailpit': ['CP33', 'Correos de los avisos en Mailpit'],
  '26-rabbitmq-exchanges': ['CP33', 'Exchanges en la consola de RabbitMQ'],
  '27-rabbitmq-colas': ['CP33, CP36', 'Colas de RabbitMQ, con la cola de errores'],
}

export async function ejecutar(config, { chromium, registro = console } = {}) {
  if (!chromium) {
    ;({ chromium } = await import('playwright'))
  }
  try {
    await fetch(config.app + '/', { signal: AbortSignal.timeout(5000) })
  } catch {
    throw new Error(`La interfaz no responde en ${config.app}: ¿está levantado docker compose up?`)
  }
  mkdirSync(config.salida, { recursive: true })
  const navegador = await chromium.launch({ headless: !config.ver })
  const tomadas = []
  const fallas = []
  const contextos = []

  async function pagina() {
    const contexto = await navegador.newContext({
      viewport: { width: 1280, height: 900 },
      locale: 'es-AR',
      timezoneId: 'America/Argentina/Buenos_Aires',
    })
    contextos.push(contexto)
    return contexto.newPage()
  }

  async function captura(p, nombre, completa = true) {
    await p.screenshot({ path: join(config.salida, `${nombre}.png`), fullPage: completa })
    tomadas.push({ nombre, hora: new Date() })
    registro.log(`  ✓ ${nombre}`)
  }

  async function seccion(nombre, pasos) {
    registro.log(`${nombre}`)
    try {
      await pasos()
    } catch (error) {
      fallas.push({ seccion: nombre, mensaje: error.message.split('\n')[0] })
      registro.log(`  ✗ ${error.message.split('\n')[0]}`)
      const abiertas = contextos.flatMap((contexto) => contexto.pages())
      const ultima = abiertas[abiertas.length - 1]
      if (ultima) {
        const archivo = `error-${nombre.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}.png`
        await ultima.screenshot({ path: join(config.salida, archivo), fullPage: true }).catch(() => {})
      }
    }
  }

  const pestana = (p, nombre) => p.getByRole('navigation').getByRole('button', { name: nombre, exact: true }).click()

  async function entrar(p, email) {
    await p.goto(config.app + '/')
    await p.getByLabel('Correo electrónico').fill(email)
    await p.getByLabel('Contraseña').fill(CONTRASENA)
    await p.getByRole('button', { name: 'Entrar' }).click()
    await p.getByRole('navigation').waitFor()
  }

  /**
   * Completa el alta. Con buscar, busca la dirección en Georef (en todos los municipios) y elige la primera
   * sugerencia del municipio pedido: el municipio sale de ahí. Sin buscar, lo elige de la lista.
   */
  async function completarAlta(p, { categoria, municipio, descripcion, foto = FOTO, buscar = true }) {
    await pestana(p, 'Crear reclamo')
    await p.getByLabel(/Categoría/).selectOption({ label: categoria })
    await p.getByLabel('Dirección').fill(DIRECCION)
    let elegida = false
    if (buscar) {
      await p.getByRole('button', { name: 'Buscar en el mapa' }).click()
      const opciones = p.getByRole('radio')
      await opciones.first().waitFor({ timeout: 15000 })
      const delMunicipio = p.getByRole('radio', { name: new RegExp(`·\\s*${municipio}\\s*$`) }).first()
      if (await delMunicipio.count()) {
        await delMunicipio.check()
        elegida = true
      }
    }
    if (!elegida) {
      await p.getByLabel('Municipio').selectOption({ label: municipio })
    }
    await p.getByLabel(/Descripción del problema/).fill(descripcion)
    await p.getByLabel('Foto del problema').setInputFiles(foto)
  }

  /**
   * Toca Crear reclamo. Si aparecen posibles duplicados, sigue con "Es otro problema" salvo que se pida otra
   * cosa. Devuelve el número del reclamo creado.
   */
  async function crear(p, { siHayDuplicados } = {}) {
    await p.getByRole('main').getByRole('button', { name: 'Crear reclamo' }).click()
    const panel = p.getByRole('region', { name: '¿Es alguno de estos?' })
    const creado = p.getByText(/^Reclamo creado/)
    await Promise.race([panel.waitFor({ timeout: 20000 }), creado.waitFor({ timeout: 20000 })])
    if (await panel.isVisible()) {
      if (siHayDuplicados) {
        return siHayDuplicados(panel)
      }
      await p.getByRole('button', { name: 'Es otro problema: continuar' }).click()
      await creado.waitFor({ timeout: 20000 })
    }
    return numeroActual(p)
  }

  async function numeroActual(p) {
    const titulo = await p.getByRole('heading', { level: 2 }).first().textContent()
    const numero = titulo.match(/REC-[0-9A-F]{8}/)
    if (!numero) {
      throw new Error(`No se encontró el número del reclamo en "${titulo}"`)
    }
    return numero[0]
  }

  async function abrirReclamo(p, numero) {
    await pestana(p, 'Reclamos')
    await p.getByRole('button', { name: numero }).first().click()
    await p.getByRole('heading', { level: 2, name: new RegExp(numero) }).waitFor()
  }

  const esperarFoto = (p) =>
    p.waitForFunction(() => document.querySelector('.foto-reclamo img')?.naturalWidth > 0, null, { timeout: 15000 })

  const sello = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')
  const estado = {}

  await seccion('Ingreso, registro y verificación', async () => {
    const p = await pagina()
    await p.goto(config.app + '/')
    await p.getByLabel('Correo electrónico').waitFor()
    await captura(p, '01-ingreso')
    await p.getByLabel('Correo electrónico').fill('ana@example.org')
    await p.getByLabel('Contraseña').fill('no-es-la-contrasena')
    await p.getByRole('button', { name: 'Entrar' }).click()
    await p.getByRole('alert').waitFor()
    await captura(p, '02-login-fallido')
    await p.getByRole('button', { name: 'Crear una cuenta' }).click()
    await p.getByLabel('DNI').fill(`4${sello.slice(-7)}`)
    await p.getByLabel('Nombre').fill('Laura')
    await p.getByLabel('Apellido').fill('Martínez')
    await p.getByLabel('Correo electrónico').fill(`laura.${sello}@example.org`)
    await p.getByLabel(/Contraseña/).fill('una-clave-larga')
    await captura(p, '03-registro')
    await p.getByRole('button', { name: 'Crear cuenta y entrar' }).click()
    await pestana(p, 'Crear reclamo')
    await p.getByRole('button', { name: 'Pedir código de verificación' }).click()
    await p.locator('.codigo-verificacion').waitFor()
    const telefono = p.getByLabel('Teléfono')
    if (!(await telefono.isVisible())) {
      await captura(p, '04-verificacion-codigo')
      throw new Error('El backend está en modo telegram: la verificación de la cuenta nueva se confirma con el bot (CP49, a mano)')
    }
    await telefono.fill(`+54 9 11 ${sello.slice(-4)}-${sello.slice(-8, -4)}`)
    await captura(p, '04-verificacion-codigo')
    await p.getByRole('button', { name: 'Confirmar teléfono' }).click()
    await p.getByLabel(/Categoría/).waitFor()
    await captura(p, '05-verificacion-confirmada')
  })

  await seccion('Alta con Georef, IA y foto', async () => {
    const p = await pagina()
    estado.ana = p
    await entrar(p, 'ana@example.org')
    await completarAlta(p, {
      categoria: 'Luminaria rota',
      municipio: 'Quilmes',
      descripcion: `Evidencia ${sello}: salen chispas del farol de la esquina y la cuadra queda a oscuras.`,
    })
    await p.getByRole('img', { name: 'Vista previa de la foto elegida' }).waitFor()
    await captura(p, '06-alta-sugerencias-georef')

    // Un archivo con extensión .jpg que no es una imagen: el backend lo rechaza al re-codificarlo.
    await p.getByLabel('Foto del problema').setInputFiles({
      name: 'no-es-una-foto.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from('esto no es una imagen'),
    })
    await p.getByRole('main').getByRole('button', { name: 'Crear reclamo' }).click()
    const panel = p.getByRole('region', { name: '¿Es alguno de estos?' })
    await Promise.race([panel.waitFor({ timeout: 20000 }), p.getByRole('alert').waitFor({ timeout: 20000 })])
    if (await panel.isVisible()) {
      await p.getByRole('button', { name: 'Es otro problema: continuar' }).click()
    }
    await p.getByRole('alert').waitFor({ timeout: 20000 })
    await captura(p, '07-alta-foto-rechazada')

    await p.getByLabel('Foto del problema').setInputFiles(FOTO)
    await p.getByRole('img', { name: 'Vista previa de la foto elegida' }).waitFor()
    estado.luminaria = await crear(p)
    await esperarFoto(p)
    // Si la IA no respondió en el alta, el detalle se actualiza solo cuando termina la revisión por la cola.
    await p
      .getByText(/la IA lo está revisando/)
      .waitFor({ state: 'detached', timeout: 90000 })
      .catch(() => {})
    await captura(p, '08-detalle-ia-maps-foto')
  })

  await seccion('Posibles duplicados y apoyos', async () => {
    const bruno = await pagina()
    estado.bruno = bruno
    await entrar(bruno, 'bruno@example.org')
    await completarAlta(bruno, {
      categoria: 'Bache',
      municipio: 'Quilmes',
      descripcion: `Evidencia ${sello}: pozo grande en la esquina, los autos frenan de golpe para esquivarlo.`,
    })
    estado.bache = await crear(bruno)
    const ana = estado.ana ?? (await pagina())
    if (!estado.ana) {
      await entrar(ana, 'ana@example.org')
      estado.ana = ana
    }
    await completarAlta(ana, {
      categoria: 'Bache',
      municipio: 'Quilmes',
      descripcion: 'Hay un bache enorme en la esquina de la plaza.',
    })
    await crear(ana, {
      siHayDuplicados: async (panel) => {
        const tarjeta = panel.locator('.tarjeta-duplicado', { hasText: estado.bache })
        await tarjeta.waitFor()
        await ana.waitForFunction(
          (numero) => [...document.querySelectorAll('.tarjeta-duplicado img')].some((img) => img.alt.includes(numero) && img.naturalWidth > 0),
          estado.bache,
          { timeout: 15000 },
        ).catch(() => {})
        await tarjeta.scrollIntoViewIfNeeded()
        await captura(ana, '09-posible-duplicado', false)
        await ana.getByRole('button', { name: `Es el mismo: apoyar ${estado.bache}` }).click()
        await ana.getByText(/Sumaste tu apoyo/).waitFor()
        await captura(ana, '10-apoyo-registrado')
      },
    })
    await bruno.reload()
    await abrirReclamo(bruno, estado.bache)
    await bruno.getByText(/vecinos? apoyan? este reclamo/).first().waitFor()
    await captura(bruno, '11-apoyo-contador')
  })

  await seccion('Flujo del agente y del ciudadano', async () => {
    if (!estado.luminaria) {
      throw new Error('Falta el reclamo de la sección "Alta con Georef, IA y foto"')
    }
    const carla = await pagina()
    await entrar(carla, 'carla@example.org')
    await carla.getByRole('button', { name: estado.luminaria }).first().waitFor()
    await captura(carla, '12-agente-listado')
    await abrirReclamo(carla, estado.luminaria)
    await carla.getByRole('button', { name: 'Tomar reclamo' }).click()
    await carla.getByRole('button', { name: 'Marcar como resuelto' }).waitFor()
    await carla.getByLabel(/Observación/).fill('Se cambió la lámpara del farol.')
    await esperarFoto(carla)
    await captura(carla, '13-agente-en-proceso')
    await carla.getByRole('button', { name: 'Marcar como resuelto' }).click()
    await carla.getByText(/pasó a Resuelto/).waitFor()

    const ana = estado.ana
    await ana.reload()
    await abrirReclamo(ana, estado.luminaria)
    await ana.getByLabel(/Observación/).fill('Siguen saliendo chispas cuando llueve.')
    await captura(ana, '14-ciudadano-reabre')
    await ana.getByRole('button', { name: 'Reabrir' }).click()
    await ana.getByText(/pasó a En proceso/).waitFor()

    await carla.reload()
    await abrirReclamo(carla, estado.luminaria)
    await carla.getByLabel(/Observación/).fill('Se reemplazó el fotocontrol y el cableado de la base.')
    await carla.getByRole('button', { name: 'Marcar como resuelto' }).click()
    await carla.getByText(/pasó a Resuelto/).waitFor()

    await ana.reload()
    await abrirReclamo(ana, estado.luminaria)
    await ana.getByRole('button', { name: 'Confirmar solución' }).click()
    await ana.getByText(/pasó a Cerrado/).waitFor()
    await esperarFoto(ana)
    await captura(ana, '15-ciudadano-cerrado')
    await pestana(ana, 'Avisos')
    await ana.getByRole('heading', { name: /Avisos/ }).first().waitFor()
    await captura(ana, '16-avisos')
  })

  await seccion('Administración', async () => {
    const ana = estado.ana ?? (await pagina())
    if (!estado.ana) {
      await entrar(ana, 'ana@example.org')
    }
    await completarAlta(ana, {
      categoria: 'Luminaria rota',
      municipio: 'Avellaneda',
      descripcion: `Evidencia ${sello}: el farol de la esquina está apagado hace una semana.`,
      buscar: false,
    })
    estado.residuos = await crear(ana)

    const elena = await pagina()
    await entrar(elena, 'elena@example.org')
    await pestana(elena, 'Administración')
    await elena.getByRole('button', { name: estado.residuos }).first().waitFor()
    await captura(elena, '17-admin-sin-asignar')
    await elena.getByRole('button', { name: estado.residuos }).first().click()
    const selector = elena.locator('form.asignacion select')
    await selector.waitFor()
    const opciones = await selector.locator('option').allTextContents()
    await selector.selectOption({ label: opciones.includes('Obras Públicas') ? 'Obras Públicas' : opciones[1] })
    await elena.getByLabel(/Observación/).fill('Avellaneda no tiene Alumbrado: lo toma Obras Públicas de Avellaneda.')
    await elena.locator('form.asignacion button[type="submit"]').click()
    await elena.getByText(/quedó asignado a/).first().waitFor()
    await captura(elena, '18-admin-asignacion-manual')

    await pestana(elena, 'Administración')
    const bloquear = elena.getByRole('button', { name: 'Bloquear a Bruno Díaz' })
    const desbloquear = elena.getByRole('button', { name: 'Desbloquear a Bruno Díaz' })
    await Promise.race([bloquear.waitFor(), desbloquear.waitFor()])
    if (await bloquear.isVisible()) {
      await bloquear.click()
    }
    await desbloquear.waitFor()
    await desbloquear.scrollIntoViewIfNeeded()
    await captura(elena, '19-admin-cuenta-bloqueada', false)

    // Con la sesión ya abierta, recargar alcanza para que la interfaz vea la cuenta bloqueada.
    const bruno = estado.bruno ?? (await pagina())
    if (estado.bruno) {
      await bruno.reload()
      await bruno.getByRole('navigation').waitFor()
    } else {
      await entrar(bruno, 'bruno@example.org')
    }
    await pestana(bruno, 'Crear reclamo')
    await bruno.getByText(/bloquead/i).first().waitFor()
    await captura(bruno, '20-ciudadano-bloqueado')

    await desbloquear.click()
    await bloquear.waitFor()
    estado.elena = elena
  })

  await seccion('Tableros', async () => {
    const visitante = await pagina()
    await visitante.goto(config.app + '/')
    await visitante.getByRole('button', { name: 'Ver el tablero público, sin iniciar sesión' }).click()
    await visitante.getByText('Creados en el período').waitFor()
    await captura(visitante, '21-tablero-publico')
    const elena = estado.elena ?? (await pagina())
    if (!estado.elena) {
      await entrar(elena, 'elena@example.org')
    }
    await pestana(elena, 'Tablero')
    await elena.getByRole('heading', { name: 'Tablero de gestión' }).waitFor()
    await elena.getByRole('table').waitFor()
    await captura(elena, '22-tablero-gestion')
  })

  await seccion('Swagger de REST 2 con Gemini', async () => {
    const p = await pagina()
    await p.goto(config.prioridad + '/swagger-ui.html', { timeout: 15000 })
    await p.locator('.opblock-summary').first().click()
    await p.getByRole('button', { name: 'Try it out' }).click()
    await p.getByRole('button', { name: 'Execute' }).click()
    await p.locator('.live-responses-table .response-col_status').first().waitFor({ timeout: 30000 })
    await p.locator('.live-responses-table').scrollIntoViewIfNeeded()
    await captura(p, '23-swagger-rest2-gemini')
  })

  await seccion('WSDL de cobertura', async () => {
    const p = await pagina()
    const respuesta = await p.goto(config.cobertura + '/ws/cobertura.wsdl', { timeout: 15000 })
    if (!respuesta?.ok()) {
      throw new Error(`El WSDL respondió ${respuesta?.status()}`)
    }
    await captura(p, '24-wsdl', false)
  })

  await seccion('Mailpit', async () => {
    const p = await pagina()
    await p.goto(config.mailpit + '/', { timeout: 15000 })
    await p.getByText(/REC-[0-9A-F]{8}/).first().waitFor({ timeout: 30000 })
    await captura(p, '25-mailpit', false)
  })

  await seccion('Consola de RabbitMQ', async () => {
    const p = await pagina()
    await p.goto(config.rabbit + '/', { timeout: 15000 })
    await p.locator('input[name="username"]').fill(config.rabbitUsuario)
    await p.locator('input[name="password"]').fill(config.rabbitContrasena)
    await p.locator('input[type="submit"], button[type="submit"]').first().click()
    await p.goto(config.rabbit + '/#/exchanges')
    await p.getByText('reclamos.eventos').first().waitFor({ timeout: 15000 })
    await captura(p, '26-rabbitmq-exchanges')
    await p.goto(config.rabbit + '/#/queues')
    await p.getByText('notificaciones.email.dlq').first().waitFor({ timeout: 15000 })
    await captura(p, '27-rabbitmq-colas')
  })

  await navegador.close()
  writeFileSync(join(config.salida, 'indice.md'), indice(tomadas, fallas, config), 'utf8')
  registro.log(`\n${tomadas.length} capturas en ${config.salida}` + (fallas.length ? `, ${fallas.length} secciones con fallas` : ''))
  return { tomadas, fallas, estado }
}

/** indice.md: qué captura ilustra qué caso. */
export function indice(tomadas, fallas, config, fecha = new Date()) {
  const hora = (valor) => valor.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  const lineas = [
    '# Capturas del sistema real',
    '',
    `Tomadas el ${fecha.toLocaleDateString('es-AR')} con \`scripts/capturas/capturas-evidencias.mjs\` contra ` +
      `\`${config.app}\`, con la demo levantada con Docker Compose. Los casos están en [evidencias](../README.md).`,
    '',
    '| Captura | Caso | Qué muestra | Hora |',
    '| --- | --- | --- | --- |',
    ...tomadas.map(({ nombre, hora: momento }) => {
      const [caso, texto] = CAPTURAS[nombre] ?? ['', '']
      return `| [${nombre}](${nombre}.png) | ${caso} | ${texto} | ${hora(momento)} |`
    }),
  ]
  if (fallas.length > 0) {
    lineas.push('', '## Secciones que no se pudieron capturar', '')
    for (const falla of fallas) {
      lineas.push(`- **${falla.seccion}:** ${falla.mensaje}`)
    }
  }
  return lineas.join('\n') + '\n'
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  ejecutar(configuracion())
    .then(({ fallas }) => {
      process.exitCode = fallas.length ? 1 : 0
    })
    .catch((error) => {
      console.error(`Las capturas no pudieron empezar: ${error.message}`)
      process.exitCode = 1
    })
}
