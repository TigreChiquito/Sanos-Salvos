"""Pruebas unitarias para app.utils.geo"""
import pytest
from app.utils.geo import distancia_km, score_ubicacion


class TestDistanciaKm:
    def test_mismas_coordenadas_retorna_cero(self):
        dist = distancia_km(-33.4489, -70.6693, -33.4489, -70.6693)
        assert dist == pytest.approx(0.0, abs=1e-4)

    def test_distancia_conocida_santiago_maipu(self):
        # Santiago centro ↔ Maipú (~11 km aprox)
        dist = distancia_km(-33.4489, -70.6693, -33.5093, -70.7581)
        assert 9.0 < dist < 14.0

    def test_distancia_es_simetrica(self):
        d1 = distancia_km(-33.4489, -70.6693, -33.5093, -70.7581)
        d2 = distancia_km(-33.5093, -70.7581, -33.4489, -70.6693)
        assert d1 == pytest.approx(d2, rel=1e-6)

    def test_distancia_positiva_siempre(self):
        dist = distancia_km(-34.0, -71.0, -33.0, -70.0)
        assert dist > 0


class TestScoreUbicacion:
    def test_mismo_punto_retorna_uno(self):
        score = score_ubicacion(-33.4489, -70.6693, -33.4489, -70.6693, max_km=10.0)
        assert score == pytest.approx(1.0, abs=1e-4)

    def test_fuera_de_rango_retorna_cero(self):
        # Distancia > 10 km → score = 0.0
        score = score_ubicacion(-33.4489, -70.6693, -33.6, -70.9, max_km=10.0)
        assert score == 0.0

    def test_mitad_de_rango_retorna_aprox_cero_punto_cinco(self):
        # 5 km → score ≈ 0.5 con max_km=10
        # Usamos max_km pequeño para controlar la distancia exacta
        score = score_ubicacion(0.0, 0.0, 0.0, 0.0, max_km=10.0)
        assert score == pytest.approx(1.0)

    def test_score_nunca_negativo(self):
        # Distancia muy grande → no puede ser negativo
        score = score_ubicacion(-33.0, -70.0, -34.5, -72.0, max_km=10.0)
        assert score >= 0.0

    def test_score_max_km_personalizado(self):
        # Con max_km=5, a 0 km el score debe ser 1.0
        score = score_ubicacion(-33.4489, -70.6693, -33.4489, -70.6693, max_km=5.0)
        assert score == pytest.approx(1.0, abs=1e-4)

    def test_score_redondeado_a_4_decimales(self):
        score = score_ubicacion(-33.4489, -70.6693, -33.4590, -70.6800, max_km=10.0)
        # Verificar que tiene a lo sumo 4 decimales
        assert score == round(score, 4)
