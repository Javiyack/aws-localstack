# Contributing

## Flujo de trabajo

```
main
 └── feature/<nombre>   →  PR → revisión → merge a main
 └── fix/<nombre>
 └── chore/<nombre>
```

- Una rama por tarea. PRs contra `main`, requieren al menos **1 aprobación**.
- Commits siguiendo [Conventional Commits](https://www.conventionalcommits.org/):
  `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`.
- **Nunca** usar `--no-verify` ni `--force` en ramas compartidas.

```bash
# Ejemplo de ciclo de trabajo
git checkout -b feature/mi-cambio
# ... código ...
git commit -m "feat(pipeline): añadir retry en KinesisProducer"
git push origin feature/mi-cambio
# Abrir PR en GitHub
```

---

## Prerrequisitos

| Herramienta | Versión mínima | Instalación |
|---|---|---|
| Java (JDK) | 21 | `winget install EclipseAdoptium.Temurin.21.JDK` |
| sbt | 1.10.7 | `cs install sbt` (via Coursier) |
| Node.js | 22 | `winget install OpenJS.NodeJS.LTS` |
| Docker Desktop | 4.x | `winget install Docker.DockerDesktop` |
| Terraform | 1.5+ | `winget install Hashicorp.Terraform` |
| AWS CLI | 2.x | `winget install Amazon.AWSCLI` |

---

## Convenciones de código

### Scala
- Formato: **Scalafmt** (config en `.scalafmt.conf`)
- Comprobación antes de commit: `sbt scalafmtCheck`
- Tests: `sbt test`  
- Cobertura: `sbt coverage test coverageReport` — umbral mínimo **80%**

### TypeScript / React
- Formato: **ESLint + Prettier** (configurado en `eslint.config.js`)
- Check: `npm run lint`
- Tests: `npm test`
- Build: `npm run build`

---

## Tests obligatorios

Todo PR debe pasar en CI sin errores:

```bash
# Pipeline Scala
cd pipeline && sbt test

# Value backend
cd value-backend && npm test

# Dashboard
cd dashboard && npm test
```

La cobertura del pipeline Scala **no debe bajar del 80%**.

---

## Levantar el entorno local

```bash
# 1. Arrancar servicios de infraestructura
docker compose up -d

# 2. Aplicar Terraform (crear recursos en LocalStack)
cd infra && terraform init && terraform apply -auto-approve && cd ..

# 3. Compilar y subir Lambda (requiere sbt)
cd pipeline && sbt assembly && cd ..

# 4. Arrancar value-backend (modo desarrollo)
cd value-backend && npm run dev

# 5. Arrancar dashboard (modo desarrollo)
cd dashboard && npm run dev   # → http://localhost:5173

# 6. Ejecutar smoke test
bash scripts/e2e-smoke.sh
```

---

## Estructura del repositorio

```
.
├── dashboard/          React + Vite (Sprint 5-7)
├── docs/sprints/       Guías de implementación por sprint
├── infra/              Terraform (LocalStack + AWS)
├── pipeline/           Scala 3 + ZIO (Lambda)
├── scripts/            Scripts E2E y utilidades
├── value-backend/      Node.js + Fastify (micro-servicio de valores)
├── docker-compose.yml  LocalStack, Redis, PostgreSQL, value-backend
├── TODO.md             Seguimiento de sprints
└── README.md
```
