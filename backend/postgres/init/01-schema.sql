-- =============================================================
--  Sanos & Salvos — Schema inicial PostgreSQL
--  DB de escritura (fuente de verdad)
--  Ejecutado automáticamente al levantar el contenedor por
--  primera vez desde /docker-entrypoint-initdb.d/
-- =============================================================

-- Extensiones necesarias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- gen de UUIDs
CREATE EXTENSION IF NOT EXISTS "vector";       -- pgvector: embeddings para Motor de Coincidencias

-- =============================================================
-- TABLA: usuarios
-- Gestión de Usuarios (Micro 1)
-- La autenticación es OAuth2 con Google; google_id es el sub
-- del JWT de Google. Sin password_hash porque no hay auth local.
-- =============================================================
CREATE TABLE IF NOT EXISTS usuarios (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre          VARCHAR(100)         NOT NULL,
    apellido        VARCHAR(100)         NOT NULL,
    email           VARCHAR(255) UNIQUE  NOT NULL,
    google_id       VARCHAR(255) UNIQUE  NOT NULL,
    foto_perfil_url TEXT,
    activo          BOOLEAN              DEFAULT TRUE,
    created_at      TIMESTAMPTZ          DEFAULT NOW(),
    updated_at      TIMESTAMPTZ          DEFAULT NOW()
);

