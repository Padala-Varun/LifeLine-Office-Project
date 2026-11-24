import { Injectable, effect, inject, signal } from '@angular/core';
import { Battery, Reading, describePlatform } from './battery';

interface Identity {
  deviceId: string;
  token: string;
  name: string;
  joinCode?: string;
}

export type ConnectResult = 'connected' | 'codeNeeded' | 'tooManyTries' | 'unreachable';

const STORAGE_KEY = 'lifeline.device';
const HEARTBEAT_MS = 60_000;

/** Registers this device once, then keeps the server up to date with its charge. */
@Injectable({ providedIn: 'root' })
export class Reporter {
  private readonly battery = inject(Battery);
  private readonly platform = describePlatform(navigator.userAgent);

  readonly identity = signal<Identity | null>(loadIdentity());
  readonly suggestedName = `${this.platform.system} device`;
  /** From a link such as /device/#join=ABCD2345. */
  readonly linkCode = joinCodeFrom(location.hash);
  readonly codeNeeded = signal(false);
  readonly alertOpen = signal(false);
  readonly lastSent = signal<Date | null>(null);
  readonly offline = signal(false);

  private sending = false;
  private sendAgain = false;

  constructor() {
    // Report as soon as the charge changes, and right after the device is named.
    effect(() => {
      if (this.battery.reading() && this.identity()) {
        void this.send();
      }
    });
    setInterval(() => void this.send(), HEARTBEAT_MS);
    window.addEventListener('online', () => void this.send());

    // Browsers may freeze a hidden page at any time, so leave the latest value behind.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.sendBeforeSleep();
      } else {
        void this.send();
      }
    });
  }

  async connect(name: string, joinCode: string): Promise<ConnectResult> {
    try {
      const response = await fetch('/api/device/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, platform: `${this.platform.system} · ${this.platform.browser}`, joinCode }),
      });
      if (response.status === 403) {
        this.codeNeeded.set(true);
        return 'codeNeeded';
      }
      if (!response.ok) {
        return response.status === 429 ? 'tooManyTries' : 'unreachable';
      }
      const { deviceId, token } = (await response.json()) as Omit<Identity, 'name'>;
      const identity = { deviceId, token, name, joinCode };
      saveIdentity(identity);
      this.identity.set(identity);
      return 'connected';
    } catch {
      return 'unreachable';
    }
  }

  /** One request at a time; if the charge moves meanwhile, the newest value goes next. */
  private async send(): Promise<void> {
    if (this.sending) {
      this.sendAgain = true;
      return;
    }
    this.sending = true;
    try {
      do {
        this.sendAgain = false;
        await this.sendOnce();
      } while (this.sendAgain);
    } finally {
      this.sending = false;
    }
  }

  private async sendOnce(): Promise<void> {
    const identity = this.identity();
    const reading = this.battery.reading();
    if (!identity || !reading) {
      return;
    }
    try {
      const response = await this.post(identity, reading, false);
      if (response.status === 401) {
        // The server no longer knows this device (for example, its data was reset).
        const result = await this.connect(identity.name, identity.joinCode ?? this.linkCode);
        if (result === 'codeNeeded') {
          this.identity.set(null); // The join code changed, so ask for the new one.
          return;
        }
        this.offline.set(result !== 'connected');
        this.sendAgain = result === 'connected';
        return;
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const { alertOpen } = (await response.json()) as { alertOpen: boolean };
      this.alertOpen.set(alertOpen);
      this.lastSent.set(new Date());
      this.offline.set(false);
    } catch {
      this.offline.set(true); // The next heartbeat tries again.
    }
  }

  private sendBeforeSleep(): void {
    const identity = this.identity();
    const reading = this.battery.reading();
    if (identity && reading) {
      this.post(identity, reading, true).catch(() => undefined);
    }
  }

  private post(identity: Identity, reading: Reading, keepalive: boolean): Promise<Response> {
    return fetch('/api/device/readings', {
      method: 'POST',
      keepalive,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
      body: JSON.stringify({ deviceId: identity.deviceId, ...reading }),
    });
  }
}

export function joinCodeFrom(hash: string): string {
  return new URLSearchParams(hash.replace(/^#/, '')).get('join') ?? '';
}

function loadIdentity(): Identity | null {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
  } catch {
    return null;
  }
}

function saveIdentity(identity: Identity): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(identity));
  } catch {
    // Storage is blocked: the device works until the page is closed.
  }
}
