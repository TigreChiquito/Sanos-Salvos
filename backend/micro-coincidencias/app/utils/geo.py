from haversine import haversine, Unit


def distancia_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """
    Calcula la distancia en kilómetros entre dos coordenadas geográficas
    usando la fórmula de Haversine.
    """
    return haversine((lat1, lng1), (lat2, lng2), unit=Unit.KILOMETERS)


def score_ubicacion(lat1: float, lng1: float,
                    lat2: float, lng2: float,
                    max_km: float = 10.0) -> float:
    """
    Convierte la distancia geográfica a un score de 0.0 a 1.0.
    - 0 km  → 1.0 (mismo lugar)
    - max_km → 0.0 (fuera del radio)
    - Más de max_km → 0.0 (no se considera candidato válido por ubicación)
    """
    dist = distancia_km(lat1, lng1, lat2, lng2)
    return max(0.0, round(1.0 - (dist / max_km), 4))
