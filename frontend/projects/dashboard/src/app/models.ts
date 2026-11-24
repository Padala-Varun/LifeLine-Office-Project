export interface Device {
  id: string;
  name: string;
  platform: string;
  /** Null until the first reading arrives. */
  level: number | null;
  charging: boolean | null;
  online: boolean;
  lastSeenAt: string;
}

export interface Alert {
  id: number;
  deviceId: string;
  deviceName: string;
  /** Charge when the alert was raised. */
  level: number;
  raisedAt: string;
  resolvedAt: string | null;
  resolution: 'CHARGING' | 'RECOVERED' | null;
}

export interface Snapshot {
  lowBelow: number;
  serverTime: string;
  devices: Device[];
  openAlerts: Alert[];
  recentAlerts: Alert[];
}

/** One WebSocket message. */
export interface Update {
  device: Device;
  alert: Alert | null;
}
