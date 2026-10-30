#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AZURE_RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-}"
AZURE_APP_NAME="${AZURE_APP_NAME:-}"
AZURE_LOCATION="${AZURE_LOCATION:-eastus}"
AZURE_PLAN_NAME="${AZURE_PLAN_NAME:-${AZURE_APP_NAME}-plan}"
AZURE_SKU="${AZURE_SKU:-F1}"
AZURE_RUNTIME="${AZURE_RUNTIME:-JAVA:21-java21}"
MONGODB_URI="${MONGODB_URI:-}"
MONGODB_DB="${MONGODB_DB:-taskflow}"
JAR="$ROOT_DIR/backend/build/libs/taskflow-backend-all.jar"

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

[ -n "$AZURE_RESOURCE_GROUP" ] || fail "Set AZURE_RESOURCE_GROUP for TaskFlow."
[ -n "$AZURE_APP_NAME" ] || fail "Set AZURE_APP_NAME to a globally unique TaskFlow app name."
[ -n "$MONGODB_URI" ] || fail "Set MONGODB_URI to TaskFlow's Cosmos DB for MongoDB or Atlas URI."
command -v az >/dev/null 2>&1 || fail "Install Azure CLI, then run 'az login'."
az account show >/dev/null 2>&1 || fail "Run 'az login' before deploying."

cd "$ROOT_DIR"
./gradlew clean azurePackage --console=plain

az group create --name "$AZURE_RESOURCE_GROUP" --location "$AZURE_LOCATION" --output none
if ! az appservice plan show --resource-group "$AZURE_RESOURCE_GROUP" --name "$AZURE_PLAN_NAME" >/dev/null 2>&1; then
    az appservice plan create \
        --resource-group "$AZURE_RESOURCE_GROUP" \
        --name "$AZURE_PLAN_NAME" \
        --location "$AZURE_LOCATION" \
        --sku "$AZURE_SKU" \
        --is-linux \
        --output none
fi
if ! az webapp show --resource-group "$AZURE_RESOURCE_GROUP" --name "$AZURE_APP_NAME" >/dev/null 2>&1; then
    az webapp create \
        --resource-group "$AZURE_RESOURCE_GROUP" \
        --plan "$AZURE_PLAN_NAME" \
        --name "$AZURE_APP_NAME" \
        --runtime "$AZURE_RUNTIME" \
        --https-only true \
        --output none
fi

az webapp config appsettings set \
    --resource-group "$AZURE_RESOURCE_GROUP" \
    --name "$AZURE_APP_NAME" \
    --settings "MONGODB_URI=$MONGODB_URI" "MONGODB_DB=$MONGODB_DB" \
    --output none
az webapp deploy \
    --resource-group "$AZURE_RESOURCE_GROUP" \
    --name "$AZURE_APP_NAME" \
    --src-path "$JAR" \
    --type jar \
    --clean true \
    --restart true \
    --output none

APP_URL="https://$(az webapp show --resource-group "$AZURE_RESOURCE_GROUP" --name "$AZURE_APP_NAME" --query defaultHostName --output tsv)"
for _ in $(seq 1 60); do
    if curl -fsS --max-time 5 "$APP_URL/tasks" >/dev/null 2>&1; then
        printf 'TaskFlow deployed: %s\n' "$APP_URL"
        printf 'Install the cloud app with: ./run.sh cloud %s\n' "$APP_URL"
        exit 0
    fi
    sleep 5
done
fail "Azure deployed the JAR, but $APP_URL/tasks did not become ready. Check Azure App Service logs."
