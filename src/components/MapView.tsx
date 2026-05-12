// src/components/MapView.tsx
import { useState, useEffect, useRef, useCallback } from 'react';
import 'leaflet/dist/leaflet.css';
import '../styles/mapa.css';
import { apiFetch } from '../lib/api';

/* ─── Types ─── */

interface FotoDto {
  id: string;
  url: string;
  orden: number;
}

/** Formato que devuelve GET /api/reportes del backend (MongoDB read model) */
interface ReporteAPI {
  id: string;
  tipo: 'perdido' | 'encontrado';
  animal: 'perro' | 'gato' | 'otro';
  nombre: string | null;
  raza: string | null;
  color: string | null;
  tamano: string | null;
  descripcion: string | null;
  lat: number;
  lng: number;
  fotos: FotoDto[];
  estado: string;
  createdAt: string; // ISO 8601
}

/** Formato interno normalizado que usa el componente */
interface Report {
  id: string;
  tipo: 'perdido' | 'encontrado';
  animal: 'perro' | 'gato' | 'otro';
  nombre: string;
  raza: string;
  color: string;
  tamano: string;
  fecha: string;       // relativa, eg. "Hace 2 días"
  desc_larga: string;
  fotos: string[];
  foto: string;        // primera foto o placeholder
  lat: number;
  lng: number;
}

interface Filters {
  tipo: string;
  animal: string;
  raza: string;
}

/* ─── Helpers ─── */

const PLACEHOLDER_FOTO = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200"><rect fill="%23f5efe0" width="200" height="200"/><text x="100" y="115" font-size="60" text-anchor="middle">🐾</text></svg>';

function formatFecha(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins  = Math.floor(diff / 60000);
  const hrs   = Math.floor(diff / 3600000);
  const days  = Math.floor(diff / 86400000);
  if (mins < 60)  return `Hace ${mins} min`;
  if (hrs  < 24)  return `Hace ${hrs} hora${hrs > 1 ? 's' : ''}`;
  if (days < 7)   return `Hace ${days} día${days > 1 ? 's' : ''}`;
  if (days < 30)  return `Hace ${Math.floor(days / 7)} semana${Math.floor(days / 7) > 1 ? 's' : ''}`;
  return new Date(iso).toLocaleDateString('es-CL', { day: 'numeric', month: 'short' });
}

function normalizeReporte(r: ReporteAPI): Report {
  const fotosUrls = (r.fotos ?? []).map(f => f.url);
  return {
    id:        r.id,
    tipo:      r.tipo,
    animal:    r.animal,
    nombre:    r.nombre || 'Sin nombre',
    raza:      r.raza   ?? 'Raza desconocida',
    color:     r.color  ?? 'Color desconocido',
    tamano:    r.tamano ?? '',
    fecha:     formatFecha(r.createdAt),
    desc_larga: r.descripcion ?? 'Sin descripción.',
    fotos:     fotosUrls.length ? fotosUrls : [PLACEHOLDER_FOTO],
    foto:      fotosUrls[0] ?? PLACEHOLDER_FOTO,
    lat:       r.lat,
    lng:       r.lng,
  };
}

