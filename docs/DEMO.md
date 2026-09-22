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
3. Download the merged result.
4. Run a parameter-based operation such as page numbering or text watermarking.
5. Convert a small DOCX file to PDF and explain that LibreOffice runs in a separate worker.
6. Refresh the page to show guest job history.
7. Optionally register, submit a job, sign out and sign in again to show account history.
8. Open Grafana at `http://127.0.0.1:3000` and show the DocFlow dashboard.
9. Run `docker compose ps` to show the API, workers, database, storage emulator and
   monitoring services as separate containers.

## Architecture summary

The React application submits a job to the Spring Boot API. The API stores job state in
PostgreSQL, places private files in Azurite and publishes a queue message. A PDF or Office
worker claims the job, creates the result and saves it to private storage. The API checks
the owner before allowing the result to be downloaded.

## Current limitations

The project has not been deployed to Azure. PPTX, XLSX and ODP conversion are not exposed.
The local authentication system and document-processing containers demonstrate the
application architecture but are not presented as a production document-security service.
