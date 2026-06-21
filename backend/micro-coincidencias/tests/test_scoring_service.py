"""Pruebas unitarias para app.services.scoring_service"""
import pytest
import numpy as np
from unittest.mock import patch

from app.services.scoring_service import calcular_scores, _calcular_total, PESOS


# Coordenadas de Santiago (mismo punto para aislar otras dimensiones)
LAT = -33.4489
LNG = -70.6693


class TestCalcularTotal:
    def test_sin_scores_activos_retorna_cero(self):
        scores = {k: None for k in PESOS}
        result = _calcular_total(scores)
        assert result == 0.0

    def test_un_solo_score_activo_redistribuye_peso(self):
        # Solo 'nombre' disponible con score 1.0 → total debe ser 1.0
        scores = {k: None for k in PESOS}
        scores["nombre"] = 1.0
        result = _calcular_total(scores)
        assert result == pytest.approx(1.0, abs=1e-4)

    def test_todos_los_scores_en_uno_retorna_uno(self):
        scores = {k: 1.0 for k in PESOS}
        result = _calcular_total(scores)
        assert result == pytest.approx(1.0, abs=1e-4)

    def test_todos_los_scores_en_cero_retorna_cero(self):
        scores = {k: 0.0 for k in PESOS}
        result = _calcular_total(scores)
        assert result == pytest.approx(0.0, abs=1e-4)

    def test_pesos_redistribuidos_correctamente(self):
        # Nombre (0.10) y raza (0.20) disponibles
        scores = {k: None for k in PESOS}
        scores["nombre"] = 1.0
        scores["raza"] = 0.0
        # peso_total = 0.10 + 0.20 = 0.30
        # total = 1.0*(0.10/0.30) + 0.0*(0.20/0.30) = 0.333...
        result = _calcular_total(scores)
        assert result == pytest.approx(1.0 / 3.0, abs=1e-4)


class TestCalcularScores:
    def test_nombres_iguales_score_uno(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a="Firulais", raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG,
                emb_texto_a=None, emb_imagen_a=None,
                nombre_b="Firulais", raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG,
                emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["nombre"] == pytest.approx(1.0, abs=1e-4)

    def test_nombres_ambos_none_score_none(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["nombre"] is None

    def test_tamano_iguales_score_uno(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a="mediano",
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b="mediano",
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["tamano"] == pytest.approx(1.0)

    def test_tamano_diferentes_score_cero(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a="pequeño",
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b="grande",
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["tamano"] == pytest.approx(0.0)

    def test_tamano_uno_none_score_none(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a="mediano",
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["tamano"] is None

    def test_color_parcial_match(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a="café con blanco",
                tamano_a=None, descripcion_a=None, lat_a=LAT, lng_a=LNG,
                emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b="café",
                tamano_b=None, descripcion_b=None, lat_b=LAT, lng_b=LNG,
                emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["color"] is not None
        assert 0.0 <= result["color"] <= 1.0

    def test_raza_exacta_score_alto(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a="Labrador Retriever",
                color_a=None, tamano_a=None, descripcion_a=None,
                lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b="Labrador Retriever",
                color_b=None, tamano_b=None, descripcion_b=None,
                lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["raza"] == pytest.approx(1.0, abs=1e-4)

    def test_texto_embedding_calcula_similitud_coseno(self):
        emb_a = np.array([1.0, 0.0, 0.0])
        emb_b = np.array([1.0, 0.0, 0.0])
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=emb_a, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=emb_b, emb_imagen_b=None,
            )
        assert result["descripcion"] == pytest.approx(1.0, abs=1e-4)
        assert result["imagen"] is None

    def test_embeddings_ortogonales_similitud_cero(self):
        emb_a = np.array([1.0, 0.0, 0.0])
        emb_b = np.array([0.0, 1.0, 0.0])
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=emb_a, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=emb_b, emb_imagen_b=None,
            )
        assert result["descripcion"] == pytest.approx(0.0, abs=1e-4)

    def test_sin_embeddings_descripcion_e_imagen_son_none(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert result["descripcion"] is None
        assert result["imagen"] is None

    def test_total_entre_cero_y_uno(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.5):
            result = calcular_scores(
                nombre_a="Max", raza_a="Beagle", color_a="blanco y negro",
                tamano_a="pequeño", descripcion_a=None,
                lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b="Max", raza_b="Beagle", color_b="blanco",
                tamano_b="pequeño", descripcion_b=None,
                lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        assert 0.0 <= result["total"] <= 1.0

    def test_resultado_contiene_todas_las_dimensiones(self):
        with patch("app.services.scoring_service.geo_score", return_value=0.9):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG, emb_texto_a=None, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG, emb_texto_b=None, emb_imagen_b=None,
            )
        for dim in PESOS:
            assert dim in result
        assert "total" in result


class TestEmbeddingsSeparados:
    def test_imagen_independiente_de_descripcion(self):
        """Embeddings de texto idénticos pero imágenes ortogonales → scores distintos."""
        emb_texto = np.array([1.0, 0.0, 0.0])
        emb_imagen_a = np.array([1.0, 0.0, 0.0])
        emb_imagen_b = np.array([0.0, 1.0, 0.0])
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG,
                emb_texto_a=emb_texto, emb_imagen_a=emb_imagen_a,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG,
                emb_texto_b=emb_texto, emb_imagen_b=emb_imagen_b,
            )
        assert result["descripcion"] == pytest.approx(1.0, abs=1e-4)
        assert result["imagen"] == pytest.approx(0.0, abs=1e-4)

    def test_imagen_none_cuando_falta_embedding_clip(self):
        """Sin embedding de imagen → score imagen es None (no penaliza)."""
        emb_texto = np.array([1.0, 0.0, 0.0])
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG,
                emb_texto_a=emb_texto, emb_imagen_a=None,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG,
                emb_texto_b=emb_texto, emb_imagen_b=None,
            )
        assert result["descripcion"] == pytest.approx(1.0, abs=1e-4)
        assert result["imagen"] is None

    def test_descripcion_none_cuando_falta_embedding_texto(self):
        """Sin embedding de texto → score descripcion es None."""
        emb_imagen = np.array([1.0, 0.0, 0.0])
        with patch("app.services.scoring_service.geo_score", return_value=0.8):
            result = calcular_scores(
                nombre_a=None, raza_a=None, color_a=None, tamano_a=None,
                descripcion_a=None, lat_a=LAT, lng_a=LNG,
                emb_texto_a=None, emb_imagen_a=emb_imagen,
                nombre_b=None, raza_b=None, color_b=None, tamano_b=None,
                descripcion_b=None, lat_b=LAT, lng_b=LNG,
                emb_texto_b=None, emb_imagen_b=emb_imagen,
            )
        assert result["imagen"] == pytest.approx(1.0, abs=1e-4)
        assert result["descripcion"] is None