/* ─── Leaflet helpers ─── */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeIcon(L: any, r: Report, selected: boolean) {
  const color = r.tipo === 'perdido' ? '#C45C3A' : '#6B8F71';
  const sz = selected ? 56 : 46;
  const bw = selected ? 3.5 : 2.5;
  const tip = Math.round(sz / 5);
  const tipH = Math.round(sz / 7);
  return L.divIcon({
    className: '',
    html: `<div style="filter:drop-shadow(0 ${selected ? 6 : 3}px ${selected ? 18 : 8}px rgba(0,0,0,.55))">
      <div style="width:${sz}px;height:${sz}px;border-radius:50%;border:${bw}px solid ${color};overflow:hidden;background:#1a0f09;">
        <img src="${r.foto}" style="width:100%;height:100%;object-fit:cover;" loading="lazy"/>
      </div>
      <div style="width:0;height:0;border-left:${tipH}px solid transparent;border-right:${tipH}px solid transparent;border-top:${tip}px solid ${color};margin:0 auto;margin-top:-2px;"></div>
    </div>`,
    iconSize: [sz, sz + tip + 2],
    iconAnchor: [sz / 2, sz + tip + 2],
    popupAnchor: [0, -(sz + tip + 10)],
  });
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makePopup(r: Report) {
  const c = r.tipo === 'perdido' ? '#C45C3A' : '#6B8F71';
  const bg = r.tipo === 'perdido' ? '#F0C5B4' : '#C8DEC9';
  const fg = r.tipo === 'perdido' ? '#9E4026' : '#2D6B33';
  return `<div style="font-family:'Nunito',sans-serif;background:#1c1008;padding:12px 14px 10px;min-width:210px;">
    <div style="display:flex;gap:10px;align-items:center;margin-bottom:9px;">
      <img src="${r.foto}" style="width:42px;height:42px;border-radius:9px;object-fit:cover;flex-shrink:0;border:2px solid rgba(255,255,255,.12);">
      <div>
        <span style="background:${bg};color:${fg};font-size:.6rem;font-weight:800;padding:2px 8px;border-radius:999px;text-transform:uppercase;">${r.tipo === 'perdido' ? '🔴 Perdido' : '🟢 Encontrado'}</span>
        <div style="font-family:'Fraunces',serif;font-weight:700;font-size:.95rem;color:white;margin-top:3px;">${r.nombre}</div>
        <div style="font-size:.7rem;color:rgba(255,255,255,.5);">${r.raza} · ${r.zona}</div>
      </div>
    </div>
    <button data-open-detail="${r.id}" style="width:100%;background:${c};color:white;border:none;padding:8px;border-radius:9px;font-family:'Nunito',sans-serif;font-weight:800;font-size:.8rem;cursor:pointer;letter-spacing:.01em;" type="button">Ver detalles →</button>
  </div>`;
}

function applyFiltersFn(reports: Report[], filters: Filters): Report[] {
  return reports.filter(r => {
    if (filters.tipo !== 'todos' && r.tipo !== filters.tipo) return false;
    if (filters.animal !== 'todos' && r.animal !== filters.animal) return false;
    if (filters.raza && !r.raza.toLowerCase().includes(filters.raza.toLowerCase())) return false;
    return true;
  });
}

/* ─── Component ─── */
export default function MapView() {
  const mapRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const leafletRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const mapInstanceRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const markersRef = useRef<Record<string, any>>({});

  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [filters, setFilters] = useState<Filters>({ tipo: 'todos', animal: 'todos', raza: '' });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailReport, setDetailReport] = useState<Report | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [panelOpen, setPanelOpen] = useState(true);

  /* ── Fetch reports from backend ── */
  useEffect(() => {
    let cancelled = false;
    apiFetch<ReporteAPI[]>('/api/reportes')
      .then(data => {
        if (cancelled) return;
        setReports(data.map(normalizeReporte));
        setLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setFetchError('No se pudieron cargar los reportes. Verifica tu conexión.');
        setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const visibleReports = applyFiltersFn(reports, filters);
  const nLost = visibleReports.filter(r => r.tipo === 'perdido').length;
  const nFound = visibleReports.filter(r => r.tipo === 'encontrado').length;

  /* ── openDetail (stable ref so popup button can call it) ── */
  const openDetailRef = useRef<(id: string) => void>(() => {});
  const openDetail = useCallback((id: string) => {
    const r = reports.find(x => x.id === id);
    if (!r) return;
    setSelectedId(id);
    setDetailReport(r);
    setDetailOpen(true);
    // update icons
    const L = leafletRef.current;
    const map = mapInstanceRef.current;
    if (L && map) {
      reports.forEach(x => {
        const m = markersRef.current[x.id];
        if (m) m.setIcon(makeIcon(L, x, x.id === id));
      });
      map.closePopup();
    }
  }, [reports]);
  useEffect(() => { openDetailRef.current = openDetail; }, [openDetail]);

  /* ── Init Leaflet (once) ── */
  useEffect(() => {
    if (!mapRef.current) return;
    let destroyed = false;

    (async () => {
      const L = (await import('leaflet')).default;
      if (destroyed || !mapRef.current) return;
      leafletRef.current = L;

      // Región Metropolitana + inmediaciones
      const RM_BOUNDS = L.latLngBounds(
        [-34.6, -71.8],
        [-32.9, -70.0],
      );

      const map = L.map(mapRef.current, {
        zoomControl: false,
        maxBounds: RM_BOUNDS,
        maxBoundsViscosity: 1.0,
        minZoom: 10,
        maxZoom: 18,
      }).setView([-33.4489, -70.6693], 12);
      mapInstanceRef.current = map;

      L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        minZoom: 10,
        maxZoom: 18,
        bounds: RM_BOUNDS,
      }).addTo(map);
      L.control.zoom({ position: 'bottomright' }).addTo(map);

      requestAnimationFrame(() => { if (!destroyed) map.invalidateSize(); });

      // Wire "Ver detalles" button in popups via event delegation
      map.on('popupopen', (e: { popup: { getElement: () => HTMLElement } }) => {
        const el = e.popup.getElement();
        if (!el) return;
        const btn = el.querySelector<HTMLButtonElement>('[data-open-detail]');
        if (btn) {
          btn.onclick = () => {
            openDetailRef.current(btn.dataset.openDetail!);
          };
        }
      });
    })();

    return () => {
      destroyed = true;
      mapInstanceRef.current?.remove();
      mapInstanceRef.current = null;
      markersRef.current = {};
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ── Sync markers when reports load or change ── */
  useEffect(() => {
    const map = mapInstanceRef.current;
    const L = leafletRef.current;
    if (!map || !L) return;

    // Add markers for any new reports not yet on the map
    reports.forEach(r => {
      if (markersRef.current[r.id]) return; // already added
      const marker = L.marker([r.lat, r.lng], { icon: makeIcon(L, r, false) })
        .addTo(map)
        .bindPopup(makePopup(r), { maxWidth: 250, closeButton: false });

      marker.on('click', () => {
        setSelectedId(prev => {
          if (prev && markersRef.current[prev]) {
            const prevR = reports.find(x => x.id === prev);
            if (prevR) markersRef.current[prev].setIcon(makeIcon(L, prevR, false));
          }
          markersRef.current[r.id]?.setIcon(makeIcon(L, r, true));
          return r.id;
        });
      });

      markersRef.current[r.id] = marker;
    });
  }, [reports]);

  /* ── Sync visible markers when filters change ── */
  useEffect(() => {
    const map = mapInstanceRef.current;
    const L = leafletRef.current;
    if (!map || !L) return;
    const visIds = new Set(visibleReports.map(r => r.id));
    reports.forEach(r => {
      const m = markersRef.current[r.id];
      if (!m) return;
      if (visIds.has(r.id)) { if (!map.hasLayer(m)) m.addTo(map); }
      else { if (map.hasLayer(m)) m.remove(); }
    });
  }, [visibleReports, reports]);

  /* ── flyTo ── */
  const flyTo = (id: string) => {
    const r = reports.find(x => x.id === id);
    const L = leafletRef.current;
    const map = mapInstanceRef.current;
    if (!r || !L || !map) return;

    setSelectedId(prev => {
      if (prev && markersRef.current[prev]) {
        const prevR = reports.find(x => x.id === prev);
        if (prevR) markersRef.current[prev].setIcon(makeIcon(L, prevR, false));
      }
      markersRef.current[r.id]?.setIcon(makeIcon(L, r, true));
      return r.id;
    });

    map.flyTo([r.lat, r.lng], 15, { duration: 0.7 });
    setTimeout(() => markersRef.current[r.id]?.openPopup(), 750);
  };

  /* ── Panel toggle ── */
  const togglePanel = () => {
    setPanelOpen(p => {
      setTimeout(() => mapInstanceRef.current?.invalidateSize(), 320);
      return !p;
    });
  };

  /* ── Filter pill helper ── */
  const pillClass = (field: keyof Filters, value: string) => {
    const cur = filters[field];
    if (cur === value) {
      if (value === 'todos') return 'mv-pill act-all';
      if (value === 'perdido') return 'mv-pill act-lost';
      return 'mv-pill act-fnd';
    }
    return 'mv-pill';
  };

  const setFilter = (field: keyof Filters, value: string) => {
    setFilters(prev => ({ ...prev, [field]: value }));
  };

  /* ─── Render ─── */
  return (
    <div className="mv-root">

      {/* ── 1. Panel (flex item izquierdo) ── */}
      <aside className={`mv-panel${panelOpen ? '' : ' collapsed'}`}>
        <div className="mv-panel-header">
          <div className="mv-panel-title">
            <h2>Reportes activos</h2>
            {!loading && (
              <span className="mv-count-badge">{visibleReports.length} reportes</span>
            )}
          </div>
          <p className="mv-panel-sub">
            <span className="mv-pulse-dot" />
            Santiago de Chile · {loading ? 'Cargando…' : 'Actualizado ahora'}
          </p>
        </div>

        <div className="mv-filters">
          <div className="mv-filter-block">
            <span className="mv-filter-label">Tipo de reporte</span>
            <div className="mv-pill-row">
              <button className={pillClass('tipo', 'todos')}       onClick={() => setFilter('tipo', 'todos')}>Todos</button>
              <button className={pillClass('tipo', 'perdido')}     onClick={() => setFilter('tipo', 'perdido')}>🔴 Perdidos</button>
              <button className={pillClass('tipo', 'encontrado')}  onClick={() => setFilter('tipo', 'encontrado')}>🟢 Encontrados</button>
            </div>
          </div>
          <div className="mv-filter-block">
            <span className="mv-filter-label">Animal</span>
            <div className="mv-pill-row">
              <button className={pillClass('animal', 'todos')} onClick={() => setFilter('animal', 'todos')}>Todos</button>
              <button className={pillClass('animal', 'perro')} onClick={() => setFilter('animal', 'perro')}>🐕 Perro</button>
              <button className={pillClass('animal', 'gato')}  onClick={() => setFilter('animal', 'gato')}>🐱 Gato</button>
              <button className={pillClass('animal', 'otro')}  onClick={() => setFilter('animal', 'otro')}>🐾 Otro</button>
            </div>
          </div>
          <div className="mv-filter-block">
            <span className="mv-filter-label">Buscar por raza</span>
            <input
              type="text"
              className="mv-raza-input"
              placeholder="Ej: Golden Retriever, Siamés…"
              value={filters.raza}
              onChange={e => setFilter('raza', e.target.value)}
            />
          </div>
        </div>

        <div className="mv-cards-list">
          {loading ? (
            <div className="mv-empty">
              <div className="mv-empty-icon" style={{ animation: 'spin 1.5s linear infinite' }}>🐾</div>
              <p>Cargando reportes…</p>
            </div>
          ) : fetchError ? (
            <div className="mv-empty">
              <div className="mv-empty-icon">⚠️</div>
              <p>{fetchError}</p>
            </div>
          ) : visibleReports.length === 0 ? (
            <div className="mv-empty">
              <div className="mv-empty-icon">🔍</div>
              <p>{reports.length === 0 ? 'Aún no hay reportes.\n¡Sé el primero en publicar!' : 'Sin resultados para\nlos filtros aplicados.'}</p>
            </div>
          ) : (
            visibleReports.map(r => (
              <div
                key={r.id}
                id={`mv-card-${r.id}`}
                className={`mv-card${selectedId === r.id ? ' selected' : ''}`}
              >
                <div className="mv-card-top" onClick={() => flyTo(r.id)} style={{ cursor: 'pointer' }}>
                  <img className="mv-card-photo" src={r.foto} alt={r.nombre} loading="lazy" />
                  <div className="mv-card-info">
                    <div className="mv-card-name">
                      <span>{r.nombre}</span>
                      <div className={`mv-sdot ${r.tipo}`} />
                    </div>
                    <div className="mv-card-raza">{r.raza} · {r.color}</div>
                    <div className="mv-card-zona">🕒 {r.fecha}</div>
                  </div>
                </div>
                <div className="mv-card-footer">
                  <span className={`mv-sbadge ${r.tipo}`}>{r.tipo === 'perdido' ? '🔴 Perdido' : '🟢 Encontrado'}</span>
                  <button className="mv-detail-btn" onClick={() => openDetail(r.id)}>Ver detalles →</button>
                </div>
              </div>
            ))
          )}
        </div>
      </aside>

      {/* ── 2. Handle (flex item central, entre panel y mapa) ── */}
      <button
        className="mv-handle"
        onClick={togglePanel}
        aria-label="Toggle panel"
      >
        {panelOpen ? '‹' : '›'}
      </button>

      {/* ── 3. Mapa (flex item derecho, flex:1) ── */}
      <div className="mv-map-wrap">
        <div ref={mapRef} className="mv-map" />

        <div className="mv-chips">
          <div className="mv-chip mv-chip-total">🗺️ {visibleReports.length} visibles</div>
          <div className="mv-chip mv-chip-lost">🔴 {nLost} perdidos</div>
          <div className="mv-chip mv-chip-found">🟢 {nFound} encontrados</div>
        </div>

        {detailOpen && (
          <div className="mv-backdrop" onClick={() => setDetailOpen(false)} />
        )}

        <div className={`mv-detail${detailOpen ? ' open' : ''}`}>
          <div className="mv-detail-handle"><div className="mv-detail-bar" /></div>
          <button className="mv-detail-close" onClick={() => setDetailOpen(false)}>✕</button>

          {detailReport && (
            <>
              <div className="mv-gallery">
                {detailReport.fotos.map((f, i) => (
                  <img key={i} src={f} alt={detailReport.nombre} loading="lazy" />
                ))}
              </div>
              <div className="mv-detail-body">
                <div>
                  <div className="mv-detail-name">{detailReport.nombre}</div>
                  <div className="mv-detail-status-row">
                    <span className={`mv-sbadge ${detailReport.tipo}`}>
                      {detailReport.tipo === 'perdido' ? '🔴 Perdido' : '🟢 Encontrado'}
                    </span>
                    <span className="mv-detail-meta">📅 {detailReport.fecha}</span>
                  </div>
                </div>
                <div className="mv-detail-divider" />
                <div>
                  <div className="mv-detail-section-title">Información física</div>
                  <div className="mv-detail-tags">
                    <span className="mv-detail-tag">🐾 {detailReport.raza}</span>
                    <span className="mv-detail-tag">🎨 {detailReport.color}</span>
                    {detailReport.tamano && (
                      <span className="mv-detail-tag">📏 {detailReport.tamano}</span>
                    )}
                  </div>
                </div>
                <div className="mv-detail-divider" />
                <div>
                  <div className="mv-detail-section-title">Descripción</div>
                  <p className="mv-detail-desc">{detailReport.desc_larga}</p>
                </div>
                <a href="#" className="mv-detail-contact">📞 Contactar al reportante</a>
              </div>
            </>
          )}
        </div>
      </div>

    </div>
  );
}
