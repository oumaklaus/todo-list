#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${TASKFLOW_RUN_DIR:-$ROOT_DIR/.run}"
PID_FILE="$RUN_DIR/backend.pid"
LOG_FILE="$RUN_DIR/backend.log"
BACKEND_BIN="$ROOT_DIR/backend/build/install/taskflow-backend/bin/taskflow-backend"
ANDROID_DIR="$ROOT_DIR/android"
APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
MONGO_CONTAINER="taskflow-mongo"
PORT="${PORT:-8081}"
MONGODB_DB="${MONGODB_DB:-taskflow}"

mkdir -p "$RUN_DIR"

log() {
    printf '%s\n' "$*"
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

ensure_jwt_secret() {
    if [ -n "${JWT_SECRET:-}" ]; then
        local secret_bytes
        secret_bytes="$(printf '%s' "$JWT_SECRET" | wc -c | tr -d ' ')"
        [ "$secret_bytes" -ge 32 ] \
            || fail "JWT_SECRET must contain at least 32 bytes."
        export JWT_SECRET
        return 0
    fi

    local secret_file="$RUN_DIR/jwt-secret"
    if [ ! -s "$secret_file" ]; then
        log "Creating a local TaskFlow JWT signing secret..."
        umask 077
        if command -v openssl >/dev/null 2>&1; then
            openssl rand -hex 32 > "$secret_file"
        else
            od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$secret_file"
            printf '\n' >> "$secret_file"
        fi
    fi
    chmod 600 "$secret_file"
    JWT_SECRET="$(cat "$secret_file")"
    [ "$(printf '%s' "$JWT_SECRET" | wc -c | tr -d ' ')" -ge 32 ] \
        || fail "The stored JWT secret is invalid. Delete $secret_file and try again."
    export JWT_SECRET
}

process_cmdline() {
    local pid="$1"
    if [ -r "/proc/$pid/cmdline" ]; then
        tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true
    else
        ps -p "$pid" -o command= 2>/dev/null || true
    fi
}

wait_for_exit() {
    local pid="$1"
    local _
    for _ in $(seq 1 25); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 0.2
    done
    kill -9 "$pid" 2>/dev/null || true
}

stop_backend() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            if process_cmdline "$pid" | grep -qE 'taskflow|com\.example\.taskflow'; then
                log "Stopping previous TaskFlow backend (PID $pid)..."
                kill "$pid" 2>/dev/null || true
                wait_for_exit "$pid"
            else
                log "Ignoring stale TaskFlow PID file for unrelated PID $pid."
            fi
        fi
        rm -f "$PID_FILE"
    fi

    local port_pid=""
    if command -v ss >/dev/null 2>&1; then
        port_pid="$(ss -ltnp 2>/dev/null \
            | grep -E "[:.]${PORT}[[:space:]]" \
            | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
            | head -n 1 || true)"
    elif command -v lsof >/dev/null 2>&1; then
        port_pid="$(lsof -ti "tcp:${PORT}" 2>/dev/null | head -n 1 || true)"
    fi

    if [ -n "$port_pid" ] && kill -0 "$port_pid" 2>/dev/null; then
        if process_cmdline "$port_pid" | grep -qE 'taskflow|com\.example\.taskflow'; then
            log "Stopping old TaskFlow backend on port $PORT (PID $port_pid)..."
            kill "$port_pid" 2>/dev/null || true
            wait_for_exit "$port_pid"
        else
            fail "Port $PORT is used by another program (PID $port_pid)."
        fi
    fi
}

local_mongo_is_ready() {
    (exec 3<>/dev/tcp/127.0.0.1/27017) >/dev/null 2>&1
}

start_mongo() {
    if [ -n "${MONGODB_URI:-}" ]; then
        log "Using the configured MongoDB URI (Atlas or another external server)."
        return 0
    fi

    export MONGODB_URI="mongodb://localhost:27017"
    if local_mongo_is_ready; then
        log "Using MongoDB already running on localhost:27017."
        return 0
    fi

    command -v docker >/dev/null 2>&1 \
        || fail "MongoDB is not reachable and Docker is not installed. Set MONGODB_URI to an Atlas connection string."

    if docker inspect "$MONGO_CONTAINER" >/dev/null 2>&1; then
        log "Starting the TaskFlow MongoDB container..."
        docker start "$MONGO_CONTAINER" >/dev/null
    else
        log "Creating the TaskFlow MongoDB container..."
        if [ "$(uname -s)" = "Linux" ]; then
            docker run -d --name "$MONGO_CONTAINER" --network host \
                -v taskflow_mongo_data:/data/db mongo:7.0 \
                --bind_ip 127.0.0.1 >/dev/null
        else
            docker run -d --name "$MONGO_CONTAINER" -p 127.0.0.1:27017:27017 \
                -v taskflow_mongo_data:/data/db mongo:7.0 >/dev/null
        fi
    fi

    log "Waiting for MongoDB..."
    local _
    for _ in $(seq 1 60); do
        if docker exec "$MONGO_CONTAINER" mongosh --quiet \
            --eval 'quit(db.adminCommand({ping:1}).ok ? 0 : 1)' >/dev/null 2>&1; then
            log "MongoDB is ready."
            return 0
        fi
        sleep 1
    done
    fail "MongoDB did not become ready. Check: docker logs $MONGO_CONTAINER"
}

