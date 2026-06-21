# ============================================================
#  Registra el conector Debezium (PostgreSQL CDC) en Kubernetes
#  Uso: .\k8s\debezium-connector.ps1
# ============================================================

$NAMESPACE = "sanos-salvos"
$KUBECTL = "kubectl"

Write-Host "Buscando pod de Debezium..." -ForegroundColor Cyan
$debeziumPod = & $KUBECTL get pod -n $NAMESPACE -l app=debezium -o jsonpath="{.items[0].metadata.name}"

if (-not $debeziumPod) {
    Write-Host "ERROR: Pod de Debezium no encontrado en namespace '$NAMESPACE'" -ForegroundColor Red
    exit 1
}
Write-Host "  Pod: $debeziumPod" -ForegroundColor Green

$connectorJson = '{
  "name": "ss-postgres-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "ss_admin",
    "database.password": "ss_dev_password_2024",
    "database.dbname": "sanos_salvos",
    "topic.prefix": "ss",
    "table.include.list": "public.usuarios,public.reportes,public.fotos,public.coincidencias",
    "plugin.name": "pgoutput",
    "publication.name": "ss_publication",
    "slot.name": "ss_debezium_slot",
    "heartbeat.interval.ms": "10000",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false"
  }
}'

Write-Host "Verificando conectores existentes..." -ForegroundColor Cyan
$existing = & $KUBECTL exec -n $NAMESPACE $debeziumPod -- curl -sf http://localhost:8083/connectors 2>&1
Write-Host "  Conectores actuales: $existing"

Write-Host "Registrando conector PostgreSQL..." -ForegroundColor Cyan
$result = & $KUBECTL exec -n $NAMESPACE $debeziumPod -- `
    curl -sf -X POST http://localhost:8083/connectors `
    -H "Content-Type: application/json" `
    -d $connectorJson 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "  Conector registrado correctamente" -ForegroundColor Green
    Write-Host "  Respuesta: $result"
} else {
    Write-Host "  Error registrando conector: $result" -ForegroundColor Red
    Write-Host ""
    Write-Host "Intentando borrar conector existente y re-registrar..." -ForegroundColor Yellow
    & $KUBECTL exec -n $NAMESPACE $debeziumPod -- `
        curl -sf -X DELETE http://localhost:8083/connectors/ss-postgres-connector 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    & $KUBECTL exec -n $NAMESPACE $debeziumPod -- `
        curl -sf -X POST http://localhost:8083/connectors `
        -H "Content-Type: application/json" `
        -d $connectorJson
}

Write-Host ""
Write-Host "Estado del conector:" -ForegroundColor Cyan
& $KUBECTL exec -n $NAMESPACE $debeziumPod -- `
    curl -sf http://localhost:8083/connectors/ss-postgres-connector/status
