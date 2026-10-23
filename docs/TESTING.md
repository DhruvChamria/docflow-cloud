# Testing

## Automated tests

The backend test suite covers domain rules, API contracts, individual document operations
and the complete local queue pipeline.

The ordinary Maven verification discovers 56 tests: 46 pass locally and the 10 tests that
require a running Compose stack are skipped. With `E2E_URL` and the local dependencies
supplied, the complete Docker-backed command runs and passes all 56. Coverage includes:

- job-state transitions, cancellation and expiry
- input and operation-parameter validation
- PDF manipulation and typed PDF, ZIP, text and JSON results
- Markdown, restricted HTML and plain-text conversion
- DOCX, PPTX, ODT, ODP and ODS conversion through the Office worker
- bounded Office package validation, including unsafe paths and compression-ratio limits
- password protection, unlocking and temporary-secret cleanup
- guest and account ownership checks
- duplicate messages, retries, leases and stale-worker fencing
- private input/result storage and authorized downloads
- controlled PostgreSQL and Azurite outage recovery
- history pagination, result length and SHA-256 verification
- request-rate limiting and active-job admission limits

Run the Docker-backed suite from the repository root:

```powershell
./scripts/test-local-pipeline.ps1
```

The ordinary Maven build does not run tests that require the live Compose stack unless the
integration-test URL is supplied.

## Frontend checks

Install the locked dependencies, run the component tests and create a production build:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
```

Seven automated tests cover catalogue loading and retry, guest-compatible account login,
PPTX input selection, upload submission, polled completion/failure states and a loaded-shell
accessibility audit. The TypeScript check and production build also pass, and the locked
production and development dependencies audit with zero known vulnerabilities.

## Configuration and security checks

CI validates both Compose profiles and runs blocking Trivy scans for high and critical
dependency vulnerabilities, exposed secrets and Docker/IaC misconfiguration. It also
builds and scans the API, PDF-worker and Office-worker images. The release images reported
zero fixable HIGH/CRITICAL findings in their operating-system packages and application JARs.
Application services use read-only root filesystems, dropped Linux capabilities, no privilege
escalation and bounded temporary filesystems.

The final Spring Boot 4 working tree passes all 56 Docker-backed backend tests, all seven
frontend tests, the TypeScript check, production build, Compose validation and Terraform
validation.

## Reliability and monitoring checks

The outage script temporarily interrupts this project's local database and storage
containers. Run it only when no upload or demonstration is in progress:

```powershell
./scripts/test-local-outages.ps1 -AllowServiceInterruptions
```

A small k6 smoke run used two virtual users for ten seconds and completed 17 job journeys
without failed requests. This was a development smoke test, not a cloud capacity test.

## Known test gaps

- no sustained multi-replica cloud load test
- incomplete hard-crash testing during queue publication and result commit
- no browser end-to-end suite
- no malware scanner or broad adversarial document corpus
