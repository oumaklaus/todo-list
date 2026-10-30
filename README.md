# TaskFlow

TaskFlow is an Android todo-list application with a Kotlin client and a
Ktor/MongoDB backend.

## Features

- Add, edit, complete, reopen, and delete tasks.
- Optional notes, due date, priority, and category.
- All, Today, Upcoming, and Done filters.
- Search across title, notes, and category.
- Loading, empty, retry, and connection-error states.

## Architecture

```text
Android app (Kotlin/XML) -> HTTPS/JSON -> Ktor API on Azure App Service
                                          |
                                          v
                              Cosmos DB for MongoDB or MongoDB Atlas
```

Azure App Service hosts the executable Ktor JAR. `MONGODB_URI` can be an Azure
Cosmos DB for MongoDB connection string or an Atlas connection string.

## Local run

Connect an Android phone with USB debugging enabled, then run:

```bash
./run.sh
```

This starts the API on port 8081, configures `adb reverse`, builds the APK,
installs it, and launches TaskFlow. Docker is only an optional local MongoDB
fallback.

```bash
./run.sh backend
./run.sh app
./run.sh stop
```

## Azure deployment

The deployment script creates or reuses a Linux Azure App Service running Java
21 and deploys `backend/build/libs/taskflow-backend-all.jar`.

Prerequisites:

1. Install Azure CLI and run `az login`.
2. Create a separate Cosmos DB for MongoDB database (or Atlas database) for
   TaskFlow and obtain its connection string.
3. Choose globally unique Azure app and resource-group names.

```bash
export AZURE_RESOURCE_GROUP="taskflow-rg"
export AZURE_APP_NAME="UNIQUE-TASKFLOW-API-NAME"
export AZURE_LOCATION="eastus"
export AZURE_SKU="F1"
read -rsp "MongoDB URI: " MONGODB_URI && export MONGODB_URI && printf '\n'
export MONGODB_DB="taskflow"
./deploy-azure.sh
```

`F1` is requested by default to minimize cost, but availability and charges
depend on the Azure subscription and region. Override `AZURE_SKU` if required.

After deployment, install the cloud-configured app:

```bash
./run.sh cloud https://UNIQUE-TASKFLOW-API-NAME.azurewebsites.net
```

The phone then connects directly to Azure and no longer needs the development
computer.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Process health |
| GET | `/tasks` | Database readiness and task list |
| POST | `/tasks` | Create task |
| PUT | `/tasks/{id}` | Edit or complete task |
| DELETE | `/tasks/{id}` | Delete task |

## Build and tests

```bash
./gradlew :backend:test azurePackage
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Verified coverage: 19 backend tests, 26 Android unit tests, 3 emulator UI tests,
plus full emulator CRUD and MongoDB restart persistence.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Limitations

This is a single-user application without login. Do not store sensitive
information. Play Store publishing is not configured.
