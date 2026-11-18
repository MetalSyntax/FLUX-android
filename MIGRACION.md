# Migración de datos FLUX (web ⇄ Android)

Ambas apps exportan e importan **el mismo `.zip`**: `flux-backup-AAAA-MM-DD.zip`.
Web: Perfil → «Exportar todo (.zip)» / «Importar copia (.zip)». Android: Perfil → «Exportar todo» / «Importar».

## Estructura del zip

```
manifest.json
books/<id>.<ext>      archivo original del libro (epub, pdf, cbz, cbr…)
covers/<id>.<ext>     portada (jpg/png) — opcional
```

## `manifest.json`

```jsonc
{
  "format": "flux-backup",   // obligatorio
  "version": 1,              // se rechaza si es mayor al soportado
  "exportedAt": 1794847492455,
  "source": "web" | "android",
  "settings": { "name", "avatar"?, "theme": "dark|black|white", "dailyGoal", "language": "en|es|pt", "showNsfw" },
  "metadata": { "sort_pref": "date|title|author", "lib_view": true, "search_history": ["..."] },
  "books": [{
    "id", "title", "author", "type": "EPUB|PDF|CBR",   // CBR incluye CBZ
    "progress": 0-100, "currentPage", "totalPages", "currentCfi"?,
    "lastReadDate": ms, "lastRead"?, "isFavorite", "tags": ["NSFW", ...],
    "fileName": "original.epub", "file": "books/<id>.epub", "cover": "covers/<id>.jpg"
  }],
  "bookmarks":   [{ "id", "bookId", "page", "cfi"?, "label", "color", "createdAt" }],
  "sessions":    [{ "id", "bookId", "startTime", "endTime", "pagesRead" }],
  "collections": [{ "id", "name", "color", "bookIds": ["..."] }]
}
```

Es el modelo de IndexedDB de la web (`db.ts`: stores `books`, `files`, `collections`, `bookmarks`, `sessions`, `settings`, `metadata`) con los binarios sacados a `books/` y `covers/`.

## Reglas de importación (fusión, sin duplicados)

- Un libro ya existe si coincide el `id` **o** (título + tipo + tamaño del archivo). No se duplica; si la copia tiene `lastReadDate` más reciente, se toma su progreso y favorito.
- Colecciones: por `id`, unión de `bookIds`. Marcadores y sesiones: por `id`.
- Ajustes: se aplican los de la copia (en Android el `avatar` no se importa).
- Libros de la copia sin archivo (`file` ausente) se omiten.
- Colores: la web usa tokens CSS (`var(--color-red)`…); Android los convierte a su paleta y exporta de vuelta el token si coincide, o `#RRGGBB`.
- NSFW = etiqueta `"NSFW"` en `tags`.

## Diferencias entre plataformas

- **EPUB**: la web guarda la posición como `currentCfi` (epub.js) y Android como 0-1000 sobre el libro. Al migrar se conserva el `progress` (%); un EPUB leído en Android y llevado a la web abre al inicio (sin CFI) pero muestra el % correcto. Web → Android abre en el % guardado.
- PDF/CBZ/CBR: `currentPage`/`totalPages` se conservan tal cual.
- Android no guarda historial de búsqueda ni etiquetas libres editables (se conservan al exportar/importar pero no se muestran).
