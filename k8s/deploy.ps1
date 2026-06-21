# ============================================================
#  Sanos & Salvos — Deploy a Kubernetes (Docker Desktop)
#  Uso: .\k8s\deploy.ps1
#       .\k8s\deploy.ps1 -Down   # eliminar todo
# ============================================================

param(
    [switch]$Down,
    [switch]$SkipWait
)

$NAMESPACE = "sanos-salvos"
$KUBECTL = "kubectl"

function Wait-Pods {
    param([string]$Label, [int]$Timeout = 300)
    Write-Host "  Esperando pods con label '$Label'..." -ForegroundColor Cyan
    & $KUBECTL wait pod -n $NAMESPACE -l $Label --for=condition=Ready --timeout="${Timeout}s" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ADVERTENCIA: timeout esperando '$Label' — continuando..." -ForegroundColor Yellow
    } else {
        Write-Host "  OK" -ForegroundColor Green
    }
}

# ── Eliminar todo ────────────────────────────────────────────
if ($Down) {
    Write-Host "Eliminando namespace $NAMESPACE..." -ForegroundColor Red
    & $KUBECTL delete namespace $NAMESPACE --ignore-not-found
    Write-Host "Listo." -ForegroundColor Green
    exit 0
}

# ── Verificar que k8s está activo ───────────────────────────
Write-Host "Verificando conexion a Kubernetes..." -ForegroundColor Cyan
& $KUBECTL cluster-info 2>&1 | Select-String "running" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: No hay conexion a un cluster Kubernetes." -ForegroundColor Red
    Write-Host "Activa Kubernetes en Docker Desktop: Settings > Kubernetes > Enable" -ForegroundColor Yellow
    exit 1
}
Write-Host "  Cluster OK" -ForegroundColor Green

# ── Paso 1: Namespace, Secrets, ConfigMaps ───────────────────
Write-Host ""
Write-Host "=== PASO 1: Namespace, Secrets y ConfigMaps ===" -ForegroundColor Magenta
$k8sDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& $KUBECTL apply -f "$k8sDir\00-namespace.yaml"
& $KUBECTL apply -f "$k8sDir\01-secrets.yaml"
& $KUBECTL apply -f "$k8sDir\02-configmap.yaml"
& $KUBECTL apply -f "$k8sDir\03-postgres-init.yaml"
& $KUBECTL apply -f "$k8sDir\04-nginx-config.yaml"

# ── Paso 2: Bases de datos ────────────────────────────────────
Write-Host ""
Write-Host "=== PASO 2: Bases de datos (PostgreSQL + MongoDB) ===" -ForegroundColor Magenta
& $KUBECTL apply -f "$k8sDir\infra\01-postgres.yaml"
& $KUBECTL apply -f "$k8sDir\infra\02-mongodb.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=postgres" 180
    Wait-Pods "app=mongodb" 120
}

# ── Paso 3: Kafka (Zookeeper primero) ─────────────────────────
Write-Host ""
Write-Host "=== PASO 3: Mensajeria (Zookeeper + Kafka) ===" -ForegroundColor Magenta
& $KUBECTL apply -f "$k8sDir\infra\03-zookeeper.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=zookeeper" 120
}

& $KUBECTL apply -f "$k8sDir\infra\04-kafka.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=kafka" 180
}

# ── Paso 4: MinIO y Debezium ──────────────────────────────────
Write-Host ""
Write-Host "=== PASO 4: Almacenamiento (MinIO) y CDC (Debezium) ===" -ForegroundColor Magenta
& $KUBECTL apply -f "$k8sDir\infra\05-minio.yaml"
& $KUBECTL apply -f "$k8sDir\infra\06-debezium.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=minio" 60
    Wait-Pods "app=debezium" 120
}

