# wdd

Currency/brand/spread management backend + frontend, built spec-first via Claude Code.

## Stack

See [env.md](env.md) for the full declared stack and container config. In short: Spring Boot 3.x (Java 17+, Maven, MyBatis) backend, React + TypeScript (Vite) frontend, MySQL 8.0.36 in Docker.

## Structure

```
wdd/
├── develop/
│   ├── backend/        ← Spring Boot Maven project
│   └── frontend/       ← React Vite project
├── docker/             ← docker-compose.yml
├── demo/               ← static UI prototypes (node demo/server.js, port 8099)
├── specs/              ← feature specs (dba/backend/frontend), the source of truth
└── env.md              ← declared stack + container config
```

## Workflow

This project is built and rebuilt from `specs/` rather than by hand:

- `/spec <requirement>` — turn a requirement into frontend/backend/dba spec files
- `/dev` — execute every pending spec, dispatching to the matching agent (dba → backend → frontend)
- `/infra` — inspect/edit `env.md` and the matching `docker-compose.yml` service
- `/init` — bootstrap `develop/`, `docker/` from `env.md` on an empty checkout
- `/start` — start containers (docker compose) then backend/frontend dev servers, in that order
- `/close` — the inverse of `/start`: stop backend/frontend and containers without deleting anything
- `/destroy` — the inverse of `/init`: wipe `develop/`, `docker/`, and every container, and reset all specs back to pending
- `/reset-env` — drop every table in the database and rebuild it from `specs/dba/` (specs re-applied even when already `done`)
- `/commit` — commit and push both this repo and the `.claude` submodule

A fresh checkout is: `/init` → `/dev`. Config and tooling shared across sessions live in the `.claude/` submodule (agents, commands, skills).

## Running the backend

```
docker compose -f docker/docker-compose.yml up -d   # MySQL
mvn -f develop/backend/pom.xml spring-boot:run
curl http://localhost:8080/api/health                # {"status":"UP"}
```
