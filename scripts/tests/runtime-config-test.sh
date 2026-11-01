#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

ADB_LOG="$TEMP_DIR/adb.log"
cat > "$TEMP_DIR/fake-adb" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$ADB_LOG"
EOF
chmod +x "$TEMP_DIR/fake-adb"
export ADB_LOG
(
    export TASKFLOW_SOURCE_ONLY=1
    # shellcheck source=/dev/null
    source "$ROOT_DIR/run.sh"
    launch_taskflow "$TEMP_DIR/fake-adb" emulator-5554
)
grep -q -- '-s emulator-5554 shell am start -n com.example.taskflow/.LoginActivity' "$ADB_LOG" || {
    echo "runner did not launch the exported login activity" >&2
    cat "$ADB_LOG" >&2
    exit 1
}

TASKFLOW_RUN_DIR="$TEMP_DIR/run" "$ROOT_DIR/run.sh" ensure-secret >/dev/null
SECRET_FILE="$TEMP_DIR/run/jwt-secret"
[ -f "$SECRET_FILE" ] || { echo "missing generated JWT secret" >&2; exit 1; }
SECRET_ONE="$(cat "$SECRET_FILE")"
[[ "$SECRET_ONE" =~ ^[0-9a-f]{64}$ ]] || { echo "JWT secret is not 32 random bytes in hex" >&2; exit 1; }
[ "$(stat -c '%a' "$SECRET_FILE")" = "600" ] || { echo "JWT secret permissions are not 600" >&2; exit 1; }

TASKFLOW_RUN_DIR="$TEMP_DIR/run" "$ROOT_DIR/run.sh" ensure-secret >/dev/null
[ "$SECRET_ONE" = "$(cat "$SECRET_FILE")" ] || { echo "JWT secret was not persistent" >&2; exit 1; }

set +e
LOCAL_SHORT_OUTPUT="$(JWT_SECRET=too-short TASKFLOW_RUN_DIR="$TEMP_DIR/short" "$ROOT_DIR/run.sh" ensure-secret 2>&1)"
LOCAL_SHORT_STATUS=$?
set -e
[ "$LOCAL_SHORT_STATUS" -ne 0 ] || { echo "local runner accepted a short JWT_SECRET" >&2; exit 1; }
grep -q "at least 32 bytes" <<<"$LOCAL_SHORT_OUTPUT" || {
    echo "local runner did not reject a short JWT_SECRET clearly" >&2
    exit 1
}

set +e
DEPLOY_OUTPUT="$(
    env -u JWT_SECRET \
        AZURE_RESOURCE_GROUP=test-rg \
        AZURE_APP_NAME=test-app \
        MONGODB_URI=mongodb://example.invalid/taskflow \
        "$ROOT_DIR/deploy-azure.sh" 2>&1
)"
DEPLOY_STATUS=$?
set -e
[ "$DEPLOY_STATUS" -ne 0 ] || { echo "deployment accepted missing JWT_SECRET" >&2; exit 1; }
grep -q "Set JWT_SECRET" <<<"$DEPLOY_OUTPUT" || {
    echo "deployment did not explain the missing JWT_SECRET" >&2
    printf '%s\n' "$DEPLOY_OUTPUT" >&2
    exit 1
}

set +e
SHORT_SECRET_OUTPUT="$(
    JWT_SECRET=too-short \
        AZURE_RESOURCE_GROUP=test-rg \
        AZURE_APP_NAME=test-app \
        MONGODB_URI=mongodb://example.invalid/taskflow \
        "$ROOT_DIR/deploy-azure.sh" 2>&1
)"
SHORT_SECRET_STATUS=$?
set -e
[ "$SHORT_SECRET_STATUS" -ne 0 ] || { echo "deployment accepted a short JWT_SECRET" >&2; exit 1; }
grep -q "at least 32 bytes" <<<"$SHORT_SECRET_OUTPUT" || {
    echo "deployment did not reject a short JWT_SECRET clearly" >&2
    printf '%s\n' "$SHORT_SECRET_OUTPUT" >&2
    exit 1
}

echo "runtime configuration checks passed"
