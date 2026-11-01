# TaskFlow

TaskFlow is an Android todo-list application with a Kotlin client and a
Ktor/MongoDB backend.

## Features

- Create an account and log in with email and password.
- Keep every user's tasks private and isolated from other accounts.
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

This starts the API on port 8081, creates a persistent local JWT signing secret
under `.run/`, configures `adb reverse`, builds the APK, installs it, and
launches TaskFlow. Docker is only an optional local MongoDB fallback.

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
read -rsp "JWT secret: " JWT_SECRET && export JWT_SECRET && printf '\n'
./deploy-azure.sh
```

Generate a deployment secret with `openssl rand -hex 32`. Keep it stable for
the lifetime of the deployment; changing it signs out every installed app.

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
| POST | `/auth/register` | Create an account and receive a JWT |
| POST | `/auth/login` | Log in and receive a JWT |
| GET | `/tasks` | List the authenticated user's tasks |
| POST | `/tasks` | Create a task for the authenticated user |
| PUT | `/tasks/{id}` | Edit or complete an owned task |
| DELETE | `/tasks/{id}` | Delete an owned task |

Task endpoints require `Authorization: Bearer TOKEN`. Passwords are stored as
BCrypt hashes, and tokens expire after 30 days.

## Build and tests

```bash
./gradlew :backend:test azurePackage
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The project includes backend route/repository tests, Android unit tests, and
Espresso login/task-screen tests.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Limitations

Password reset and email verification are not implemented. Play Store
publishing is not configured.