# ── Paso 5: Microservicios ─────────────────────────────────────
Write-Host ""
Write-Host "=== PASO 5: Microservicios ===" -ForegroundColor Magenta
& $KUBECTL apply -f "$k8sDir\microservices\01-micro-usuarios.yaml"
& $KUBECTL apply -f "$k8sDir\microservices\02-micro-mascotas.yaml"
& $KUBECTL apply -f "$k8sDir\microservices\03-micro-coincidencias.yaml"
& $KUBECTL apply -f "$k8sDir\microservices\04-orquestador.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=micro-usuarios" 180
    Wait-Pods "app=micro-mascotas" 180
    Wait-Pods "app=micro-coincidencias" 300
    Wait-Pods "app=orquestador" 120
}

# ── Paso 6: NGINX (punto de entrada) ──────────────────────────
Write-Host ""
Write-Host "=== PASO 6: NGINX (Load Balancer) ===" -ForegroundColor Magenta
& $KUBECTL apply -f "$k8sDir\nginx\nginx.yaml"

if (-not $SkipWait) {
    Wait-Pods "app=nginx" 60
}

# ── Paso 7: Registrar conector Debezium ───────────────────────
Write-Host ""
Write-Host "=== PASO 7: Registrando conector Debezium ===" -ForegroundColor Magenta
Write-Host "  Esperando 10s para que Debezium este listo..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

$connector = @{
    name   = "ss-postgres-connector"
    config = @{
        "connector.class"                    = "io.debezium.connector.postgresql.PostgresConnector"
        "database.hostname"                  = "postgres"
        "database.port"                      = "5432"
        "database.user"                      = "ss_admin"
        "database.password"                  = "ss_dev_password_2024"
        "database.dbname"                    = "sanos_salvos"
        "database.server.name"               = "ss"
        "topic.prefix"                        = "ss"
        "table.include.list"                 = "public.usuarios,public.reportes,public.fotos,public.coincidencias"
        "plugin.name"                        = "pgoutput"
        "publication.name"                   = "ss_publication"
        "slot.name"                          = "ss_debezium_slot"
        "heartbeat.interval.ms"              = "10000"
        "key.converter"                      = "org.apache.kafka.connect.json.JsonConverter"
        "value.converter"                    = "org.apache.kafka.connect.json.JsonConverter"
        "key.converter.schemas.enable"       = "false"
        "value.converter.schemas.enable"     = "false"
    }
} | ConvertTo-Json -Depth 10

$debeziumPod = & $KUBECTL get pod -n $NAMESPACE -l app=debezium -o jsonpath="{.items[0].metadata.name}" 2>&1
if ($debeziumPod -and $debeziumPod -notlike "*Error*") {
    & $KUBECTL exec -n $NAMESPACE $debeziumPod -- `
        curl -sf -X POST http://localhost:8083/connectors `
        -H "Content-Type: application/json" `
        -d $connector 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Conector Debezium registrado OK" -ForegroundColor Green
    } else {
        Write-Host "  ADVERTENCIA: no se pudo registrar el conector automaticamente." -ForegroundColor Yellow
        Write-Host "  Ejecuta manualmente: .\k8s\debezium-connector.ps1" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Pod Debezium no encontrado, salta registro automatico." -ForegroundColor Yellow
}

# ── Resumen ────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== DEPLOY COMPLETO ===" -ForegroundColor Green
Write-Host ""
Write-Host "URLs de acceso:" -ForegroundColor Cyan
Write-Host "  API (a traves de NGINX):  http://localhost:30080"
Write-Host "  MinIO Console:            http://localhost:30901"
Write-Host "  MinIO API (S3):           http://localhost:30900"
Write-Host ""
Write-Host "Estado de pods:" -ForegroundColor Cyan
& $KUBECTL get pods -n $NAMESPACE
Write-Host ""
Write-Host "Logs de un pod especifico:" -ForegroundColor Cyan
Write-Host "  kubectl logs -n $NAMESPACE -l app=micro-usuarios -f"