start_backend() {
    ensure_jwt_secret
    stop_backend
    log "Building the TaskFlow backend..."
    (cd "$ROOT_DIR" && ./gradlew :backend:installDist --console=plain)
    [ -x "$BACKEND_BIN" ] || fail "Backend executable was not created."

    : > "$LOG_FILE"
    log "Starting TaskFlow API on http://localhost:$PORT ..."
    (
        cd "$ROOT_DIR"
        if command -v setsid >/dev/null 2>&1; then
            setsid env \
                PORT="$PORT" \
                MONGODB_URI="$MONGODB_URI" \
                MONGODB_DB="$MONGODB_DB" \
                JWT_SECRET="$JWT_SECRET" \
                "$BACKEND_BIN" >>"$LOG_FILE" 2>&1 < /dev/null &
        else
            PORT="$PORT" \
            MONGODB_URI="$MONGODB_URI" \
            MONGODB_DB="$MONGODB_DB" \
            JWT_SECRET="$JWT_SECRET" \
            nohup "$BACKEND_BIN" >>"$LOG_FILE" 2>&1 &
        fi
        echo $! > "$PID_FILE"
    )

    local backend_pid
    backend_pid="$(cat "$PID_FILE")"
    local _
    for _ in $(seq 1 60); do
        if curl -sf -m 3 "http://localhost:$PORT/health" >/dev/null 2>&1; then
            log "TaskFlow API is ready."
            return 0
        fi
        kill -0 "$backend_pid" 2>/dev/null || break
        sleep 1
    done

    tail -n 30 "$LOG_FILE" >&2 || true
    fail "TaskFlow API did not start. See $LOG_FILE"
}

find_adb() {
    if [ -n "${ADB:-}" ] && [ -x "$ADB" ]; then
        printf '%s\n' "$ADB"
        return 0
    fi
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi
    local sdk
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk"; do
        if [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ]; then
            printf '%s\n' "$sdk/platform-tools/adb"
            return 0
        fi
    done
    return 1
}

select_device() {
    local adb="$1"
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        printf '%s\n' "$ANDROID_SERIAL"
        return 0
    fi

    local candidates candidate
    candidates="$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    for candidate in $candidates; do
        if [ "$("$adb" -s "$candidate" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    [ -n "$candidates" ] && printf '%s\n' "$candidates" | head -n 1
}

start_app() {
    local cloud_url="${1:-}"
    local adb
    adb="$(find_adb)" || fail "adb was not found. Install Android SDK platform-tools."

    local device
    device="$(select_device "$adb")"
    if [ -z "$device" ]; then
        local pending
        pending="$("$adb" devices | awk 'NR > 1 && $1 != "" { print $1 " (" $2 ")"; exit }')"
        [ -z "$pending" ] \
            || fail "Device $pending is not ready. Unlock it and accept the USB debugging prompt."
        fail "No Android device detected. Connect a phone with USB debugging, then run this command again."
    fi

    local status
    status="$("$adb" devices | awk -v d="$device" '$1 == d { print $2; exit }')"
    [ "$status" = "device" ] || fail "Device $device is not ready (state: ${status:-not found})."

    local is_emulator api_base_url
    is_emulator="$("$adb" -s "$device" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$cloud_url" ]; then
        api_base_url="${cloud_url%/}/"
        log "Using cloud API: $api_base_url"
    elif [ "$is_emulator" = "1" ]; then
        api_base_url="http://10.0.2.2:${PORT}/"
        log "Using emulator API: $api_base_url"
    else
        log "Forwarding port $PORT to the phone with adb reverse..."
        "$adb" -s "$device" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null \
            || fail "adb reverse failed. Reconnect the phone and try again."
        api_base_url="http://127.0.0.1:${PORT}/"
    fi

    log "Building the TaskFlow Android app..."
    (cd "$ANDROID_DIR" && ./gradlew assembleDebug -PapiBaseUrl="$api_base_url" --console=plain)
    [ -f "$APK" ] || fail "APK was not created at $APK"

    log "Installing TaskFlow on $device..."
    "$adb" -s "$device" install -r "$APK"
    log "Launching TaskFlow..."
    "$adb" -s "$device" shell am start -n com.example.taskflow/.MainActivity >/dev/null
    log "TaskFlow is open on $device."
}

case "${1:-all}" in
    all|start)
        start_mongo
        start_backend
        start_app
        ;;
    backend)
        start_mongo
        start_backend
        ;;
    app)
        curl -sf -m 3 "http://localhost:$PORT/health" >/dev/null \
            || fail "Local TaskFlow API is not running. Use ./run.sh first."
        start_app
        ;;
    ensure-secret)
        ensure_jwt_secret
        log "TaskFlow JWT signing secret is ready in $RUN_DIR."
        ;;
    cloud)
        cloud_url="${2:-${API_BASE_URL:-}}"
        [ -n "$cloud_url" ] || fail "Usage: ./run.sh cloud https://YOUR-APP.azurewebsites.net"
        start_app "$cloud_url"
        ;;
    stop)
        stop_backend
        log "TaskFlow backend stopped. MongoDB was left running."
        ;;
    *)
        log "Usage: ./run.sh [all|backend|app|cloud URL|stop|ensure-secret]"
        exit 1
        ;;
esac
