#!/bin/bash
# =============================================================
#  Sanos & Salvos — Registro del conector Debezium
#  Ejecutar DESPUÉS de que todos los servicios estén healthy:
#    ./scripts/register-debezium.sh
# =============================================================

set -euo pipefail

DEBEZIUM_URL="http://localhost:8083"
CONNECTOR_FILE="$(dirname "$0")/../kafka/connectors/debezium-connector.json"

# Cargar variables del .env
if [ -f "$(dirname "$0")/../.env" ]; then
  export $(grep -v '^#' "$(dirname "$0")/../.env" | xargs)
fi

echo "⏳  Esperando que Debezium esté listo..."
until curl -sf "${DEBEZIUM_URL}/connectors" > /dev/null; do
  sleep 3
  echo "   ...todavía iniciando"
done
echo "✅  Debezium está listo."

# Reemplazar variables en el JSON del conector
CONNECTOR_JSON=$(cat "$CONNECTOR_FILE" \
  | sed "s|\${file:/kafka/connect.properties:POSTGRES_USER}|${POSTGRES_USER}|g" \
  | sed "s|\${file:/kafka/connect.properties:POSTGRES_PASSWORD}|${POSTGRES_PASSWORD}|g" \
  | sed "s|\${file:/kafka/connect.properties:POSTGRES_DB}|${POSTGRES_DB}|g")

CONNECTOR_NAME=$(echo "$CONNECTOR_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['name'])")

# Verificar si el conector ya existe
if curl -sf "${DEBEZIUM_URL}/connectors/${CONNECTOR_NAME}" > /dev/null 2>&1; then
  echo "ℹ️   El conector '${CONNECTOR_NAME}' ya existe. Actualizando configuración..."
  curl -s -X PUT \
    -H "Content-Type: application/json" \
    --data "$(echo "$CONNECTOR_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d['config']))")" \
    "${DEBEZIUM_URL}/connectors/${CONNECTOR_NAME}/config" | python3 -m json.tool
else
  echo "🔌  Registrando conector '${CONNECTOR_NAME}'..."
  curl -s -X POST \
    -H "Content-Type: application/json" \
    --data "$CONNECTOR_JSON" \
    "${DEBEZIUM_URL}/connectors" | python3 -m json.tool
fi

echo ""
echo "📋  Estado actual de conectores:"
curl -s "${DEBEZIUM_URL}/connectors?expand=status" | python3 -m json.tool
