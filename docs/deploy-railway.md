# Deploy on Railway

Railway is a good fit for hosting the **admin API and UI** and keeping **PostgreSQL** online. Windows **MDM enrollment** from home or lab PCs can still target your Railway URL, but you must understand TLS and **lab CA persistence** (see [Caveats](#caveats)).

## Architecture on Railway

```text
Browser / Admin  ──HTTPS──►  Railway edge TLS  ──HTTP──►  Spring Boot :PORT
Windows MDM      ──HTTPS──►  same public URL  ──HTTP──►  OMA-DM + enrollment SOAP
                                    │
                                    └── PostgreSQL (Railway plugin)
```

The app listens on Railway’s **`PORT`** with **`APPLOCKER_LAB_TLS=false`**. Railway terminates HTTPS; do not enable the in-container lab TLS keystore on Railway.

## Prerequisites

1. Code on **GitHub** (this repo).
2. [Railway](https://railway.app) account connected to GitHub.
3. A strong **`APPLOCKER_ADMIN_TOKEN`** (password manager); do not rely on the auto-generated file inside the container.

## 1. Create the project

1. **New Project** → **Deploy from GitHub repo** → select this repository.
2. Open the **AppLocker** service → **Settings** → set **Root Directory** to `backend`.
3. Railway should detect `backend/Dockerfile` (and optional `railway.toml` at repo root).

Generate a public domain: **Settings → Networking → Generate Domain** (e.g. `applocker-production.up.railway.app`).

## 2. Add PostgreSQL

1. In the same Railway project: **New** → **Database** → **PostgreSQL**.
2. On the **web service**, **Variables** → **Add Reference** → link Postgres variables (`PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`).

Flyway runs on startup when `SPRING_PROFILES_ACTIVE=deploy`.

## 3. Required environment variables

Set these on the **web service** (not in git):

| Variable | Example / source |
|----------|----------------|
| `SPRING_PROFILES_ACTIVE` | `deploy` |
| `APPLOCKER_LAB_TLS` | `false` |
| `APPLOCKER_ADMIN_TOKEN` | long random secret |
| `PUBLIC_BASE_URL` | `https://YOUR-SERVICE.up.railway.app` (no trailing slash) |

Railway can template the public URL after you create a domain:

```text
PUBLIC_BASE_URL=https://${{RAILWAY_PUBLIC_DOMAIN}}
```

(Paste the full `https://` prefix if your template syntax differs.)

**Optional override** if JDBC does not connect:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:PORT/railway?sslmode=require
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

Copy JDBC host/user/password from the Postgres service **Connect** tab.

## 4. Persist the lab CA (strongly recommended)

Enrollment and device MDM trust certificates issued by the **lab CA** under `data/lab-ca`. Without a volume, **every redeploy creates a new CA** and existing enrolled devices stop trusting the server.

1. **Volumes** → mount path **`/app/data`** on the web service (e.g. 1 GB).
2. After first deploy, download **`lab-ca.crt`** once via SSH/shell or by copying from a one-off admin export path you trust — today the CA is written on disk under `data/lab-ca/lab-ca.crt` inside the container.
3. Install that CA on test Windows devices (same as [enrollment-lab.md](enrollment-lab.md)).

If you skip the volume, treat each deploy as a **new lab** (re-trust CA, new PPKG).

## 5. Deploy and verify

1. Trigger deploy (push to `main` or **Deploy** in Railway).
2. Open `https://YOUR-SERVICE.up.railway.app/api/v1/health` → should return OK JSON.
3. Open the UI root URL, sign in with `APPLOCKER_ADMIN_TOKEN`.
4. Create an enrollment token; use the printed **`https://.../discovery/{id}`** as `DiscoveryServiceFullUrl` in Windows Configuration Designer.

## 6. Windows enrollment from the internet

- Test devices must **trust the lab CA** (not only Railway’s edge cert).
- `PUBLIC_BASE_URL` must match the hostname Windows calls (your Railway domain).
- PPKG still built with **Windows Configuration Designer**; nothing in this repo builds `.ppkg`.
- AppLocker **enforce** behavior still depends on enrollment scope (MDM Full vs device); see project README and Windows docs.

## Caveats

| Topic | Railway behavior |
|-------|------------------|
| TLS to the JVM | Edge HTTPS only; set `APPLOCKER_LAB_TLS=false`. |
| Admin auth | Required; set `APPLOCKER_ADMIN_TOKEN`. |
| CA / server cert | Persists only with **`/app/data` volume** (or external CA — not implemented). |
| Cold start | First boot runs Flyway + may generate CA; health check allows ~120s in Dockerfile. |
| Production MDM | Railway is fine for **demos and labs**; enterprise MDM usually needs your own domain, PKI, and compliance review. |

## Local Docker smoke test (optional)

From `backend`:

```powershell
docker build -t applocker:local .
docker run --rm -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=deploy `
  -e APPLOCKER_LAB_TLS=false `
  -e APPLOCKER_ADMIN_TOKEN=test-admin `
  -e PUBLIC_BASE_URL=http://localhost:8080 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/applocker `
  -e DATABASE_USERNAME=applocker `
  -e DATABASE_PASSWORD=applocker `
  applocker:local
```

Use a local Postgres (e.g. `docker compose up -d` from the repo root).

## CLI alternative

With [Railway CLI](https://docs.railway.com/develop/cli):

```bash
railway login
railway init
# set root directory to backend in the dashboard, add Postgres, set variables, then:
railway up
```