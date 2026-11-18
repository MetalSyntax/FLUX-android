# Flux — pendientes (2026-11-17)

- [x] Ícono nuevo (v2): como el logo web — libro de tapa cuadrada, lomo fino a la derecha, broche con botón, F con la parte de arriba ondulada; trazo blanco estilo Parity/Vitreon, fondo degradado verde `#00C08B → #006B4D`, escala 0.84 por zona segura.
- [x] Instalado en TECNO por USB (2026-11-17) + APK copiado a /sdcard/Download (ADB Wi-Fi no llega: el teléfono no es visible desde la Mac).
- [ ] Ver el ícono en el launcher real (formas circular/cuadrada y tema monocromo de Android 13+).
- [ ] Commit (sin git en esta carpeta).

## 2026-11-17 noche
- [x] Crash al abrir Perfil: `Color(Long.toULong())` en el selector de acento y el tema → `Color(Long)` (verificado en el TECNO, Perfil abre).
- [x] Tema Oscuro más claro (fondos #151C1B/#1E2725, texto atenuado #C4CCCA, bordes 16 %) y barra inferior con superficie gris, borde visible, píldora en el ítem activo y etiquetas 12 sp. «Estadísticas» → «Stats» en la barra (como la web en inglés).
- [ ] Revisar Biblioteca con libros y el lector con los temas nuevos.

## Descubrir (Gutenberg) lento / "se queda leyendo" — 2026-11-17
Causa medida: gutendex.com/books cuelga >45 s con consultas no cacheadas (search=, mime_type=, topic combinado; la app mandaba mime_type siempre) y www.gutenberg.org estaba en mantenimiento (503) hasta 18-nov 21:00 UTC; el único mirror que sirve EPUB (gutenberg.pglaf.org) va a ~19 KB/s.
Cambios: timeouts 10 s en catálogo y 8 s conexión/25 s socket en descargas; sin mime_type (filtro EPUB en cliente); fallback Gutendex -> OPDS oficial -> caché vencida; caché memoria+disco (6 h, cacheDir/catalog); paginación ("Cargar más", Gutendex); descarga con lista de mirrors (www ebooks, cache/epub, pglaf) y progreso %; tests en test/.../data/remote/CatalogSourcesTest.kt.
Pendiente: probar en el dispositivo; el parser OPDS no se pudo validar contra el feed real (gutenberg.org caído), revisar cuando vuelva; portadas siguen en www.gutenberg.org (sin fallback); OPDS sin paginación; Standard Ebooks requiere auth (401), descartado.

## Fuentes instalables (reemplazo de Gutenberg fijo) — 2026-11-18
- [x] Descubrir arranca sin catálogos; botón «Fuentes»: agregar repositorio por URL (JSON), instalar/quitar fuentes, quitar repositorio (quita sus fuentes). Guardado en filesDir/sources.json.
- [x] Motores: `gutendex` (Gutenberg, con fallback OPDS y mirrors), `openlibrary` (solo dominio público, EPUB de archive.org), `opds` (feed OPDS 1.x genérico, búsqueda con plantilla `{q}`). Las fuentes son datos validados, nunca código.
- [x] Repo de ejemplo: `repos/flux-sources.json` (Gutenberg + Open Library) → tras el push: https://raw.githubusercontent.com/MetalSyntax/FLUX-android/main/repos/flux-sources.json
- [x] Tests: BookSourcesTest (validación de repo, store, OPDS, Open Library). Build debug OK.
- [ ] Probar en el TECNO (no había dispositivo por USB).
- [ ] Motor OPDS sin validar contra un feed real (Gutenberg 503 y Feedbooks caído el 18-nov).
- [ ] Las fuentes no van en el respaldo .zip (hay que volver a agregar el repo tras restaurar).

## Funciones tomadas de Episteme — 2026-11-18
- [x] Formatos TXT, Markdown, HTML y FB2: se convierten a EPUB al importar (TextToEpub; TXT detecta capítulos y Windows-1252; HTML sin scripts/on*; FB2 con autor y portada).
- [x] Lectura en voz alta (TTS del sistema) en EPUB: párrafo a párrafo, resalta y sigue el párrafo, pasa de capítulo solo, velocidad 0.8–2x.
- [x] Desplazamiento automático en EPUB con velocidad ajustable; pasa de capítulo al llegar al final. Pantalla encendida mientras lee/desplaza.
- [x] Sincronizar carpeta: ícono de carpeta en Biblioteca; importa lo nuevo (por nombre de archivo) recursivamente; la carpeta queda recordada.
- [x] Repo de fuentes: + Gutenberg en español (gutendex languages=es) y unglue.it (OPDS, licencias libres; parser probado con el feed real).
- [ ] Probar en el TECNO: TTS (voz en español instalada), auto-scroll, importar TXT/FB2, sincronizar carpeta.
- [ ] TTS y auto-scroll solo en EPUB (no PDF/cómic). Sin sincronización automática en segundo plano.
- Descartado por costo/beneficio: anotaciones PDF, reflow de PDF, modo músico, MOBI/AZW3, DOCX/ODT.
