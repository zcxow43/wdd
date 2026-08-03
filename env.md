# Development Environment

## Project Directory Structure
```
wdd/                    ← project root (shared configs only)
├── develop/
│   ├── backend/        ← Spring Boot Maven project (pom.xml, src/, etc.)
│   └── frontend/       ← React Vite project (package.json, src/, etc.)
├── docker/             ← docker-compose.yml
├── demo/               ← static UI prototypes, served by demo/server.js
├── specs/              ← spec files
└── env.md
```

# Develop

## Frontend
- Directory: `develop/frontend/`
- Language: TypeScript
- Framework: React
- Build Tool: Vite
- Package Manager: npm
- Server: `npm --prefix develop/frontend run dev`
- Port: 5173

## Backend
- Directory: `develop/backend/`
- Language: Java 17+
- Framework: Spring Boot 3.x
- Build Tool: Maven
- ORM: MyBatis
- Base Package: `pl.piomin.services`
- No Lombok
- Server: `mvn -f develop/backend/pom.xml spring-boot:run`
- Port: 8080

## Demo
- Directory: `demo/`
- Server: `node demo/server.js` — plain static file server rooted at `demo/`
- Port: 8099

Every `Server`/`Port` above is mirrored as a `preview_start`-compatible entry in `.claude/launch.json` (fixed path, required by the Claude Code harness itself — see `.claude/agents/infra.md`). `env.md` is the source of truth; `launch.json` must always match it exactly, the same way `docker/docker-compose.yml` mirrors the `# Container` section below.

# Container

## Database
- Engine: MySQL 8.0.36
- Host: 127.0.0.1
- Port: 3306
- Database: wdd
- Username: app
- Password: 1234

## JDBC Connection
```
jdbc:mysql://127.0.0.1:3306/wdd?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```
