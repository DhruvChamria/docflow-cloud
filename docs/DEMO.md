# Demo guide

## Start the application

From the repository root:

```powershell
docker compose --profile app --profile observability up --build -d
```

Start the frontend in another terminal:

```powershell
cd frontend
npm.cmd run dev
```

Open `http://127.0.0.1:5173`. Use this hostname consistently so that the browser session
cookie and Vite API proxy remain on the same origin.

## Suggested demonstration

1. Open the operation selector and show that the catalogue is loaded from the backend.
2. Merge two small PDFs and show the `QUEUED`, `PROCESSING` and `COMPLETED` states.
3. Show the result size and SHA-256 prefix, then download the merged result.
4. Run a parameter-based operation such as page numbering or text watermarking.
5. Convert a small DOCX or PPTX file to PDF and explain that LibreOffice runs in a separate worker.
6. Refresh the page to show guest job history and, when enough jobs exist, load an older page.
7. Optionally register, submit a job, sign out and sign in again to show account history.
8. Open Grafana at `http://127.0.0.1:3000` and show the DocFlow dashboard.
9. Run `docker compose ps` to show the API, workers, database, storage emulator and
   monitoring services as separate containers.

For the cloud portion, open the Azure HTTPS application, submit one small PDF or document
conversion and show the completed result. Explain that the API remains available while both
queue-driven workers can scale to zero when their queues are empty. Keep the local stack as
the fallback if the campus network or Azure portal is slow.

## Architecture summary

The React application submits a job to the Spring Boot API. The API stores job state in
PostgreSQL, places private files in Azurite and publishes a queue message. A PDF or Office
worker claims the job, creates the result and saves it to private storage. The API checks
the owner before allowing the result to be downloaded.

## Useful implementation points for questions

- Upload requests return quickly with `202 Accepted`; workers perform the expensive work.
- PostgreSQL is the source of truth for states and leases, while queue messages only wake workers.
- Duplicate delivery is safe because completion is fenced by a lease token and dispatch ID.
- Office files are validated as bounded packages before LibreOffice receives them.
- Results carry stored byte length and SHA-256 integrity metadata.
- Guest access is the default; accounts only add persistent owner-scoped history.

## Current limitations

The project is deployed to Azure, but the evaluation deployment is intentionally small and
may show a short worker cold-start delay. XLSX conversion is not exposed. The authentication
system and document-processing containers demonstrate the application architecture but are
not presented as a production document-security service. Use only non-sensitive test files
because the project does not include malware scanning.
