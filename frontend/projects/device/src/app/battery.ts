import { Injectable, signal } from '@angular/core';

/** The parts of the browser's BatteryManager we use. TypeScript's DOM library does not include it. */
export interface BatteryManagerLike extends EventTarget {
  readonly level: number;
  readonly charging: boolean;
}

export interface Reading {
  level: number;
  charging: boolean;
}

export type Problem = 'insecure' | 'ios' | 'unsupported';

type BatteryNavigator = Navigator & { getBattery?: () => Promise<BatteryManagerLike> };

export function toReading(battery: Pick<BatteryManagerLike, 'level' | 'charging'>): Reading {
  // Rounded, because 0.29 * 100 is 28.999999999999996.
  return { level: Math.round(battery.level * 100), charging: battery.charging };
}

export function isIos(nav: Pick<Navigator, 'userAgent' | 'maxTouchPoints'>): boolean {
  // iPads identify themselves as Macs, but Macs have no touch screen.
  return /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1);
}

export function describePlatform(ua: string): { system: string; browser: string } {
  const system = /Android/.test(ua)
    ? 'Android'
    : /CrOS/.test(ua)
      ? 'Chromebook'
      : /Windows/.test(ua)
        ? 'Windows'
        : /Macintosh/.test(ua)
          ? 'Mac'
          : /Linux/.test(ua)
            ? 'Linux'
            : 'Unknown';
  const browser = /SamsungBrowser/.test(ua)
    ? 'Samsung Internet'
    : /EdgA?\//.test(ua)
      ? 'Edge'
      : /OPR\//.test(ua)
        ? 'Opera'
        : /Chrome\//.test(ua)
          ? 'Chrome'
          : 'Browser';
  return { system, browser };
}

/** Live charge of the device this page is open on. */
@Injectable({ providedIn: 'root' })
export class Battery {
  readonly reading = signal<Reading | null>(null);
  readonly problem = signal<Problem | null>(null);

  constructor() {
    void this.start();
  }

  private async start(): Promise<void> {
    if (!window.isSecureContext) {
      this.problem.set('insecure');
      return;
    }
    const nav = navigator as BatteryNavigator;
    if (!nav.getBattery) {
      this.problem.set(isIos(navigator) ? 'ios' : 'unsupported');
      return;
    }
    try {
      const battery = await nav.getBattery();
      const update = () => this.reading.set(toReading(battery));
      battery.addEventListener('levelchange', update);
      battery.addEventListener('chargingchange', update);
      update();
    } catch {
      this.problem.set('unsupported');
    }
  }
}
