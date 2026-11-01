# TaskFlow User Accounts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add database-backed email/password accounts, authenticated per-user tasks, an Android login/sign-up page, persistent sessions, and logout.

**Architecture:** Ktor owns registration, BCrypt verification, JWT issuance, and authenticated task routing. Mongo repositories store unique users and owner-scoped tasks. Android owns validation and login state, persists the JWT in SharedPreferences, and attaches it with an OkHttp interceptor.

**Tech Stack:** Kotlin 2.0, Ktor 3, MongoDB Java driver, java-jwt, jBCrypt, Android XML/ViewBinding, Retrofit/OkHttp, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-01-user-accounts-design.md`

## Global Constraints

- Passwords are 8–72 characters and stored only as BCrypt hashes.
- Email lookup is trimmed and case-insensitive.
- JWTs expire after 30 days and require `JWT_SECRET` outside tests.
- Every task operation must include the authenticated `ownerId` in its repository query.
- Ownerless legacy tasks remain inaccessible.

## Review Focus

- Duplicate email registration returns conflict without creating a second user.
- Wrong-password and unknown-email login return the same unauthorized response.
- Knowing another user's task ID cannot update or delete it.
- Expired/invalid JWTs cannot access tasks and force the Android session back to login.
- Passwords never appear in API responses, logs, or persisted user fields outside `passwordHash`.

---

### Task 1: Backend account primitives

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/example/taskflow/model/Auth.kt`
- Create: `backend/src/main/kotlin/com/example/taskflow/auth/PasswordHasher.kt`
- Create: `backend/src/main/kotlin/com/example/taskflow/auth/JwtTokenService.kt`
- Create: `backend/src/main/kotlin/com/example/taskflow/repository/UserRepository.kt`
- Create: `backend/src/main/kotlin/com/example/taskflow/repository/MongoUserRepository.kt`
- Test: `backend/src/test/kotlin/com/example/taskflow/AuthPrimitivesTest.kt`
- Test: `backend/src/test/kotlin/com/example/taskflow/MongoUserRepositoryTest.kt`

**Interfaces:**
- Produces: `PasswordHasher`, `JwtTokenService`, `UserRepository`, `UserRecord`, and serializable auth request/response models.

- [ ] Write tests that prove BCrypt round-trips without plaintext equality, JWT claims verify, email uniqueness is case-insensitive, and stored records contain a hash.
- [ ] Run the focused tests and confirm they fail because the account primitives do not exist.
- [ ] Add the dependencies and minimal production types/repositories.
- [ ] Run focused and full backend tests; commit.

### Task 2: Authenticated backend routes and owner-scoped tasks

**Files:**
- Modify: `backend/src/main/kotlin/com/example/taskflow/Application.kt`
- Modify: `backend/src/main/kotlin/com/example/taskflow/model/Task.kt`
- Modify: `backend/src/main/kotlin/com/example/taskflow/repository/TaskRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/taskflow/repository/MongoTaskRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/taskflow/routes/TaskRoutes.kt`
- Create: `backend/src/main/kotlin/com/example/taskflow/routes/AuthRoutes.kt`
- Modify: `backend/src/test/kotlin/com/example/taskflow/TaskRoutesTest.kt`
- Modify: `backend/src/test/kotlin/com/example/taskflow/MongoTaskRepositoryTest.kt`
- Create: `backend/src/test/kotlin/com/example/taskflow/AuthRoutesTest.kt`

**Interfaces:**
- Consumes: Task 1 auth services and repositories.
- Produces: public `POST /auth/register`, `POST /auth/login`, protected `/tasks`, and owner-scoped task repository methods.

- [ ] Write route/repository tests for registration, duplicate email, generic login failure, valid login, missing/invalid token, and cross-user task isolation.
- [ ] Run them and confirm expected failures against the unauthenticated backend.
- [ ] Install Ktor JWT auth, add auth routes, and include `ownerId` in every task repository operation.
- [ ] Run focused and full backend tests; commit.

### Task 3: Android auth data, validation, and session transport

**Files:**
- Modify: `android/app/src/main/java/com/example/taskflow/data/ApiService.kt`
- Modify: `android/app/src/main/java/com/example/taskflow/data/ApiClient.kt`
- Create: `android/app/src/main/java/com/example/taskflow/data/AuthModels.kt`
- Create: `android/app/src/main/java/com/example/taskflow/data/AuthRepository.kt`
- Create: `android/app/src/main/java/com/example/taskflow/data/SessionStore.kt`
- Create: `android/app/src/main/java/com/example/taskflow/data/BearerAuthInterceptor.kt`
- Create: `android/app/src/main/java/com/example/taskflow/ui/AuthInputValidator.kt`
- Test: `android/app/src/test/java/com/example/taskflow/ui/AuthInputValidatorTest.kt`
- Test: `android/app/src/test/java/com/example/taskflow/data/RemoteAuthRepositoryTest.kt`
- Test: `android/app/src/test/java/com/example/taskflow/data/BearerAuthInterceptorTest.kt`

**Interfaces:**
- Produces: `AuthRepository.authenticate`, `SessionStore`, matching client validation, and automatic bearer/401 handling.

- [ ] Write tests for valid/invalid login and registration input, API result mapping, bearer attachment, and protected `401` session clearing.
- [ ] Run focused tests and confirm expected failures.
- [ ] Implement minimal models, repository, session abstraction, interceptor, and validator.
- [ ] Run focused and full Android unit tests; commit.

### Task 4: Android login/sign-up UI and logout

**Files:**
- Create: `android/app/src/main/java/com/example/taskflow/TaskFlowApplication.kt`
- Create: `android/app/src/main/java/com/example/taskflow/LoginActivity.kt`
- Create: `android/app/src/main/java/com/example/taskflow/ui/AuthViewModel.kt`
- Create: `android/app/src/main/res/layout/activity_login.xml`
- Create: `android/app/src/main/res/menu/main.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/example/taskflow/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Test: `android/app/src/test/java/com/example/taskflow/ui/AuthViewModelTest.kt`
- Test: `android/app/src/androidTest/java/com/example/taskflow/LoginActivityTest.kt`

**Interfaces:**
- Consumes: Task 3 repository/session types.
- Produces: launcher login/sign-up page, persisted entry to `MainActivity`, invalid-session redirect, and logout.

- [ ] Write ViewModel and instrumentation tests for login state, registration state, validation errors, launcher fields, and switching modes.
- [ ] Run focused tests and confirm expected failures.
- [ ] Implement Application initialization, login UI/ViewModel, manifest navigation, logout, and invalid-session redirect.
- [ ] Run Android unit tests, assemble, lint, and available instrumentation tests; commit.

### Task 5: Runtime scripts and documentation

**Files:**
- Modify: `run.sh`
- Modify: `deploy-azure.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes: backend `JWT_SECRET` requirement and public `/health` endpoint.
- Produces: generated local secret, required Azure secret, authenticated deployment instructions, and health checks that do not require a user token.

- [ ] Add shell-level checks that local startup provisions a persistent secret and deployment rejects a missing secret.
- [ ] Update scripts to use `/health` readiness and pass `JWT_SECRET`; update README/API documentation.
- [ ] Run `bash -n`, full backend tests/package, Android unit tests/assemble/lint, inspect the final diff, and commit.

