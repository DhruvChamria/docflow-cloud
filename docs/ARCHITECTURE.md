# Architecture

## Overview

DocFlow Cloud separates user requests from document processing. The API validates an
upload, stores it privately, records a job and sends a compact message to a queue. A worker
processes the document and stores the result. The API remains responsible for ownership
checks and result delivery.

```text
React frontend
      |
      v
Spring Boot API ---- PostgreSQL
      |
      +---- Azurite Blob Storage
      |
      +---- Azurite Queue Storage
                  |
                  +---- PDF worker
                  |
                  +---- Office worker

API and workers ---- Prometheus ---- Grafana
```

## Components

The React frontend obtains the operation catalogue from the API and provides file upload,
operation parameters, progress, job history and result download.

The Spring Boot API validates requests, resolves the current guest or account owner,
persists job metadata and authorizes lifecycle actions. It does not run PDFBox,
Ghostscript or LibreOffice directly.

PostgreSQL stores users, sessions, job state, dispatch records and worker leases. Azurite
provides local Blob and Queue services. Input and output objects are private and are
accessed through the API.

The PDF worker handles PDFBox operations, generated PDFs and bounded Ghostscript
compression. Office conversions use a separate queue and a separate LibreOffice container
so that native conversion does not block the PDF worker.

Prometheus collects application and job metrics. Grafana is provisioned with a dashboard
for queue state, worker health, outcomes, duration and retries.

## Job lifecycle

```text
CREATED -> QUEUED -> PROCESSING -> COMPLETED -> EXPIRED
                         |
                         +-> RETRYING -> QUEUED
                         +-> FAILED

CREATED or QUEUED -> CANCELLED
```

Creating a job and its dispatch intent is coordinated through PostgreSQL. Queue delivery
is treated as repeatable: workers claim a specific attempt using a lease and attempt token.
A stale or duplicate worker cannot publish over a newer attempt. Retry delays and a maximum
of three attempts prevent continuous failure loops.

## Ownership and file handling

Guest users receive a random HTTP-only session cookie. Registered users receive a separate
server-side session. Status, history, cancellation, deletion and download operations are
filtered by the resolved owner.

Uploads and results are temporary. Cleanup removes stored objects before expiring their
metadata. Passwords supplied for protect and unlock operations are encrypted separately
from ordinary job parameters and removed when processing reaches a terminal state.

## Local and cloud mapping

| Local component | Intended Azure equivalent |
|---|---|
| Vite frontend | Azure Static Web Apps |
| Docker Compose API and workers | Azure Container Apps |
| Azurite Blob Storage | Azure Blob Storage |
| Azurite Queue Storage | Azure Queue Storage |
| PostgreSQL | Azure Cosmos DB adapter |
| Prometheus and Grafana | Azure Monitor and Application Insights |

The Azure infrastructure and cloud-specific adapters have not been implemented. The table
describes the planned deployment mapping, not the current deployment state.
