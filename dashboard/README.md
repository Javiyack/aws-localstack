# Dashboard — React / TypeScript / Vite

Interfaz web para monitorizar y operar el pipeline AWS local en tiempo real.

## Stack

| Tecnología | Versión |
|---|---|
| React | 18.3 |
| TypeScript | 5.6 |
| Vite | 6 |
| Tailwind CSS | 4 |
| Recharts | 2.13 |
| Zustand | 5 |
| React Router | 6 |

## Desarrollo

```bash
npm install
npm run dev       # → http://localhost:5173
```

Requiere **LocalStack corriendo** en `http://localhost:4566` y **value-backend** en `http://localhost:3000`.

## Scripts disponibles

| Comando | Descripción |
|---|---|
| `npm run dev` | Servidor de desarrollo con HMR |
| `npm run build` | Build de producción en `dist/` |
| `npm run preview` | Preview del build |
| `npm test` | Tests con Vitest (run) |
| `npm run test:watch` | Tests en modo watch |
| `npm run lint` | ESLint sobre `src/` |

## Páginas

| Ruta | Descripción |
|---|---|
| `/` | Panel de estado de recursos AWS |
| `/streams` | Lector y escritor de streams Kinesis |
| `/logs` | Visor de logs CloudWatch |
| `/simulator` | Simulador de carga con gráficas en tiempo real |

## Variables de entorno

Crear `.env.local` (no se sube al repo):

```
VITE_API_URL=http://localhost:3000
```

## Build de producción

```bash
npm run build
# Servir dist/ con cualquier servidor estático
npx serve dist
```

**Nota de seguridad:** En producción no exponer LocalStack directamente al navegador.
Usar un backend proxy que autentique las peticiones con credenciales reales de AWS.

## Tests

14 tests unitarios cubriendo:
- `usePolling` — comportamiento del hook de refresco
- `StatusBadge` — renderizado por estado
- `messageGenerator` — generación de mensajes sintéticos válidos
