# Dev data seeder

`DevDataSeeder` runs at startup **only** under the `dev` Spring profile (disable with
`humano.dev.seed.enabled=false`). It boots a ready-to-use demo tenant so you can exercise every role
without manual onboarding. It is fail-soft (never blocks startup) and idempotent (safe to re-run).

## Requirements

- A reachable **dev MySQL** server — provisioning creates a physical tenant database. With H2 only,
  provisioning fails (logged, non-fatal) and no demo tenant is created.
- Run with the `dev` profile, e.g. `./mvnw` (the dev profile is active by default) or
  `-Dspring.profiles.active=dev`.

## What you get

- A business tenant on subdomain **`demo`** (not the platform `default` tenant).
- One **activated** user per role in the tenant's authority catalog, each holding that role +
  `ROLE_USER`.

## Logging in

Authentication resolves the user **inside the tenant context**, so every request must target the demo
tenant with the header:

```
X-Tenant-ID: demo
```

- **Password (all users):** `Passw0rd!`
- **Logins:** `<role>@demo.humano`, where `<role>` is the role name without the `ROLE_` prefix,
  lowercased, with `_` → `.`. Examples:
  - `admin@demo.humano` (ROLE_ADMIN)
  - `hr.manager@demo.humano` (ROLE_HR_MANAGER)
  - `payroll.admin@demo.humano` (ROLE_PAYROLL_ADMIN)
  - `finance.manager@demo.humano`, `manager@demo.humano`, `employee@demo.humano`, …

The full list of created logins is printed on startup as `[dev-seed] created …@demo.humano (role …)`
lines, followed by a `DEV DEMO LOGINS` summary block.
