# Security model

## Trust boundaries

The browser, uploaded documents and queue messages are untrusted. The API is the only
component exposed to the browser. PostgreSQL, Azurite and both workers remain on the
internal Compose network; stored files are downloaded through an owner-authorized API
route rather than through public blob URLs.

## Principal threats and controls

| Threat | Current control |
|---|---|
| Cross-user job or file access | Random guest identity or server-side account session; every job query includes the resolved owner |
| Cross-site mutation | SameSite session cookies and a required `X-Requested-With` header |
| Password disclosure | Document passwords are encrypted separately, omitted from queue messages and logs, and deleted after terminal processing |
| Oversized or malformed input | Request, file, total-upload, page-count and output limits; PDF/image/text signatures are checked |
| Office archive expansion | Safe paths, required markers, entry count, per-entry size, total expanded size and compression ratio are validated |
| Duplicate or stale queue delivery | Dispatch identifiers, leases and attempt tokens fence state transitions and result publication |
| Resource exhaustion | Per-client mutation limits, an active-job cap, worker timeouts, retry limits and container CPU/memory/tmpfs bounds |
| Container privilege abuse | Non-root process, read-only root filesystem, all capabilities dropped and `no-new-privileges` enabled |
| Dependency or configuration regression | Locked frontend dependencies plus CI tests, Compose validation and Trivy vulnerability/secret/misconfiguration scanning |
| Residual files | Twenty-four-hour expiry, cancellation/deletion cleanup and private storage prefixes per job |

## Deliberate limitations

The local profile uses HTTP and therefore does not mark cookies `Secure`; the cloud profile
must terminate HTTPS and enable secure cookies. The platform does not scan uploads for
malware and should only be demonstrated with non-sensitive test documents. LibreOffice,
PDFBox and Ghostscript reduce parser exposure through worker isolation and limits, but they
do not make hostile documents risk-free. Formula-heavy XLSX input is intentionally not
offered. Azure identity, Key Vault integration and cloud network policy remain part of the
final deployment phase.

## Reporting and verification

Security-relevant failures use generic client messages and structured server logs without
document contents or passwords. CI treats high and critical Trivy findings as failures.
Before a release, run the full Docker-backed regression suite and scan each built image in
addition to the repository filesystem scan.