-- =============================================================
-- TABLA: reportes
-- Gestión de Mascotas (Micro 2)
-- Cada reporte es una mascota perdida o encontrada.
-- El campo `embedding` almacena el vector de similitud
-- generado por el Motor de Coincidencias (Micro 3) al
-- combinar texto e imagen del reporte.
-- =============================================================
CREATE TABLE IF NOT EXISTS reportes (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Clasificación del reporte
    tipo        VARCHAR(20)  NOT NULL CHECK (tipo IN ('perdido', 'encontrado')),
    animal      VARCHAR(20)  NOT NULL CHECK (animal IN ('perro', 'gato', 'otro')),
    estado      VARCHAR(20)  NOT NULL DEFAULT 'activo'
                    CHECK (estado IN ('activo', 'resuelto', 'eliminado')),

    -- Atributos de la mascota
    nombre      VARCHAR(100),
    raza        VARCHAR(100),
    color       VARCHAR(100),
    tamano      VARCHAR(20)  CHECK (tamano IN ('pequeño', 'mediano', 'grande')),
    descripcion TEXT,

    -- Ubicación (Región Metropolitana)
    lat         DECIMAL(10, 8) NOT NULL,
    lng         DECIMAL(11, 8) NOT NULL,
    zona        VARCHAR(255),  -- texto derivado de geocodificación inversa

    -- Embedding vectorial para el Motor de Coincidencias
    -- Dimensión 512: compatible con CLIP ViT-B/32 y sentence-transformers
    embedding   vector(512),

    -- Relación con usuario
    usuario_id  UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,

    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- =============================================================
-- TABLA: fotos
-- Fotos asociadas a un reporte, almacenadas en MinIO.
-- `bucket_key` es la ruta dentro del bucket de MinIO.
-- `url` es la URL pública/presignada para acceso desde el frontend.
-- =============================================================
CREATE TABLE IF NOT EXISTS fotos (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporte_id  UUID    NOT NULL REFERENCES reportes(id) ON DELETE CASCADE,
    url         TEXT    NOT NULL,
    bucket_key  TEXT    NOT NULL,
    orden       INTEGER DEFAULT 0,  -- orden de display (0 = foto principal)
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- =============================================================
-- TABLA: coincidencias
-- Motor de Coincidencias (Micro 3)
-- Resultado del algoritmo de matching entre un reporte "perdido"
-- y uno "encontrado". El score total es un promedio ponderado
-- de los scores individuales por dimensión.
-- =============================================================
CREATE TABLE IF NOT EXISTS coincidencias (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporte_perdido_id      UUID NOT NULL REFERENCES reportes(id) ON DELETE CASCADE,
    reporte_encontrado_id   UUID NOT NULL REFERENCES reportes(id) ON DELETE CASCADE,

    -- Score total (0.0 a 1.0) y scores por dimensión
    score_total             DECIMAL(5, 4) NOT NULL,
    score_nombre            DECIMAL(5, 4),
    score_raza              DECIMAL(5, 4),
    score_color             DECIMAL(5, 4),
    score_tamano            DECIMAL(5, 4),
    score_descripcion       DECIMAL(5, 4),
    score_ubicacion         DECIMAL(5, 4),  -- basado en distancia geográfica
    score_imagen            DECIMAL(5, 4),  -- similitud vectorial entre fotos

    -- Estado de la coincidencia (gestionado por los usuarios o moderadores)
    estado                  VARCHAR(20) DEFAULT 'pendiente'
                                CHECK (estado IN ('pendiente', 'confirmado', 'descartado')),

    -- Control de notificaciones
    notificado_perdido      BOOLEAN DEFAULT FALSE,
    notificado_encontrado   BOOLEAN DEFAULT FALSE,

    created_at              TIMESTAMPTZ DEFAULT NOW(),

    -- No puede existir el mismo par dos veces
    UNIQUE (reporte_perdido_id, reporte_encontrado_id)
);

-- =============================================================
-- TABLA: notificaciones
-- Notificaciones enviadas a los usuarios cuando el Motor de
-- Coincidencias encuentra un posible match.
-- =============================================================
CREATE TABLE IF NOT EXISTS notificaciones (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id      UUID    NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    coincidencia_id UUID    REFERENCES coincidencias(id) ON DELETE SET NULL,
    tipo            VARCHAR(50)  NOT NULL,  -- ej: "nueva_coincidencia", "reporte_resuelto"
    mensaje         TEXT         NOT NULL,
    leida           BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

-- =============================================================
-- ÍNDICES
-- =============================================================

-- Reportes: búsquedas frecuentes del frontend
CREATE INDEX IF NOT EXISTS idx_reportes_tipo       ON reportes(tipo);
CREATE INDEX IF NOT EXISTS idx_reportes_animal     ON reportes(animal);
CREATE INDEX IF NOT EXISTS idx_reportes_estado     ON reportes(estado);
CREATE INDEX IF NOT EXISTS idx_reportes_usuario    ON reportes(usuario_id);
CREATE INDEX IF NOT EXISTS idx_reportes_ubicacion  ON reportes(lat, lng);
CREATE INDEX IF NOT EXISTS idx_reportes_created    ON reportes(created_at DESC);

-- Reportes: búsqueda vectorial del Motor de Coincidencias
-- ivfflat: eficiente para datasets medianos; ajustar lists según volumen
CREATE INDEX IF NOT EXISTS idx_reportes_embedding
    ON reportes USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Fotos
CREATE INDEX IF NOT EXISTS idx_fotos_reporte ON fotos(reporte_id);

-- Coincidencias
CREATE INDEX IF NOT EXISTS idx_coincidencias_perdido
    ON coincidencias(reporte_perdido_id);
CREATE INDEX IF NOT EXISTS idx_coincidencias_encontrado
    ON coincidencias(reporte_encontrado_id);
CREATE INDEX IF NOT EXISTS idx_coincidencias_score
    ON coincidencias(score_total DESC);

-- Notificaciones
CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario
    ON notificaciones(usuario_id, leida, created_at DESC);

-- =============================================================
-- FUNCIÓN Y TRIGGERS: updated_at automático
-- =============================================================
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuarios_updated_at
    BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_reportes_updated_at
    BEFORE UPDATE ON reportes
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================
-- PUBLICACIÓN PARA DEBEZIUM CDC
-- Debezium usará esta publicación para capturar cambios y
-- publicarlos en Kafka. Se crea aquí para asegurar que exista
-- antes de que Debezium registre su conector.
-- =============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'ss_publication'
    ) THEN
        CREATE PUBLICATION ss_publication FOR TABLE
            usuarios,
            reportes,
            fotos,
            coincidencias,
            notificaciones;
    END IF;
END $$;
