import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiClientError } from './api';
import { isAssetInZone, OfflineMapCanvas } from './OfflineMapCanvas';
import {
  AlertAuditEntry,
  AlertItem,
  AssetPosition,
  AuthSession,
  SystemStats,
  TrackPoint,
  ZoneFeature
} from './types';

export const App: React.FC = () => {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [usernameInput, setUsernameInput] = useState('operator-1');
  const [passwordInput, setPasswordInput] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);

  const [connected, setConnected] = useState<boolean>(true);
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [zones, setZones] = useState<ZoneFeature[]>([]);
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [alertFilter, setAlertFilter] = useState<string>('');
  const [actionBanner, setActionBanner] = useState<{ type: 'error' | 'info'; text: string } | null>(null);
  const [srAnnouncement, setSrAnnouncement] = useState<string>('');

  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [assetTrack, setAssetTrack] = useState<TrackPoint[]>([]);
  const [sourceTypeFilter, setSourceTypeFilter] = useState<string>('');

  const [resolveTargetId, setResolveTargetId] = useState<string | null>(null);
  const [resolveNote, setResolveNote] = useState<string>('');
  const [auditModalAlertId, setAuditModalAlertId] = useState<string | null>(null);
  const [auditEntries, setAuditEntries] = useState<AlertAuditEntry[]>([]);
  const [showZoneTable, setShowZoneTable] = useState<boolean>(false);

  const assetsRef = useRef<Map<string, AssetPosition>>(new Map());
  const [assetSnapshotCount, setAssetSnapshotCount] = useState<number>(0);
  const bboxRef = useRef<string>('54.5,24.5,56.0,26.0');
  const lastAlertCountRef = useRef<number>(0);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError(null);
    try {
      const auth = await api.login(usernameInput.trim(), passwordInput);
      setSession(auth);
    } catch (err) {
      setLoginError(err instanceof Error ? err.message : 'Sign-in failed');
    }
  };

  useEffect(() => {
    if (!session) return;
    api.getZones(session.token).then(setZones).catch(() => {});
  }, [session]);

  useEffect(() => {
    if (!session) return;
    let active = true;

    const syncLiveState = async () => {
      try {
        const [latestAssets, latestAlerts, latestStats] = await Promise.all([
          api.getAssets(session.token, bboxRef.current),
          api.getAlerts(session.token, alertFilter || undefined),
          api.getStats(session.token)
        ]);
        if (!active) return;

        const nextMap = new Map<string, AssetPosition>();
        for (const a of latestAssets) {
          nextMap.set(a.assetId, a);
        }
        assetsRef.current = nextMap;
        setAssetSnapshotCount(nextMap.size);
        setAlerts(latestAlerts);
        setStats(latestStats);
        setConnected(true);

        if (latestStats.openAlerts > lastAlertCountRef.current) {
          const diff = latestStats.openAlerts - lastAlertCountRef.current;
          setSrAnnouncement(`${diff} new restricted zone alert${diff > 1 ? 's' : ''} active.`);
        }
        lastAlertCountRef.current = latestStats.openAlerts;
      } catch {
        if (active) setConnected(false);
      }
    };

    syncLiveState();
    const timer = window.setInterval(syncLiveState, 1000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [session, alertFilter]);

  useEffect(() => {
    if (!session || !selectedAssetId) {
      setAssetTrack([]);
      return;
    }
    api
      .getAssetTrack(session.token, selectedAssetId, sourceTypeFilter || undefined)
      .then(setAssetTrack)
      .catch(() => setAssetTrack([]));
  }, [session, selectedAssetId, sourceTypeFilter, assetSnapshotCount]);

  const handleViewportChange = useCallback((bbox: string) => {
    bboxRef.current = bbox;
  }, []);

  const handleClaim = async (alertId: string) => {
    if (!session) return;
    setActionBanner(null);
    try {
      const updated = await api.claimAlert(session.token, alertId);
      setAlerts((prev) => prev.map((a) => (a.alertId === alertId ? updated : a)));
      setActionBanner({ type: 'info', text: `Claimed alert ${alertId.slice(0, 8)}.` });
    } catch (err) {
      if (err instanceof ApiClientError && err.code === 'ALERT_ALREADY_CLAIMED') {
        setActionBanner({
          type: 'error',
          text: `Claim race lost: ${err.message}. Another operator claimed this alert first.`
        });
      } else {
        setActionBanner({
          type: 'error',
          text: err instanceof Error ? err.message : 'Could not claim alert'
        });
      }
    }
  };

  const handleRelease = async (alertId: string) => {
    if (!session) return;
    setActionBanner(null);
    try {
      const updated = await api.releaseAlert(session.token, alertId);
      setAlerts((prev) => prev.map((a) => (a.alertId === alertId ? updated : a)));
      setActionBanner({ type: 'info', text: `Released alert ${alertId.slice(0, 8)}.` });
    } catch (err) {
      setActionBanner({
        type: 'error',
        text: err instanceof Error ? err.message : 'Could not release alert'
      });
    }
  };

  const handleResolveSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!session || !resolveTargetId) return;
    setActionBanner(null);
    try {
      const updated = await api.resolveAlert(session.token, resolveTargetId, resolveNote);
      setAlerts((prev) => prev.map((a) => (a.alertId === resolveTargetId ? updated : a)));
      setResolveTargetId(null);
      setResolveNote('');
      setActionBanner({ type: 'info', text: `Resolved alert ${updated.alertId.slice(0, 8)}.` });
    } catch (err) {
      setActionBanner({
        type: 'error',
        text: err instanceof Error ? err.message : 'Could not resolve alert'
      });
    }
  };

  const openAudit = async (alertId: string) => {
    if (!session) return;
    setAuditModalAlertId(alertId);
    const entries = await api.getAlertAudit(session.token, alertId);
    setAuditEntries(entries);
  };

  if (!session) {
    return (
      <main
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0f172a',
          color: '#f8fafc',
          fontFamily: 'system-ui, sans-serif'
        }}
      >
        <form
          onSubmit={handleLogin}
          style={{
            width: 360,
            background: '#1e293b',
            padding: 24,
            borderRadius: 8,
            border: '1px solid #334155'
          }}
        >
          <h1 style={{ margin: '0 0 8px', fontSize: 20 }}>Zone Watch Console</h1>
          <p style={{ margin: '0 0 16px', fontSize: 13, color: '#94a3b8' }}>
            Air-Gapped Operator Sign-In
          </p>
          <label style={{ display: 'block', marginBottom: 12, fontSize: 13 }}>
            Username
            <input
              type="text"
              required
              value={usernameInput}
              onChange={(e) => setUsernameInput(e.target.value)}
              style={{
                width: '100%',
                marginTop: 4,
                padding: 8,
                borderRadius: 4,
                border: '1px solid #475569',
                background: '#0f172a',
                color: '#f8fafc'
              }}
            />
          </label>
          <label style={{ display: 'block', marginBottom: 16, fontSize: 13 }}>
            Password
            <input
              type="password"
              required
              value={passwordInput}
              onChange={(e) => setPasswordInput(e.target.value)}
              style={{
                width: '100%',
                marginTop: 4,
                padding: 8,
                borderRadius: 4,
                border: '1px solid #475569',
                background: '#0f172a',
                color: '#f8fafc'
              }}
            />
          </label>
          {loginError && (
            <div role="alert" style={{ color: '#fca5a5', fontSize: 13, marginBottom: 12 }}>
              {loginError}
            </div>
          )}
          <button
            type="submit"
            style={{
              width: '100%',
              padding: 10,
              background: '#0284c7',
              color: '#ffffff',
              border: 'none',
              borderRadius: 4,
              fontWeight: 600,
              cursor: 'pointer'
            }}
          >
            Sign In
          </button>
        </form>
      </main>
    );
  }

  const selectedAsset = selectedAssetId ? assetsRef.current.get(selectedAssetId) ?? null : null;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        background: '#0f172a',
        color: '#f8fafc',
        fontFamily: 'system-ui, sans-serif'
      }}
    >
      <div aria-live="polite" style={{ position: 'absolute', left: -9999 }}>
        {srAnnouncement}
      </div>

      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 16px',
          background: '#1e293b',
          borderBottom: '1px solid #334155',
          fontSize: 13
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <strong>ZONE WATCH</strong>
          <span
            role="status"
            style={{
              padding: '2px 8px',
              borderRadius: 999,
              fontWeight: 600,
              background: connected ? '#14532d' : '#7f1d1d',
              color: connected ? '#86efac' : '#fca5a5'
            }}
          >
            {connected ? 'LIVE CONNECTED' : 'CONNECTION INTERRUPTED — RETRYING'}
          </span>
          {stats && (
            <span style={{ color: '#cbd5e1' }}>
              Assets: <strong>{stats.assets}</strong> | Accepted: <strong>{stats.accepted}</strong> |
              Open Alerts: <strong>{stats.openAlerts}</strong>
            </span>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            type="button"
            onClick={() => setShowZoneTable((v) => !v)}
            style={{
              background: '#334155',
              color: '#f8fafc',
              border: '1px solid #475569',
              borderRadius: 4,
              padding: '4px 10px',
              cursor: 'pointer'
            }}
          >
            {showZoneTable ? 'Hide Zone Occupancy Table' : 'Accessible Zone Occupancy View'}
          </button>
          <span>
            Signed in: <strong>{session.username}</strong> ({session.role})
          </span>
          <button
            type="button"
            onClick={() => setSession(null)}
            style={{
              background: 'transparent',
              color: '#94a3b8',
              border: '1px solid #475569',
              borderRadius: 4,
              padding: '4px 8px',
              cursor: 'pointer'
            }}
          >
            Sign Out
          </button>
        </div>
      </header>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <section style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative' }}>
          <div style={{ flex: 1 }}>
            <OfflineMapCanvas
              assetsRef={assetsRef}
              zones={zones}
              selectedAssetId={selectedAssetId}
              onSelectAsset={setSelectedAssetId}
              onViewportChange={handleViewportChange}
            />
          </div>

          {showZoneTable && (
            <div
              role="region"
              aria-label="Assets inside zones table"
              style={{
                maxHeight: 220,
                overflowY: 'auto',
                background: '#1e293b',
                borderTop: '1px solid #334155',
                padding: 12
              }}
            >
              <h2 style={{ margin: '0 0 8px', fontSize: 14 }}>Zone Occupancy (Non-Map View)</h2>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ textAlign: 'left', borderBottom: '1px solid #475569' }}>
                    <th style={{ padding: 4 }}>Zone</th>
                    <th style={{ padding: 4 }}>Class</th>
                    <th style={{ padding: 4 }}>Assets Currently Inside</th>
                  </tr>
                </thead>
                <tbody>
                  {zones.map((z) => {
                    const inside = Array.from(assetsRef.current.values()).filter((a) =>
                      isAssetInZone(a, z)
                    );
                    return (
                      <tr key={z.zoneId} style={{ borderBottom: '1px solid #334155' }}>
                        <td style={{ padding: 4 }}>{z.name}</td>
                        <td style={{ padding: 4 }}>{z.zoneClass}</td>
                        <td style={{ padding: 4 }}>
                          {inside.length === 0
                            ? 'None'
                            : inside.map((a) => (
                                <button
                                  key={a.assetId}
                                  type="button"
                                  onClick={() => setSelectedAssetId(a.assetId)}
                                  style={{
                                    marginRight: 6,
                                    background: '#0f172a',
                                    color: '#38bdf8',
                                    border: '1px solid #475569',
                                    borderRadius: 4,
                                    padding: '2px 6px',
                                    cursor: 'pointer'
                                  }}
                                >
                                  {a.assetId}
                                </button>
                              ))}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {selectedAssetId && (
            <div
              role="region"
              aria-label="Selected asset details"
              style={{
                height: 210,
                background: '#1e293b',
                borderTop: '1px solid #334155',
                padding: 12,
                overflowY: 'auto',
                fontSize: 12
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <strong>
                  Asset: {selectedAssetId}{' '}
                  {selectedAsset &&
                    `(${selectedAsset.lat.toFixed(5)}, ${selectedAsset.lon.toFixed(5)} · Heading ${
                      selectedAsset.headingDeg ?? 'N/A'
                    }°)`}
                </strong>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <label>
                    Source Type:{' '}
                    <select
                      value={sourceTypeFilter}
                      onChange={(e) => setSourceTypeFilter(e.target.value)}
                      style={{ background: '#0f172a', color: '#f8fafc', border: '1px solid #475569' }}
                    >
                      <option value="">ALL</option>
                      <option value="SATELLITE">SATELLITE</option>
                      <option value="GROUND">GROUND</option>
                      <option value="MOBILE">MOBILE</option>
                      <option value="UNKNOWN">UNKNOWN</option>
                    </select>
                  </label>
                  <button
                    type="button"
                    onClick={() => setSelectedAssetId(null)}
                    style={{
                      background: '#334155',
                      color: '#f8fafc',
                      border: 'none',
                      borderRadius: 4,
                      padding: '2px 8px',
                      cursor: 'pointer'
                    }}
                  >
                    Close
                  </button>
                </div>
              </div>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ textAlign: 'left', borderBottom: '1px solid #475569' }}>
                    <th style={{ padding: 4 }}>Observed At (UTC)</th>
                    <th style={{ padding: 4 }}>Lat</th>
                    <th style={{ padding: 4 }}>Lon</th>
                    <th style={{ padding: 4 }}>Source</th>
                    <th style={{ padding: 4 }}>Source Type</th>
                  </tr>
                </thead>
                <tbody>
                  {assetTrack.slice(-15).map((pt) => (
                    <tr key={pt.reportId} style={{ borderBottom: '1px solid #334155' }}>
                      <td style={{ padding: 4 }}>{pt.observedAt}</td>
                      <td style={{ padding: 4 }}>{pt.lat.toFixed(5)}</td>
                      <td style={{ padding: 4 }}>{pt.lon.toFixed(5)}</td>
                      <td style={{ padding: 4 }}>{pt.source ?? '-'}</td>
                      <td style={{ padding: 4 }}>
                        <strong>{pt.sourceType}</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <aside
          aria-label="Restricted zone alerts panel"
          style={{
            width: 420,
            background: '#1e293b',
            borderLeft: '1px solid #334155',
            display: 'flex',
            flexDirection: 'column',
            padding: 12
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <h2 style={{ margin: 0, fontSize: 16 }}>Alerts</h2>
            <label style={{ fontSize: 12 }}>
              Status:{' '}
              <select
                value={alertFilter}
                onChange={(e) => setAlertFilter(e.target.value)}
                style={{
                  background: '#0f172a',
                  color: '#f8fafc',
                  border: '1px solid #475569',
                  borderRadius: 4,
                  padding: '4px 6px'
                }}
              >
                <option value="">All</option>
                <option value="OPEN">OPEN</option>
                <option value="CLAIMED">CLAIMED</option>
                <option value="RESOLVED">RESOLVED</option>
              </select>
            </label>
          </div>

          {actionBanner && (
            <div
              role="alert"
              style={{
                padding: 8,
                marginBottom: 8,
                borderRadius: 4,
                fontSize: 12,
                background: actionBanner.type === 'error' ? '#7f1d1d' : '#1e3a8a',
                color: '#f8fafc'
              }}
            >
              {actionBanner.text}
            </div>
          )}

          {resolveTargetId && (
            <form
              onSubmit={handleResolveSubmit}
              style={{
                background: '#0f172a',
                padding: 10,
                borderRadius: 6,
                border: '1px solid #38bdf8',
                marginBottom: 10
              }}
            >
              <label style={{ display: 'block', fontSize: 12, marginBottom: 6 }}>
                Resolution Note (1–500 chars) for Alert {resolveTargetId.slice(0, 8)}:
                <textarea
                  required
                  minLength={1}
                  maxLength={500}
                  rows={3}
                  value={resolveNote}
                  onChange={(e) => setResolveNote(e.target.value)}
                  style={{
                    width: '100%',
                    marginTop: 4,
                    background: '#1e293b',
                    color: '#f8fafc',
                    border: '1px solid #475569',
                    borderRadius: 4,
                    padding: 6
                  }}
                />
              </label>
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="submit"
                  style={{
                    background: '#16a34a',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: 4,
                    padding: '4px 10px',
                    cursor: 'pointer'
                  }}
                >
                  Confirm Resolve
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setResolveTargetId(null);
                    setResolveNote('');
                  }}
                  style={{
                    background: '#334155',
                    color: '#f8fafc',
                    border: 'none',
                    borderRadius: 4,
                    padding: '4px 10px',
                    cursor: 'pointer'
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {alerts.map((al) => {
              const isOwner = al.owner === session.username;
              const canManage = isOwner || session.role === 'SUPERVISOR';
              return (
                <article
                  key={al.alertId}
                  style={{
                    background: '#0f172a',
                    border: '1px solid #334155',
                    borderRadius: 6,
                    padding: 10,
                    fontSize: 12
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <strong>{al.zoneName}</strong>
                    <span
                      style={{
                        padding: '1px 6px',
                        borderRadius: 4,
                        fontWeight: 700,
                        background:
                          al.status === 'OPEN'
                            ? '#991b1b'
                            : al.status === 'CLAIMED'
                            ? '#92400e'
                            : '#166534'
                      }}
                    >
                      {al.status}
                    </span>
                  </div>
                  <div>
                    Asset:{' '}
                    <button
                      type="button"
                      onClick={() => setSelectedAssetId(al.assetId)}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#38bdf8',
                        textDecoration: 'underline',
                        padding: 0,
                        cursor: 'pointer'
                      }}
                    >
                      {al.assetId}
                    </button>
                  </div>
                  <div style={{ color: '#94a3b8' }}>Triggered: {al.triggeredAt}</div>
                  {al.owner && <div>Owner: {al.owner}</div>}
                  {al.resolutionNote && <div>Note: {al.resolutionNote}</div>}

                  <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                    {al.status === 'OPEN' && (
                      <button
                        type="button"
                        onClick={() => handleClaim(al.alertId)}
                        style={{
                          background: '#0284c7',
                          color: '#ffffff',
                          border: 'none',
                          borderRadius: 4,
                          padding: '4px 10px',
                          cursor: 'pointer'
                        }}
                      >
                        Claim
                      </button>
                    )}
                    {al.status === 'CLAIMED' && canManage && (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            setResolveTargetId(al.alertId);
                            setResolveNote('');
                          }}
                          style={{
                            background: '#16a34a',
                            color: '#ffffff',
                            border: 'none',
                            borderRadius: 4,
                            padding: '4px 10px',
                            cursor: 'pointer'
                          }}
                        >
                          Resolve
                        </button>
                        <button
                          type="button"
                          onClick={() => handleRelease(al.alertId)}
                          style={{
                            background: '#475569',
                            color: '#ffffff',
                            border: 'none',
                            borderRadius: 4,
                            padding: '4px 10px',
                            cursor: 'pointer'
                          }}
                        >
                          Release
                        </button>
                      </>
                    )}
                    <button
                      type="button"
                      onClick={() => openAudit(al.alertId)}
                      style={{
                        background: '#1e293b',
                        color: '#cbd5e1',
                        border: '1px solid #475569',
                        borderRadius: 4,
                        padding: '4px 8px',
                        cursor: 'pointer'
                      }}
                    >
                      Audit Log
                    </button>
                  </div>
                </article>
              );
            })}
          </div>

          {auditModalAlertId && (
            <div
              role="region"
              aria-label="Alert audit history"
              style={{
                marginTop: 10,
                background: '#0f172a',
                border: '1px solid #475569',
                borderRadius: 6,
                padding: 10,
                maxHeight: 180,
                overflowY: 'auto',
                fontSize: 12
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                <strong>Audit Trail ({auditModalAlertId.slice(0, 8)})</strong>
                <button
                  type="button"
                  onClick={() => setAuditModalAlertId(null)}
                  style={{
                    background: '#334155',
                    color: '#f8fafc',
                    border: 'none',
                    borderRadius: 4,
                    padding: '2px 6px',
                    cursor: 'pointer'
                  }}
                >
                  Close
                </button>
              </div>
              {auditEntries.map((entry) => (
                <div key={entry.auditId} style={{ borderBottom: '1px solid #1e293b', padding: '4px 0' }}>
                  <strong>{entry.action}</strong> by {entry.actor ?? 'SYSTEM'} at {entry.occurredAt}
                  {entry.note ? ` — "${entry.note}"` : ''}
                </div>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
};
