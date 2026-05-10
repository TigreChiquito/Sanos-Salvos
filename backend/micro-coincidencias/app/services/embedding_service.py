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
            response = requests.get(url_foto, timeout=10)
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

    def generar_embedding_combinado(
        self,
        nombre: Optional[str],
        raza: Optional[str],
        color: Optional[str],
        descripcion: Optional[str],
        url_foto: Optional[str],
    ) -> np.ndarray:
        """
        Combina embeddings de texto e imagen en un único vector de 512 dims.

        Pesos de la combinación:
          - Texto: 60% (nombre + raza + color + descripción concatenados)
          - Imagen: 40% (primera foto del reporte)

        Si no hay imagen disponible, el texto ocupa el 100%.
        El vector resultante se normaliza a norma 1 (cosine similarity ready).
        """
        # Construir texto descriptivo del reporte
        partes = [p for p in [nombre, raza, color, descripcion] if p]
        texto = " ".join(partes) if partes else "mascota"

        emb_texto = self.generar_texto_embedding(texto)   # (512,)
        emb_imagen = self.generar_imagen_embedding(url_foto) if url_foto else None

        if emb_imagen is not None:
            combinado = 0.6 * emb_texto + 0.4 * emb_imagen
        else:
            combinado = emb_texto

        # Normalizar a norma unitaria para que cosine_similarity funcione correctamente
        combinado = normalize(combinado.reshape(1, -1)).flatten()
        return combinado.astype(np.float32)

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
