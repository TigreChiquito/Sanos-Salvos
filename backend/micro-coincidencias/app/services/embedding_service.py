"""
Servicio de generación de embeddings vectoriales.

Combina dos modelos:
  - sentence-transformers: embeddings de texto (nombre + raza + color + descripción)
  - CLIP ViT-B/32: embeddings de imagen (primera foto del reporte)

El embedding final es una combinación ponderada normalizada de ambos,
de dimensión 512, compatible con la columna `embedding vector(512)` en PostgreSQL.

Ambos modelos se cargan una sola vez al inicio (singleton) y permanecen
en memoria. En CPU, la inferencia toma ~0.5s por reporte.
"""
import io
import logging
from typing import Optional

import clip
import numpy as np
import requests
import torch
from PIL import Image
from sentence_transformers import SentenceTransformer
from sklearn.preprocessing import normalize

from app.config import settings

log = logging.getLogger(__name__)


class EmbeddingService:
    _instance: Optional["EmbeddingService"] = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return

        log.info("Cargando modelos ML (puede tomar un momento)...")

        # Modelo de texto — multilingüe, soporta español
        self.texto_model = SentenceTransformer(settings.texto_model_name)
        self.texto_dim = self.texto_model.get_sentence_embedding_dimension()

        # Modelo de imagen — CLIP ViT-B/32
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.clip_model, self.clip_preprocess = clip.load("ViT-B/32", device=self.device)
        self.clip_model.eval()
        self.clip_dim = 512  # dimensión de salida de ViT-B/32

        self._initialized = True
        log.info("Modelos ML cargados — dispositivo: %s", self.device)

    def generar_texto_embedding(self, texto: str) -> np.ndarray:
        """
        Genera un embedding de 512 dimensiones para el texto dado.
        Si el modelo genera una dimensión distinta, se hace padding/truncado.
        """
        emb = self.texto_model.encode(texto, normalize_embeddings=True)
        return self._ajustar_dimension(emb, 512)

    def generar_imagen_embedding(self, url_foto: str) -> Optional[np.ndarray]:
        """
        Descarga la imagen desde la URL (MinIO presignada) y genera un
        embedding CLIP de 512 dimensiones.
        Retorna None si la imagen no está disponible o falla la descarga.
        """
        try:
            # Reemplaza la URL pública (localhost) por la URL interna de Docker
            url_interna = url_foto.replace(
                settings.minio_public_url, settings.minio_internal_url, 1
            )
            response = requests.get(url_interna, timeout=10)
            response.raise_for_status()

            imagen = Image.open(io.BytesIO(response.content)).convert("RGB")
            imagen_tensor = self.clip_preprocess(imagen).unsqueeze(0).to(self.device)

            with torch.no_grad():
                features = self.clip_model.encode_image(imagen_tensor)
                features = features / features.norm(dim=-1, keepdim=True)

            return features.cpu().numpy().flatten()

        except Exception as e:
            log.warning("No se pudo generar embedding de imagen desde %s: %s", url_foto, e)
            return None

    def generar_todos_embeddings(
        self,
        nombre: Optional[str],
        raza: Optional[str],
        color: Optional[str],
        descripcion: Optional[str],
        url_foto: Optional[str],
    ) -> tuple[np.ndarray, Optional[np.ndarray], np.ndarray]:
        """
        Genera los tres vectores necesarios para el motor de coincidencias:

        Returns:
            emb_texto (768-dim):     sentence-transformers nativo → score_descripcion
            emb_imagen (512-dim):    CLIP ViT-B/32 puro          → score_imagen (None si no hay foto)
            emb_combinado (512-dim): texto(60%) + imagen(40%)    → columna `embedding` (búsqueda ANN)
        """
        partes = [p for p in [nombre, raza, color, descripcion] if p]
        texto = " ".join(partes) if partes else "mascota"

        # Texto nativo: 768-dim (paraphrase-multilingual-mpnet-base-v2)
        emb_texto = self.texto_model.encode(texto, normalize_embeddings=True).astype(np.float32)

        # Imagen pura: 512-dim CLIP
        emb_imagen = self.generar_imagen_embedding(url_foto) if url_foto else None

        # Combinado: mezcla ponderada truncando texto a 512 para igualar dimensiones
        emb_texto_512 = self._ajustar_dimension(emb_texto, 512)
        if emb_imagen is not None:
            combinado = 0.6 * emb_texto_512 + 0.4 * emb_imagen
        else:
            combinado = emb_texto_512
        emb_combinado = normalize(combinado.reshape(1, -1)).flatten().astype(np.float32)

        return emb_texto, emb_imagen, emb_combinado

    def generar_embedding_combinado(
        self,
        nombre: Optional[str],
        raza: Optional[str],
        color: Optional[str],
        descripcion: Optional[str],
        url_foto: Optional[str],
    ) -> np.ndarray:
        """Compatibilidad: devuelve solo el vector combinado de 512 dims."""
        _, _, combinado = self.generar_todos_embeddings(nombre, raza, color, descripcion, url_foto)
        return combinado

    # ── Privado ────────────────────────────────────────────────

    @staticmethod
    def _ajustar_dimension(vector: np.ndarray, target_dim: int) -> np.ndarray:
        """Ajusta la dimensión del vector al target mediante padding o truncado."""
        current = vector.shape[0]
        if current == target_dim:
            return vector
        if current > target_dim:
            return vector[:target_dim]
        # Padding con ceros
        return np.concatenate([vector, np.zeros(target_dim - current)])


# Singleton global
embedding_service = EmbeddingService()
