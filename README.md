# TaskFlow

TaskFlow is an Android todo-list application written in Kotlin. It uses a Ktor
backend and MongoDB to keep each account's tasks private.

## Features

- Account registration and login
- Add, edit, complete, reopen, and delete tasks
- Notes, due dates, priorities, and categories
- All, Today, Upcoming, and Done filters
- Search by title, notes, or category

## Project structure

```text
todo-list/
├── android/    Android application
├── backend/    Ktor API
└── README.md
```

## Run the Android app

The app uses the hosted backend by default:

```text
https://taskflow-api-2db81ce1.azurewebsites.net/
```

MongoDB and the local backend do not need to be running.

1. In IntelliJ IDEA or Android Studio, select **File > Open**.
2. Open the `android` folder as its own project.
3. Wait for Gradle sync and indexing to finish.
4. Connect and unlock your phone.
5. Enable **Developer options > USB debugging** and accept the phone's prompt.
6. Select the `app` run configuration and your phone in the top toolbar.
7. Click the green Run button.

If IntelliJ does not show Android options, enable the Android plugin and sync the
project with Gradle files.

## Run the backend locally (optional)

1. Start MongoDB locally.
2. Open the project root in IntelliJ IDEA.
3. Open `backend/src/main/kotlin/com/example/taskflow/Application.kt`.
4. Click the Run icon beside `fun main()`.

The backend defaults to `http://localhost:8080` and uses a local development JWT
secret automatically. These environment variables can override its settings:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | API port |
| `MONGODB_URI` | `mongodb://localhost:27017` | MongoDB connection |
| `MONGODB_DB` | `taskflow` | Database name |
| `JWT_SECRET` | Local development value | Token-signing secret |

## API

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Check the API status |
| POST | `/auth/register` | Create an account |
| POST | `/auth/login` | Log in |
| GET | `/tasks` | List the user's tasks |
| POST | `/tasks` | Create a task |
| PUT | `/tasks/{id}` | Update or complete a task |
| DELETE | `/tasks/{id}` | Delete a task |

## Presentation order

1. Connect the phone and select it in the IDE.
2. Run the `app` configuration.
3. Register an account.
4. Add a task with a category, priority, and due date.
5. Edit it, complete it, filter the list, and delete it.
