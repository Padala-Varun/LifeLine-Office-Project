import { Component, computed, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Live } from './live';
import { Alert } from './models';
import { ago, sortDevices } from './state';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly live = inject(Live);
  private readonly title = inject(Title);

  protected readonly signingIn = signal(false);
  protected readonly signInError = signal<string | null>(null);

  protected readonly devices = computed(() => {
    const low = new Set(this.live.state().openAlerts.map((a) => a.deviceId));
    return sortDevices(this.live.state().devices).map((device) => ({ ...device, low: low.has(device.id) }));
  });
  protected readonly online = computed(() => this.devices().filter((d) => d.online).length);
  protected readonly openAlerts = computed(() => {
    const byId = new Map(this.devices().map((d) => [d.id, d]));
    return this.live.state().openAlerts.map((alert) => ({ ...alert, device: byId.get(alert.deviceId) }));
  });

  constructor() {
    // Visible in the browser tab even when another page is in front.
    effect(() => {
      const count = this.live.state().openAlerts.length;
      this.title.setTitle(count ? `(${count}) LifeLine dashboard` : 'LifeLine dashboard');
    });
  }

  protected async signIn(event: Event, username: string, password: string): Promise<void> {
    event.preventDefault();
    this.signingIn.set(true);
    this.signInError.set(await this.live.signIn(username, password));
    this.signingIn.set(false);
  }

  protected ago(iso: string): string {
    return ago(iso, this.live.now());
  }

  protected outcome(alert: Alert): string {
    return alert.resolution === 'CHARGING' ? 'Put on charge' : 'Charge recovered';
  }
}
