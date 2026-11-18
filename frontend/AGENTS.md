# Repository Guidelines

## Project Structure & Module Organization

This repository is a React 19, TypeScript, and Vite frontend. Application code lives in `src/`. Keep route-level views in `src/screens/<area>/` (one lazy chunk per route, registered in `src/router.tsx`), reusable primitives in `src/components/ui/`, and shared page chrome in `src/components/layout/`. API clients and React Query hooks live in `src/api/` (one file per backend module); authentication and the session store live in `src/auth/`. Every tenant-scoped query key must start with `['t', tenantId, ...]` (use `useTenantQuery`). `src/main.tsx` creates the QueryClient and router. `src/vendor/scrollcraft/` is a vendored third-party engine used only by the landing page; do not lint or restyle it.

## Build, Test, and Development Commands

- `npm install` installs the locked dependencies from `package-lock.json`.
- `npm run dev` starts Vite with hot module replacement.
- `npm run build` runs TypeScript project checks, then creates the production bundle in `dist/`.
- `npm run lint` checks the repository with Oxlint.
- `npm run preview` serves the built bundle locally for final verification.
- `npm run seed` creates demo data against a backend running with the `dev` profile.
- `npm run e2e` runs Playwright tests in `e2e/` (backend-dependent tests skip when the backend is down).

Run `npm run lint` and `npm run build` before submitting changes.

## Coding Style & Naming Conventions

Use TypeScript and functional React components. Follow the existing two-space indentation and single-quote import style; keep semicolon usage consistent within each edited file. Name components and screen files in PascalCase (`DynamicRecords.tsx`), variables and callbacks in camelCase, and CSS files or static assets descriptively. Define component prop types close to their components. Prefer reusable controls from `src/components/ui/` and design tokens from `tailwind.config.js` over duplicated markup or arbitrary colors. Oxlint enforces React hooks and TypeScript rules; the TypeScript configuration rejects unused locals and parameters.

## Testing Guidelines

End-to-end tests use Playwright in `e2e/*.spec.ts`; screenshots go to `e2e/screenshots/`. Validate changes with lint, a production build, and `npm run e2e`. For UI work, verify login flow, tenant switching, navigation, responsive layout, skeleton/empty/error states, and `prefers-reduced-motion`. All UI text is Vietnamese; format dates and numbers with `src/lib/format.ts` (vi-VN).

## Commit & Pull Request Guidelines

The repository has no commit history from which to infer a house style. Use short, imperative commit subjects, optionally following Conventional Commits, for example `feat: add audit log filters`. Keep commits focused. Pull requests should summarize the change, explain validation performed, link relevant issues, and include before/after screenshots for visible UI changes. Call out configuration changes or known follow-up work explicitly.
