import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Alert, Snapshot, Update } from './models';
import { EMPTY_STATE, applyUpdate, fromSnapshot } from './state';

const MAX_RETRY_MS = 15_000;
const TOAST_MS = 12_000;
const MAX_TOASTS = 3; // The rest are in the "Needs charging" list.

/** Session, snapshot and live updates for the dashboard. */
@Injectable({ providedIn: 'root' })
export class Live {
  private readonly http = inject(HttpClient);

  readonly session = signal<'checking' | 'signedOut' | 'signedIn'>('checking');
  readonly connection = signal<'connecting' | 'live' | 'reconnecting'>('connecting');
  readonly state = signal(EMPTY_STATE);
  readonly lowBelow = signal(25);
  readonly toasts = signal<Alert[]>([]);
  /** Server time, so "2 min ago" is right even if this computer's clock is not. */
  readonly now = signal(Date.now());

  private clockOffset = 0;
  private socket: WebSocket | null = null;
  private retryMs = 1_000;
  private loadedOnce = false;

  constructor() {
    setInterval(() => this.tick(), 5_000);
    void this.resume();
  }

  /** @returns an error message, or null on success */
  async signIn(username: string, password: string): Promise<string | null> {
    try {
      const body = new HttpParams({ fromObject: { username, password } });
      await firstValueFrom(this.http.post('/api/auth/login', body));
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      switch (status) {
        case 401:
          return 'Wrong username or password.';
        case 429:
          return 'Too many wrong tries. Wait 15 minutes, then try again.';
        default:
          return "Couldn't reach the server. Check that it is running and try again.";
      }
    }
    this.session.set('signedIn');
    this.connect();
    return null;
  }

  async signOut(): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/logout', null)).catch(() => undefined);
    this.showSignIn();
    await this.currentUser().catch(() => undefined); // Fetches a fresh CSRF cookie for the next sign-in.
  }

  dismiss(alert: Alert): void {
    this.toasts.update((list) => list.filter((a) => a.id !== alert.id));
  }

  private async resume(): Promise<void> {
    try {
      if (await this.currentUser()) {
        this.session.set('signedIn');
        this.connect();
      } else {
        this.showSignIn();
      }
    } catch {
      this.connection.set('reconnecting');
      setTimeout(() => void this.resume(), this.nextRetry());
    }
  }

  private async currentUser(): Promise<string | null> {
    return (await firstValueFrom(this.http.get<{ username: string | null }>('/api/auth/me'))).username;
  }

  private showSignIn(): void {
    const socket = this.socket;
    this.socket = null;
    socket?.close();
    this.state.set(EMPTY_STATE);
    this.toasts.set([]);
    this.loadedOnce = false;
    this.session.set('signedOut');
  }

  /** Opens the socket first and loads the snapshot second, so nothing in between is missed. */
  private connect(): void {
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/dashboard`);
    this.socket = socket;
    const early: Update[] = [];
    let ready = false;

    socket.onopen = async () => {
      try {
        const snapshot = await firstValueFrom(this.http.get<Snapshot>('/api/dashboard'));
        if (this.socket !== socket) {
          return;
        }
        this.load(snapshot);
        ready = true;
        early.forEach((update) => this.apply(update));
        this.retryMs = 1_000;
        this.connection.set('live');
      } catch {
        socket.close();
      }
    };
    socket.onmessage = (event) => {
      const update = JSON.parse(event.data) as Update;
      if (ready) {
        this.apply(update);
      } else {
        early.push(update);
      }
    };
    socket.onclose = () => {
      if (this.socket !== socket) {
        return; // Signed out, or already replaced.
      }
      this.socket = null;
      this.connection.set('reconnecting');
      setTimeout(() => void this.resume(), this.nextRetry());
    };
  }

  private load(snapshot: Snapshot): void {
    const knownIds = new Set(this.state().openAlerts.map((a) => a.id));
    this.clockOffset = Date.parse(snapshot.serverTime) - Date.now();
    this.tick();
    this.lowBelow.set(snapshot.lowBelow);
    this.state.set(fromSnapshot(snapshot));
    if (this.loadedOnce) {
      // Raised while we were disconnected.
      snapshot.openAlerts.filter((a) => !knownIds.has(a.id)).forEach((a) => this.toast(a));
    }
    this.loadedOnce = true;
  }

  private apply(update: Update): void {
    const before = this.state();
    const after = applyUpdate(before, update);
    this.state.set(after);
    this.tick();

    const { alert } = update;
    if (!alert) {
      return;
    }
    const isOpen = (list: Alert[]) => list.some((a) => a.id === alert.id);
    if (alert.resolvedAt) {
      this.dismiss(alert);
    } else if (isOpen(after.openAlerts) && !isOpen(before.openAlerts)) {
      this.toast(alert);
    }
  }

  private toast(alert: Alert): void {
    this.toasts.update((list) => [alert, ...list].slice(0, MAX_TOASTS));
    setTimeout(() => this.dismiss(alert), TOAST_MS);
  }

  private tick(): void {
    this.now.set(Date.now() + this.clockOffset);
  }

  private nextRetry(): number {
    const wait = this.retryMs;
    this.retryMs = Math.min(this.retryMs * 2, MAX_RETRY_MS);
    return wait;
  }
}
