import React, { useEffect, useRef, useState } from 'react';
import { AssetPosition, ZoneFeature } from './types';

interface OfflineMapProps {
  assetsRef: React.MutableRefObject<Map<string, AssetPosition>>;
  zones: ZoneFeature[];
  selectedAssetId: string | null;
  onSelectAsset: (assetId: string) => void;
  onViewportChange: (bbox: string) => void;
}

interface Viewport {
  centerLon: number;
  centerLat: number;
  zoom: number;
}

function pointInPolygon(lon: number, lat: number, ring: number[][]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1];
    const xj = ring[j][0], yj = ring[j][1];
    const intersect = yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi + 1e-12) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

export function isAssetInZone(asset: AssetPosition, zone: ZoneFeature): boolean {
  try {
    const parsed = JSON.parse(zone.geojson);
    const geom = parsed.geometry ?? parsed;
    const polys: number[][][][] =
      geom.type === 'Polygon' ? [geom.coordinates] : geom.type === 'MultiPolygon' ? geom.coordinates : [];
    for (const poly of polys) {
      if (poly.length > 0 && pointInPolygon(asset.lon, asset.lat, poly[0])) {
        const inHole = poly.slice(1).some((hole) => pointInPolygon(asset.lon, asset.lat, hole));
        if (!inHole) return true;
      }
    }
  } catch {
    return false;
  }
  return false;
}

