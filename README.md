# DocFlow Cloud

DocFlow Cloud is a cloud-native document processing platform for PDF tools and
document-to-PDF conversions. It uses an asynchronous job pipeline instead of processing
uploaded files inside the web request.

The current implementation runs locally with Docker Compose. Azure deployment and
Terraform infrastructure are planned but are not included yet.

## Features

- 27 PDF and document operations, including merge, split, page editing, watermarks,
  metadata tools, password protection, compression and document conversion
- guest access without registration
- optional accounts with owner-specific job history
- asynchronous processing with queued jobs, retries and worker leases
- separate PDF and LibreOffice workers
- temporary private file storage and owner-authorized downloads
- Prometheus metrics and a provisioned Grafana dashboard

Supported conversions include images, plain text, Markdown, restricted HTML, DOCX,
ODT and ODS to PDF. PPTX, XLSX and ODP conversion are not currently offered.

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

The latest complete local verification passed 44 Maven tests, the frontend TypeScript
check and the production frontend build. See [docs/TESTING.md](docs/TESTING.md) for the
covered behaviour and current test limitations.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Testing](docs/TESTING.md)
- [Demo guide](docs/DEMO.md)

## Current scope

The local application demonstrates the intended queue-based architecture, authentication,
authorization, worker separation, recovery behaviour and monitoring. Azure resources,
cloud autoscaling and production security controls such as malware scanning are future
work and should not be inferred from the local implementation.
