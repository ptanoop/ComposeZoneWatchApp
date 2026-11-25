export type UserRole = 'INGEST' | 'OPERATOR' | 'SUPERVISOR';

export interface AuthSession {
  token: string;
  username: string;
  role: UserRole;
}

export interface AssetPosition {
  assetId: string;
  observedAt: string;
  lat: number;
  lon: number;
  speedKn: number | null;
  headingDeg: number | null;
  source: string | null;
}

export interface TrackPoint {
  reportId: string;
  assetId: string;
  observedAt: string;
  lat: number;
  lon: number;
  speedKn: number | null;
  headingDeg: number | null;
  source: string | null;
  sourceType: 'SATELLITE' | 'GROUND' | 'MOBILE' | 'UNKNOWN';
}

export interface ZoneFeature {
  zoneId: string;
  name: string;
  zoneClass: 'RESTRICTED' | 'ADVISORY';
  geojson: string;
}

export type AlertStatus = 'OPEN' | 'CLAIMED' | 'RESOLVED';

export interface AlertItem {
  alertId: string;
  assetId: string;
  zoneId: string;
  zoneName: string;
  status: AlertStatus;
  owner: string | null;
  resolutionNote: string | null;
  triggeredAt: string;
  updatedAt: string;
}

export interface AlertAuditEntry {
  auditId: number;
  alertId: string;
  action: 'OPENED' | 'CLAIMED' | 'RELEASED' | 'RESOLVED';
  actor: string | null;
  note: string | null;
  occurredAt: string;
}

export interface SystemStats {
  received: number;
  accepted: number;
  duplicates: number;
  rejected: number;
  assets: number;
  openAlerts: number;
  claimedAlerts: number;
  resolvedAlerts: number;
}

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
}
