# Currency Management Frontend

React + TypeScript + Vite single-page app for managing currencies. Consumes the
Currency API implemented in `develop/backend` (`specs/backend/currency.md`).

## Tech Stack
- TypeScript
- React 19
- Vite 8 (build tool + dev server)
- react-router-dom (routing)
- Vitest + Testing Library (unit/component tests)
- npm (package manager)

## Getting Started

```bash
cd develop/frontend
npm install
npm run dev
```

The dev server starts on `http://localhost:5173` and opens the app at
`/currencies` (root `/` redirects there).

## Backend API Configuration

The app talks to the Currency API at base path `/api/currencies`. The backend
(Spring Boot) runs on `http://localhost:8080` by default and does **not**
send CORS headers, so two env vars control how requests reach it:

| Variable                     | Used by            | Purpose                                                                                     |
|-------------------------------|---------------------|-----------------------------------------------------------------------------------------------|
| `VITE_API_BASE_URL`           | Browser (client code) | Prefix for every API call. Left empty by default so requests are relative (`/api/currencies`), same-origin, and CORS-free. Only set this to an absolute URL (e.g. `https://api.example.com`) if the backend is deployed on a different origin *and* sends the correct CORS headers. |
| `VITE_BACKEND_PROXY_TARGET`   | Vite dev server only  | Where `npm run dev` forwards `/api/*` requests to, avoiding CORS during local development. Defaults to `http://localhost:8080`. |

Defaults are checked into `.env.development` and mirrored in `.env.example`.
To point at a backend running on a different host/port, override
`VITE_BACKEND_PROXY_TARGET` (dev) or `VITE_API_BASE_URL` (build), e.g.:

```bash
VITE_BACKEND_PROXY_TARGET=http://localhost:9090 npm run dev
```

In production, deploy the built static assets behind the same reverse proxy
(or gateway) that also serves `/api/*` from the backend, so `VITE_API_BASE_URL`
can stay empty and no CORS configuration is needed.

## Scripts

| Command            | Description                                  |
|---------------------|-----------------------------------------------|
| `npm run dev`       | Start the Vite dev server with API proxy      |
| `npm run build`     | Type-check (`tsc -b`) and build production assets to `dist/` |
| `npm run preview`   | Preview the production build locally          |
| `npm test`          | Run all tests once (Vitest)                   |
| `npm run test:watch`| Run tests in watch mode                       |
| `npm run lint`      | Lint the codebase (Oxlint)                    |

## Project Structure

```
src/
├── api/            # fetch-based API client + typed error classes + currencyApi
├── components/      # reusable UI components (table, modal, form, toast, filter)
├── pages/           # route-level pages (CurrencyPage)
├── types/           # shared TypeScript types
├── test/            # Vitest setup (jsdom, testing-library cleanup)
├── App.tsx           # route definitions
└── main.tsx           # app bootstrap (router + toast provider)
```

## Features

- `/currencies` page listing all currencies in a table (Code, Name, 中文名稱,
  Symbol, Decimal Places, Active, Actions)
- Status filter (All / Active / Inactive) and free-text search (code/name/中文名稱)
- Add/Edit via modal form with client-side validation (matches backend rules:
  code = 3 uppercase letters and disabled while editing, name required ≤100
  chars, nameZh/symbol optional, decimalPlaces integer 0–8)
- Delete with a confirmation dialog
- Error handling: inline "幣種代碼已存在" on 409 create conflicts, toast
  "幣種不存在，請重新整理頁面" on 404s during edit/delete, toast
  "網路錯誤，請稍後再試" on network failures
- Loading and empty states for the table
