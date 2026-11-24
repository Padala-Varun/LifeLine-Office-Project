import { Alert, Device, Snapshot, Update } from './models';

export const RECENT_LIMIT = 20;

export interface DashboardState {
  devices: Device[];
  openAlerts: Alert[];
  recentAlerts: Alert[];
}

export const EMPTY_STATE: DashboardState = { devices: [], openAlerts: [], recentAlerts: [] };

export function fromSnapshot(snapshot: Snapshot): DashboardState {
  return {
    devices: snapshot.devices,
    openAlerts: snapshot.openAlerts,
    recentAlerts: snapshot.recentAlerts,
  };
}

/** Safe to apply twice, or after a newer snapshot: stale messages change nothing. */
export function applyUpdate(state: DashboardState, { device, alert }: Update): DashboardState {
  const known = state.devices.find((d) => d.id === device.id);
  const isStale = known !== undefined && Date.parse(known.lastSeenAt) > Date.parse(device.lastSeenAt);
  const devices = isStale ? state.devices : [...state.devices.filter((d) => d.id !== device.id), device];

  if (!alert) {
    return { ...state, devices };
  }
  const alreadyResolved = state.recentAlerts.some((a) => a.id === alert.id);
  if (alreadyResolved && !alert.resolvedAt) {
    return { ...state, devices };
  }
  const openAlerts = state.openAlerts.filter((a) => a.id !== alert.id);
  const recentAlerts = state.recentAlerts.filter((a) => a.id !== alert.id);
  return alert.resolvedAt
    ? { devices, openAlerts, recentAlerts: [alert, ...recentAlerts].slice(0, RECENT_LIMIT) }
    : { devices, openAlerts: [alert, ...openAlerts], recentAlerts };
}

/** Devices that need attention first, offline ones last. */
export function sortDevices(devices: Device[]): Device[] {
  return [...devices].sort(
    (a, b) =>
      Number(b.online) - Number(a.online) ||
      (a.level ?? 101) - (b.level ?? 101) ||
      a.name.localeCompare(b.name),
  );
}

export function ago(iso: string, now: number): string {
  const seconds = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? '1 day ago' : `${days} days ago`;
}
