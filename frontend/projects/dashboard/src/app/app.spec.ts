import { Alert, Device } from './models';
import { EMPTY_STATE, ago, applyUpdate, sortDevices } from './state';

const device = (over: Partial<Device> = {}): Device => ({
  id: 'd1',
  name: 'Tablet',
  platform: 'Android · Chrome',
  level: 24,
  charging: false,
  online: true,
  lastSeenAt: '2026-01-01T09:00:00Z',
  ...over,
});

const alert = (over: Partial<Alert> = {}): Alert => ({
  id: 1,
  deviceId: 'd1',
  deviceName: 'Tablet',
  level: 24,
  raisedAt: '2026-01-01T09:00:00Z',
  resolvedAt: null,
  resolution: null,
  ...over,
});

describe('applyUpdate', () => {
  it('opens an alert once, even if the message is repeated', () => {
    const update = { device: device(), alert: alert() };
    const state = applyUpdate(applyUpdate(EMPTY_STATE, update), update);
    expect(state.devices.length).toBe(1);
    expect(state.openAlerts.length).toBe(1);
  });

  it('moves a resolved alert to history, and a late "raised" message does not reopen it', () => {
    const raised = { device: device(), alert: alert() };
    const resolved = {
      device: device({ charging: true, lastSeenAt: '2026-01-01T09:05:00Z' }),
      alert: alert({ resolvedAt: '2026-01-01T09:05:00Z', resolution: 'CHARGING' as const }),
    };
    const state = applyUpdate(applyUpdate(applyUpdate(EMPTY_STATE, raised), resolved), raised);
    expect(state.openAlerts).toEqual([]);
    expect(state.recentAlerts.length).toBe(1);
    expect(state.devices[0].charging).toBe(true);
  });
});

it('lists the emptiest online device first and offline devices last', () => {
  const sorted = sortDevices([
    device({ id: 'offline', level: 5, online: false }),
    device({ id: 'full', level: 90 }),
    device({ id: 'new', level: null }),
    device({ id: 'low', level: 12 }),
  ]);
  expect(sorted.map((d) => d.id)).toEqual(['low', 'full', 'new', 'offline']);
});

it('describes elapsed time', () => {
  const start = Date.parse('2026-01-01T09:00:00Z');
  const at = (seconds: number) => ago('2026-01-01T09:00:00Z', start + seconds * 1000);
  expect([at(-3), at(59), at(60), at(3600), at(86400), at(200000)]).toEqual([
    'just now',
    'just now',
    '1 min ago',
    '1 h ago',
    '1 day ago',
    '2 days ago',
  ]);
});
