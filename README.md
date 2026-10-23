# DocFlow Cloud

DocFlow Cloud is a cloud-native document processing platform for PDF tools and
document-to-PDF conversions. It uses an asynchronous job pipeline instead of processing
uploaded files inside the web request.

The application runs locally with Docker Compose and is deployed to Azure Container Apps.
Terraform defines the Azure environment, including storage, PostgreSQL, secrets, monitoring
and queue-scaled PDF and Office workers.

## Features

- 29 PDF and document operations, including merge, split, page editing, watermarks,
  metadata tools, password protection, compression and document conversion
- guest access without registration
- optional accounts with owner-specific job history
- asynchronous processing with queued jobs, retries and worker leases
- separate PDF and LibreOffice workers
- temporary private file storage and owner-authorized downloads
- paginated history plus result size and SHA-256 integrity metadata
- request throttling, per-owner active-job limits and hardened application containers
- Prometheus metrics and a provisioned Grafana dashboard

Supported conversions include images, plain text, Markdown, restricted HTML, DOCX,
PPTX, ODT, ODP and ODS to PDF. XLSX conversion is not currently offered because accepting
macro-enabled and formula-heavy spreadsheet inputs safely needs more validation work.

## Technology stack

- React, TypeScript and Vite
- Java 21 and Spring Boot
- Apache PDFBox, Ghostscript and LibreOffice
- PostgreSQL
- Azurite Blob and Queue services
- Docker Compose
- Prometheus and Grafana
- GitHub Actions

## Running locally

Requirements:

- Docker Desktop with Linux containers
- Node.js 22 or later
- npm

Create the local environment file and replace both example passwords:

```powershell
Copy-Item .env.example .env
```

Build and start the application services:

```powershell
docker compose --profile app --profile observability up --build -d
```

Start the frontend in another terminal:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open `http://127.0.0.1:5173`. Grafana is available at
`http://127.0.0.1:3000` and Prometheus at `http://127.0.0.1:9090`.

Stop the stack without deleting its named volumes:

```powershell
docker compose down
```

## Verification

Run the complete local pipeline test:

```powershell
./scripts/test-local-pipeline.ps1
```

Run service health checks:

```powershell
./scripts/verify-local-services.ps1
```

The ordinary backend suite passes 46 tests and marks 10 live-pipeline tests as skipped
until the Compose stack is supplied. The complete Docker-backed run passes all 56 tests.
Seven frontend component/accessibility tests, the TypeScript check and the production
frontend build also pass, and the locked frontend dependencies audit with zero known
vulnerabilities. See [docs/TESTING.md](docs/TESTING.md) for the full verification
procedure and current limitations.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Security model](docs/SECURITY.md)
- [Testing](docs/TESTING.md)
- [Demo guide](docs/DEMO.md)

## Current scope

The local and Azure deployments demonstrate the queue-based architecture, authentication,
authorization, worker separation, recovery behaviour, integrity metadata, abuse controls
and monitoring. The platform does not include malware scanning and is not presented as a
production document-security service.
