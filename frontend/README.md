# ReconcileX — Frontend

The React 19 frontend for ReconcileX: a dark-themed, enterprise-grade financial reconciliation interface. Built with TanStack Router, TanStack Start, and Tailwind CSS v4.

---

## Tech Stack

| Technology | Version | Purpose |
| :--- | :--- | :--- |
| **React** | 19.x | UI component library |
| **TanStack Router** | 1.170.x | File-based routing with type-safety |
| **TanStack Start** | 1.168.x | SSR + Nitro build pipeline |
| **TanStack Query** | 5.x | Server state management |
| **Tailwind CSS** | 4.x | Utility-first styling |
| **Vite** | 8.x | Development server and build tool |
| **TypeScript** | 5.8.x | Type-safe development |
| **Vitest** | 4.x | Unit and integration tests |
| **Lucide React** | 0.575.x | Icon library |
| **Radix UI** | Various | Accessible headless component primitives |

---

## Getting Started

**Prerequisites**: Node.js 20+, pnpm 8+

```powershell
# Install dependencies
pnpm install

# Start development server (proxies /api/* to localhost:8081)
pnpm run dev
```

The frontend dev server runs at **`http://localhost:8082`**.

All `/api/*` requests are reverse-proxied to the Spring Boot backend at `http://localhost:8081` via the Vite dev server proxy configured in `vite.config.ts`.

### Available Scripts

| Script | Description |
| :--- | :--- |
| `pnpm run dev` | Start Vite dev server with hot reload |
| `pnpm run build` | Production build (Vite + Nitro SSR) |
| `pnpm run preview` | Preview the production build locally |
| `pnpm test` | Run Vitest test suite (17 tests, once) |
| `pnpm test:watch` | Run Vitest in watch mode |
| `pnpm exec tsc --noEmit` | TypeScript strict type-check (no output) |
| `pnpm run lint` | ESLint code quality check |
| `pnpm run format` | Prettier code formatting |

