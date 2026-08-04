# Development Environment

## Project Directory Structure
```
wdd/                    ← project root (shared configs only)
├── develop/
│   ├── backend/        ← Spring Boot Maven project (pom.xml, src/, etc.)
│   └── frontend/       ← React Vite project (package.json, src/, etc.)
├── docker/             ← docker-compose.yml + start-*.sh (run commands)
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

## Backend
- Directory: `develop/backend/`
- Language: Java 17+
- Framework: Spring Boot 3.x
- Build Tool: Maven
- ORM: MyBatis

## Demo
- Directory: `demo/`
- Description: plain static file server rooted at `demo/`

# Container

## Database
- Engine: MySQL 8.0.36
- Host: 127.0.0.1
- Port: 3306
- Database: wdd
- Username: app
- Password: 1234