export const OfflineMapCanvas: React.FC<OfflineMapProps> = ({
  assetsRef,
  zones,
  selectedAssetId,
  onSelectAsset,
  onViewportChange
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [viewport, setViewport] = useState<Viewport>({
    centerLon: 55.22,
    centerLat: 25.12,
    zoom: 1800
  });
  const dragState = useRef<{ dragging: boolean; startX: number; startY: number; origLon: number; origLat: number }>({
    dragging: false,
    startX: 0,
    startY: 0,
    origLon: 55.22,
    origLat: 25.12
  });

  useEffect(() => {
    const canvas = canvasRef.current;
    const w = canvas?.clientWidth || 900;
    const h = canvas?.clientHeight || 600;
    const halfLon = w / 2 / viewport.zoom;
    const halfLat = h / 2 / viewport.zoom;
    const minLon = (viewport.centerLon - halfLon).toFixed(5);
    const minLat = (viewport.centerLat - halfLat).toFixed(5);
    const maxLon = (viewport.centerLon + halfLon).toFixed(5);
    const maxLat = (viewport.centerLat + halfLat).toFixed(5);
    onViewportChange(`${minLon},${minLat},${maxLon},${maxLat}`);
  }, [viewport, onViewportChange]);

  useEffect(() => {
    let animId = 0;

    const render = () => {
      const canvas = canvasRef.current;
      if (canvas) {
        const dpr = window.devicePixelRatio || 1;
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
          canvas.width = width * dpr;
          canvas.height = height * dpr;
        }
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.save();
          ctx.scale(dpr, dpr);

          ctx.fillStyle = '#0f172a';
          ctx.fillRect(0, 0, width, height);

          const toScreenX = (lon: number) => width / 2 + (lon - viewport.centerLon) * viewport.zoom;
          const toScreenY = (lat: number) => height / 2 - (lat - viewport.centerLat) * viewport.zoom;

          ctx.strokeStyle = '#1e293b';
          ctx.lineWidth = 1;
          const step = viewport.zoom > 1000 ? 0.05 : 0.25;
          const startLon = Math.floor((viewport.centerLon - width / 2 / viewport.zoom) / step) * step;
          const endLon = viewport.centerLon + width / 2 / viewport.zoom;
          for (let lon = startLon; lon <= endLon; lon += step) {
            const x = toScreenX(lon);
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, height);
            ctx.stroke();
          }
          const startLat = Math.floor((viewport.centerLat - height / 2 / viewport.zoom) / step) * step;
          const endLat = viewport.centerLat + height / 2 / viewport.zoom;
          for (let lat = startLat; lat <= endLat; lat += step) {
            const y = toScreenY(lat);
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(width, y);
            ctx.stroke();
          }

          for (const zone of zones) {
            try {
              const parsed = JSON.parse(zone.geojson);
              const geom = parsed.geometry ?? parsed;
              const polys: number[][][][] =
                geom.type === 'Polygon'
                  ? [geom.coordinates]
                  : geom.type === 'MultiPolygon'
                  ? geom.coordinates
                  : [];

              const isRestricted = zone.zoneClass === 'RESTRICTED';
              ctx.fillStyle = isRestricted ? 'rgba(239, 68, 68, 0.20)' : 'rgba(245, 158, 11, 0.14)';
              ctx.strokeStyle = isRestricted ? '#ef4444' : '#f59e0b';
              ctx.lineWidth = 2;

              for (const poly of polys) {
                ctx.beginPath();
                for (const ring of poly) {
                  ring.forEach(([lon, lat], idx) => {
                    const sx = toScreenX(lon);
                    const sy = toScreenY(lat);
                    if (idx === 0) ctx.moveTo(sx, sy);
                    else ctx.lineTo(sx, sy);
                  });
                  ctx.closePath();
                }
                ctx.fill('evenodd');
                ctx.stroke();

                if (poly[0]?.[0]) {
                  const lx = toScreenX(poly[0][0][0]);
                  const ly = toScreenY(poly[0][0][1]);
                  ctx.fillStyle = isRestricted ? '#fca5a5' : '#fcd34d';
                  ctx.font = 'bold 11px sans-serif';
                  ctx.fillText(`${zone.name} [${zone.zoneClass}]`, lx + 6, ly - 6);
                }
              }
            } catch {
              // ignore invalid zone geometry
            }
          }

          for (const asset of assetsRef.current.values()) {
            const sx = toScreenX(asset.lon);
            const sy = toScreenY(asset.lat);
            if (sx < -20 || sx > width + 20 || sy < -20 || sy > height + 20) continue;

            const isSelected = asset.assetId === selectedAssetId;
            const headingRad = ((asset.headingDeg ?? 0) * Math.PI) / 180;

            ctx.save();
            ctx.translate(sx, sy);
            ctx.rotate(headingRad);

            ctx.beginPath();
            ctx.moveTo(0, -7);
            ctx.lineTo(5, 5);
            ctx.lineTo(0, 2);
            ctx.lineTo(-5, 5);
            ctx.closePath();

            ctx.fillStyle = isSelected ? '#38bdf8' : '#22c55e';
            ctx.fill();
            if (isSelected) {
              ctx.strokeStyle = '#ffffff';
              ctx.lineWidth = 1.5;
              ctx.stroke();
            }
            ctx.restore();
          }

          ctx.restore();
        }
      }
      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, [assetsRef, zones, viewport, selectedAssetId]);

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.25 : 0.8;
    setViewport((v) => ({
      ...v,
      zoom: Math.min(Math.max(v.zoom * factor, 100), 60000)
    }));
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    dragState.current = {
      dragging: true,
      startX: e.clientX,
      startY: e.clientY,
      origLon: viewport.centerLon,
      origLat: viewport.centerLat
    };
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!dragState.current.dragging) return;
    const dx = e.clientX - dragState.current.startX;
    const dy = e.clientY - dragState.current.startY;
    setViewport((v) => ({
      ...v,
      centerLon: dragState.current.origLon - dx / v.zoom,
      centerLat: dragState.current.origLat + dy / v.zoom
    }));
  };

  const handleMouseUp = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const moved =
      Math.abs(e.clientX - dragState.current.startX) + Math.abs(e.clientY - dragState.current.startY);
    dragState.current.dragging = false;
    if (moved < 5 && canvasRef.current) {
      const rect = canvasRef.current.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;
      let nearestId: string | null = null;
      let bestDist = 14;
      for (const asset of assetsRef.current.values()) {
        const sx = rect.width / 2 + (asset.lon - viewport.centerLon) * viewport.zoom;
        const sy = rect.height / 2 - (asset.lat - viewport.centerLat) * viewport.zoom;
        const dist = Math.hypot(sx - clickX, sy - clickY);
        if (dist < bestDist) {
          bestDist = dist;
          nearestId = asset.assetId;
        }
      }
      if (nearestId) onSelectAsset(nearestId);
    }
  };

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <canvas
        ref={canvasRef}
        aria-label="Live tactical asset and zone map"
        style={{ width: '100%', height: '100%', display: 'block', cursor: 'grab' }}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={() => {
          dragState.current.dragging = false;
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: 12,
          left: 12,
          background: 'rgba(15, 23, 42, 0.88)',
          color: '#e2e8f0',
          padding: '6px 10px',
          borderRadius: 6,
          fontSize: 12,
          border: '1px solid #334155'
        }}
      >
        Scroll to zoom · Drag to pan · Click asset to inspect ·{' '}
        <button
          type="button"
          onClick={() => setViewport({ centerLon: 55.22, centerLat: 25.12, zoom: 1800 })}
          style={{
            background: '#1e293b',
            color: '#38bdf8',
            border: '1px solid #475569',
            borderRadius: 4,
            padding: '2px 6px',
            cursor: 'pointer'
          }}
        >
          Reset View
        </button>
      </div>
    </div>
  );
};