---

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── AdminDashboardView.tsx      # Admin KPI dashboard with live run history
│   │   ├── AuditView.tsx              # Append-only audit trail browser
│   │   ├── AuthModal.tsx              # Common sign-in modal (single flow for ADMIN + VIEWER)
│   │   ├── BackendHealthIndicator.tsx # Live GET /api/health polling indicator
│   │   ├── DashboardView.tsx          # Role-based dashboard router (Guest / ADMIN / VIEWER)
│   │   ├── GuestDashboardView.tsx     # Public landing page with workspace access overview
│   │   ├── ImportsView.tsx            # CSV file upload with SHA-256 duplicate detection
│   │   ├── ResultsView.tsx            # Signature results screen: outcomes table + analytics rail
│   │   ├── RunsView.tsx               # Reconciliation run launcher and paginated run history
│   │   ├── SettingsView.tsx           # Platform specifications and architecture reference
│   │   ├── Sidebar.tsx                # Responsive navigation (desktop persistent + mobile drawer)
│   │   ├── UserManagementView.tsx     # ADMIN user provisioning and RBAC matrix
│   │   ├── ViewerDashboardView.tsx    # Viewer read-only dashboard with run history
│   │   └── ui/                        # Radix UI primitive wrappers (Button, Input, Dialog, etc.)
│   ├── lib/
│   │   ├── api.ts                     # All fetch API calls to the Spring Boot backend
│   │   ├── auth-context.tsx           # AuthProvider: in-memory JWT state, session timer, logout
│   │   ├── matching-rules.ts          # Single source of truth: rules, CSV headers, audit event types
│   │   └── types.ts                   # Shared TypeScript interface definitions
│   ├── routes/
│   │   ├── __root.tsx                 # Root layout: viewport meta, CSS, AuthProvider, QueryClient
│   │   └── index.tsx                  # Main application shell: sidebar, header, tab routing
│   ├── test/
│   │   ├── content-accuracy.test.ts   # Verifies matching-rules.ts constants are correct
│   │   ├── auth-and-rbac.test.ts      # JWT decode, role guards, session expiry logic
│   │   ├── app-routing.test.tsx       # Route rendering smoke tests
│   │   └── setup.ts                   # Vitest setup (jest-dom matchers)
│   ├── router.tsx                     # TanStack Router creation
│   ├── server.ts                      # Nitro SSR server entry (error wrapper)
│   ├── start.ts                       # App entry point
│   └── styles.css                     # Global CSS: Tailwind v4 theme + ReconcileX dark navy tokens
├── public/
├── package.json
├── tsconfig.json
├── vite.config.ts
└── vitest.config.ts
```

---

## Authentication & Security

Authentication follows a **single, common login flow** for both roles:

1. User enters credentials in the `AuthModal`.
2. `POST /api/auth/login` is called and returns a signed HS256 JWT.
3. The JWT payload is decoded client-side to extract `sub` (username) and `role` (`ADMIN` or `VIEWER`).
4. The token is stored **exclusively in React `useState` (in-memory)** — never in `localStorage` or `sessionStorage`.
5. Role-based navigation gates are applied automatically: ADMIN-only tabs (`File Imports`, `Audit Trail`, `User Management`, `Settings`) are hidden from VIEWER sessions.
6. A 30-minute countdown timer runs from the token's `exp` claim. When the session expires, a dismissible banner prompts re-authentication.

> **No credentials or tokens ever touch browser storage.** Refreshing the browser page clears the session — the user must sign in again.

---

## Single Source of Truth: `matching-rules.ts`

All matching rule labels, result type codes, required CSV column headers, and audit event types are defined **once** in [`src/lib/matching-rules.ts`](src/lib/matching-rules.ts). Every screen — Guest dashboard, Admin dashboard, Viewer dashboard, Imports, Results, Audit, and Settings — reads from this file. This ensures the frontend is always in sync with the Spring Boot backend rule hierarchy.

### Constants Exported

| Constant | Purpose |
| :--- | :--- |
| `MATCHING_RULES` | Array of 12 rule definitions (order, class, resultType, label, description, category) |
| `BANK_REQUIRED_HEADERS` | 8 required column names for bank statement CSV |
| `INTERNAL_REQUIRED_HEADERS` | 8 required column names for internal ledger CSV |
| `AUDIT_EVENT_TYPES` | 10 recorded audit event type strings |

---

## Application Screens

### Guest (Unauthenticated)
- **Dashboard** — Platform overview, ADMIN/VIEWER workspace descriptions, platform capabilities. No fabricated status indicators.
- **Reconciliation Runs** — Sign-in prompt; no data exposed.
- **Results & Reports** — Sign-in prompt; no data exposed.

### ADMIN Role
- **Dashboard** — Live backend health (GET /api/health), total run count, recent run table, 12-rule pipeline architecture.
- **File Imports** — Drag-and-drop or click CSV upload for bank statements and internal ledgers. SHA-256 duplicate detection returns HTTP 409 with the original Import ID.
- **Reconciliation Runs** — Trigger a new multi-pass matching run by Import ID, monitor status, paginated run history, per-run CSV download.
- **Results & Reports** *(Signature screen)* — Paginated transaction outcomes table, 5-card financial KPI rail (INR values), outcome category distribution, interactive filters (by result type, date range), expandable row details, streaming CSV export.
- **Audit Trail** — Append-only compliance log browser, filterable by Run ID and event type dropdown.
- **User Management** — Provision ADMIN/VIEWER accounts, view all application users from the backend, RBAC matrix reference.
- **Settings** — Platform specifications: engine architecture, 12-rule hierarchy, CSV schema, token policy, streaming implementation.

### VIEWER Role
- **Dashboard** — Live backend health, total run count, latest run status, run history table.
- **Reconciliation Runs** — Browse and download CSVs from historical runs (read-only; no trigger action).
- **Results & Reports** — Full access to all run data, filters, and CSV export.

---

## Responsive Design

The application is fully mobile-responsive:

| Breakpoint | Screen Width | Layout |
| :--- | :---: | :--- |
| Mobile | < 640px | Slide-out drawer navigation, single-column layout, full-width inputs, swipable tables |
| Small | ≥ 640px | 2-column KPI grids, inline action ribbons |
| Medium | ≥ 768px | Persistent sidebar navigation unlocked |
| Large | ≥ 1024px | Multi-column workstations (e.g., 8-col table + 4-col analytics rail) |

- All data tables (`ResultsView`, `RunsView`, `AuditView`, `UserManagementView`) use `overflow-x-auto` wrappers for touch horizontal scrolling.
- The authentication modal uses `w-[calc(100vw-2rem)] sm:max-w-[400px]` to prevent viewport overflow on 320px screens.
- The sidebar hamburger (`Menu` icon) appears only on `< md` screens; tapping the backdrop or any nav item auto-closes the drawer.

---

## Tests

```powershell
pnpm test
```

**17 tests across 3 suites — all passing.**

| Suite | Tests | Description |
| :--- | :---: | :--- |
| `content-accuracy.test.ts` | 5 | MATCHING_RULES count/structure, CSV header arrays, AUDIT_EVENT_TYPES |
| `auth-and-rbac.test.ts` | 11 | JWT payload decoding, role gates, session expiry countdown logic |
| `app-routing.test.tsx` | 1 | Route tree renders without crash |

---

## Production Build

```powershell
pnpm run build
```

The build pipeline:
1. **Vite client build** — outputs optimised JS/CSS bundles to `.output/public/assets/`.
2. **Vite SSR build** — renders the initial HTML shell server-side.
3. **Nitro build** — packages the Cloudflare Workers-compatible SSR server to `.output/server/`.

The production build is deployable to:
- **Cloudflare Workers** (`npx nitro deploy --prebuilt`)
- **Any Node.js host** (preview locally with `npx vite preview`)
- **Static hosting** with a configured backend CORS policy and `VITE_API_URL` environment variable pointing to the Spring Boot backend URL.

---

## Environment Variables

| Variable | Description | Default |
| :--- | :--- | :--- |
| `VITE_API_URL` | Backend API base URL for production deployment | `""` (same-origin proxy in dev) |

In development, the Vite proxy (`/api → http://localhost:8081`) handles all API calls — no `VITE_API_URL` needed.

In production, set `VITE_API_URL` to your deployed Spring Boot backend URL (e.g., `https://api.reconcilex.example.com`) so the frontend can reach it across origins.
