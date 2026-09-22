# Testing

## Automated tests

The backend test suite covers domain rules, API contracts, individual document operations
and the complete local queue pipeline.

The latest Docker-backed run completed 44 tests with no failures, errors or skipped tests.
Coverage includes:

- job-state transitions, cancellation and expiry
- input and operation-parameter validation
- PDF manipulation and typed PDF, ZIP, text and JSON results
- Markdown, restricted HTML and plain-text conversion
- DOCX, ODT and ODS conversion through the Office worker
- password protection, unlocking and temporary-secret cleanup
- guest and account ownership checks
- duplicate messages, retries, leases and stale-worker fencing
- private input/result storage and authorized downloads
- controlled PostgreSQL and Azurite outage recovery

Run the Docker-backed suite from the repository root:

```powershell
./scripts/test-local-pipeline.ps1
```

The ordinary Maven build does not run tests that require the live Compose stack unless the
integration-test URL is supplied.

## Frontend checks

Install the locked dependencies and run the production build:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
```

The TypeScript check and production build pass. The main browser workflow has also been
checked manually against the Compose stack.

## Reliability and monitoring checks

The outage script temporarily interrupts this project's local database and storage
containers. Run it only when no upload or demonstration is in progress:

```powershell
./scripts/test-local-outages.ps1 -AllowServiceInterruptions
```

A small k6 smoke run used two virtual users for ten seconds and completed 17 job journeys
without failed requests. This was a development smoke test, not a cloud capacity test.

## Known test gaps

- no automated frontend component or browser end-to-end suite
- no multi-replica or Azure deployment test
- incomplete hard-crash testing during queue publication and result commit
- no adversarial Office archive or malware-scanning test corpus
- no completed dependency, secret and container-image security scan
