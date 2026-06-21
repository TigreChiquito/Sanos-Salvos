from prometheus_client import Counter, Histogram

KAFKA_EVENTOS = Counter(
    'ss_kafka_eventos_total',
    'Eventos Kafka recibidos por tipo de evento',
    ['tipo']
)

MATCHING_PROCESADOS = Counter(
    'ss_matching_procesados_total',
    'Reportes enviados al motor de matching'
)

MATCHING_CANDIDATOS = Histogram(
    'ss_matching_candidatos',
    'Candidatos evaluados por reporte',
    buckets=[1, 2, 5, 10, 20, 50, 100]
)

COINCIDENCIAS_ENCONTRADAS = Counter(
    'ss_coincidencias_encontradas_total',
    'Coincidencias que superaron el umbral de score'
)

MATCHING_DURACION = Histogram(
    'ss_matching_duracion_segundos',
    'Tiempo de procesamiento completo del matching en segundos',
    buckets=[0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0]
)

COINCIDENCIA_SCORE = Histogram(
    'ss_coincidencia_score',
    'Score de las coincidencias encontradas',
    buckets=[0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.01]
)
