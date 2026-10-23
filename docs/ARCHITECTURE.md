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
operation parameters, progress, paginated job history and result download. Completed jobs
show the result size and a shortened SHA-256 checksum.

The Spring Boot API validates requests, resolves the current guest or account owner,
persists job metadata and authorizes lifecycle actions. It does not run PDFBox,
Ghostscript or LibreOffice directly. Mutating job and authentication routes have separate
per-client request budgets, and each owner may have at most five active jobs by default.

PostgreSQL stores users, sessions, job state, dispatch records and worker leases. Azurite
provides local Blob and Queue services. Input and output objects are private and are
accessed through the API.

The PDF worker handles PDFBox operations, generated PDFs and bounded Ghostscript
compression. Office conversions use a separate queue and a separate LibreOffice container
so that native conversion does not block the PDF worker. Office uploads are inspected as
bounded ZIP packages before queueing: entry count, expanded size, per-entry size,
compression ratio, path safety and required package markers are checked.

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
of three attempts prevent continuous failure loops. Result upload records its byte size and
SHA-256 checksum in the fenced completion update; the API returns both metadata and a
checksum response header when the owner downloads the result.

## Ownership and file handling

Guest users receive a random HTTP-only session cookie. Registered users receive a separate
server-side session. Status, history, cancellation, deletion and download operations are
filtered by the resolved owner.

Uploads and results are temporary. Cleanup removes stored objects before expiring their
metadata. Passwords supplied for protect and unlock operations are encrypted separately
from ordinary job parameters and removed when processing reaches a terminal state.

The API, PDF worker and Office worker run as non-root users in read-only containers. Docker
Compose removes Linux capabilities, prevents privilege escalation and provides bounded
writable temporary filesystems. CI runs backend and frontend tests, validates Compose and
scans dependencies, repository secrets, container/IaC configuration and all three built
application images with Trivy.

## Local and Azure deployment

| Local component | Deployed Azure service |
|---|---|
| Vite frontend | Compiled assets served by the API Container App |
| Docker Compose API and workers | Azure Container Apps |
| Azurite Blob Storage | Azure Blob Storage |
| Azurite Queue Storage | Azure Queue Storage |
| PostgreSQL | Azure Database for PostgreSQL Flexible Server |
| Local secret volume | Azure Key Vault and Container App secrets |
| Prometheus and Grafana | Azure Monitor and Log Analytics |
| Local container images | Azure Container Registry Basic |

Terraform provisions one Workload Profiles Container Apps environment in Central India.
The API serves the compiled React frontend from the same HTTPS origin and keeps one replica
available. The PDF and Office workers consume separate Azure Storage queues, scale from zero
when work arrives and return to zero after their cooldown. PostgreSQL remains the authoritative
job store in both environments, so the cloud deployment uses the same persistence model as
the local implementation. Azure Monitor and Log Analytics provide cloud logs, while the local
Prometheus and Grafana stack remains available for the dashboard demonstration.
