import {
  AlertAuditEntry,
  AlertItem,
  AssetPosition,
  AuthSession,
  ProblemDetail,
  SystemStats,
  TrackPoint,
  ZoneFeature
} from './types';

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string
  ) {
    super(message);
  }
}

async function request<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    ...(init?.headers as Record<string, string> | undefined)
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(path, { ...init, headers });
  if (!res.ok) {
    let problem: ProblemDetail = {};
    try {
      problem = (await res.json()) as ProblemDetail;
    } catch {
      // fallback if non-JSON error
    }
    throw new ApiClientError(
      res.status,
      problem.code ?? 'HTTP_ERROR',
      problem.detail ?? `Request failed (${res.status})`
    );
  }
  return (await res.json()) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<AuthSession>('/api/v1/auth/login', undefined, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    }),

  getStats: (token: string) => request<SystemStats>('/api/v1/stats', token),

  getZones: (token: string) => request<ZoneFeature[]>('/api/v1/zones', token),

  getAssets: (token: string, bbox: string) =>
    request<AssetPosition[]>(`/api/v1/assets?bbox=${encodeURIComponent(bbox)}&limit=3500`, token),

  getAssetTrack: (token: string, assetId: string, sourceType?: string) => {
    const query = sourceType ? `?sourceType=${encodeURIComponent(sourceType)}` : '';
    return request<TrackPoint[]>(`/api/v1/assets/${encodeURIComponent(assetId)}/track${query}`, token);
  },

  getAlerts: (token: string, status?: string) => {
    const query = status ? `?status=${encodeURIComponent(status)}&limit=100` : '?limit=100';
    return request<AlertItem[]>(`/api/v1/alerts${query}`, token);
  },

  getAlertAudit: (token: string, alertId: string) =>
    request<AlertAuditEntry[]>(`/api/v1/alerts/${encodeURIComponent(alertId)}/audit`, token),

  claimAlert: (token: string, alertId: string) =>
    request<AlertItem>(`/api/v1/alerts/${encodeURIComponent(alertId)}/claim`, token, {
      method: 'POST'
    }),

  releaseAlert: (token: string, alertId: string) =>
    request<AlertItem>(`/api/v1/alerts/${encodeURIComponent(alertId)}/release`, token, {
      method: 'POST'
    }),

  resolveAlert: (token: string, alertId: string, note: string) =>
    request<AlertItem>(`/api/v1/alerts/${encodeURIComponent(alertId)}/resolve`, token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note })
    })
};
