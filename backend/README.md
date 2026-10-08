# CarePlus Backend (Java prototype)

A small REST backend written in **pure Java** (JDK `com.sun.net.httpserver`). No Maven, Gradle, Spring or database server is needed. It:

- **serves the frontend** (`../frontend`) at http://localhost:8080
- **stores data on the server** as JSON files in `backend/data/` (created on first run)
- exposes a **REST API** with real server-side rules (validation, double-booking protection, status rules, analytics)

## Requirements
JDK 11 or newer (`javac -version` must work). Nothing else.

## Run
- Windows: double-click `run.bat`
- Mac / Linux: `sh run.sh`

Then open **http://localhost:8080**. Log in with any non-empty ID and password. Another port: `sh run.sh 9090`.

## Project layout
```
backend/
|-- run.sh  run.bat
`-- src/com/careplus/
    |-- Main.java         starts the HTTP server
    |-- Api.java          all REST endpoints and business rules
    |-- Store.java        thread-safe in-memory store, saved to data/*.json
    |-- Json.java         small JSON parser/writer (no libraries)
    `-- StaticFiles.java  serves the frontend files
```

## How the frontend uses the backend
When the site is opened through the server (`http://`), `frontend/js/core.js`:
1. loads all saved data from `GET /api/state` when a page opens,
2. saves every change with `PUT /api/data/{collection}`,
3. calls `POST /api/login` when you log in.

So Doctor, Patient and Admin can use **different browsers or computers** and still see the same data. If you open `frontend/index.html` directly (`file://`), it falls back to localStorage only.

## REST API
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | server status |
| POST | `/api/login` | body `{userId,password,role}`; any non-empty credentials work (simulated); returns token + display name |
| GET | `/api/state` | all collections (used by the frontend on load) |
| GET / PUT | `/api/data/{name}` | read / replace: users, appointments, records, feedback, notifications, settings, schedules |
| GET | `/api/appointments?status=&doctorId=&patientId=` | list with filters |
| POST | `/api/appointments` | create; validates fields and date; **409** if the doctor's slot is taken |
| PUT | `/api/appointments/{id}/status` | body `{status}`; finished/cancelled cannot change; only Confirmed can be Completed; notifies the patient |
| GET / POST | `/api/users` | list (`?role=&q=`) / create (validates email, role, duplicate email) |
| DELETE | `/api/users/{id}` | delete a user |
| GET | `/api/analytics` | totals, counts by status/department/doctor, completion and cancellation rate, average rating |

### Try it
```
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/login -d '{"userId":"adarsh","password":"1","role":"patient"}'
curl http://localhost:8080/api/analytics
```

## Honest limitations (prototype)
- Authentication is simulated; tokens are issued but not required by other endpoints.
- Storage is JSON files (last write wins), not a real database.
- The frontend saves whole collections, so two people editing the same collection at the same moment can overwrite each other.
- Fictional data only. Not for real medical use.

## Next steps toward production
Spring Boot + MySQL/PostgreSQL (JPA), password hashing and JWT, role checks on every endpoint, automated tests.
