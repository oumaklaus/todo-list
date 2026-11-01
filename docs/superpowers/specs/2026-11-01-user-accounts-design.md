# TaskFlow User Accounts Design

## Intent

Add real email/password accounts to TaskFlow so each person signs up or logs in before using the app and can access only their own tasks.

## Goals

- Provide in-app sign-up and login using display name, email, and password.
- Store user accounts in MongoDB with BCrypt password hashes; never store plaintext passwords.
- Issue signed JWT bearer tokens and persist the active session on Android.
- Scope every task read/write/delete operation to the authenticated user.
- Provide logout and return expired or invalid sessions to login.
- Keep local and Azure launch/deployment scripts usable with an explicit JWT secret.

## Non-goals

- Password reset, email verification, social login, administrator tools, refresh tokens, or multi-device session revocation.
- Migrating legacy ownerless tasks into a new user account; they remain inaccessible.

## Constraints

- Preserve the Kotlin/XML Android client, Ktor backend, and MongoDB storage.
- Passwords are 8–72 characters and account email matching is case-insensitive.
- JWTs expire after 30 days and contain only the user identifier and email.
- Authentication failures use generic messages and never reveal whether an email exists.

## Approaches

1. **JWT + BCrypt in the existing backend (selected):** minimal infrastructure, works locally and on Azure, and fits the current Retrofit/Ktor stack. The tradeoff is that issued access tokens cannot be individually revoked before expiry.
2. **Server-side opaque sessions:** immediate revocation is easier, but adds session persistence and cleanup to a small app.
3. **Managed identity provider:** strongest ready-made lifecycle features, but adds an external service and substantially changes deployment.

## Design

The backend adds a unique `users` collection, BCrypt hashing, `/auth/register` and `/auth/login`, and Ktor JWT authentication. Task documents gain `ownerId`; repository filters include it for all operations, preventing cross-account access even when another task ID is known.

The Android app launches `LoginActivity`, which supports both login and account creation. A SharedPreferences-backed session store retains the JWT; an OkHttp interceptor attaches it to API requests and clears it on protected-endpoint `401` responses. `MainActivity` adds logout and redirects to login when the session becomes invalid.

Validation exists at both boundaries. Backend rules are authoritative; the Android validator provides immediate matching feedback. Existing unauthenticated task endpoints become protected, while `/health` and `/auth/*` remain public.

