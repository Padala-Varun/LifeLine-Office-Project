import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Battery } from './battery';
import { Reporter } from './reporter';

@Component({
  selector: 'app-root',
  imports: [DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly battery = inject(Battery);
  protected readonly reporter = inject(Reporter);
  protected readonly connecting = signal(false);
  protected readonly problem = signal('');

  protected async connect(event: Event, name: string, code: string): Promise<void> {
    event.preventDefault();
    if (!name.trim() || this.connecting()) {
      return;
    }
    this.connecting.set(true);
    this.problem.set('');
    switch (await this.reporter.connect(name.trim(), code.trim())) {
      case 'codeNeeded':
        this.problem.set(
          code.trim() ? 'That join code is not right. Check it and try again.' : 'Enter the join code to add this device.',
        );
        break;
      case 'tooManyTries':
        this.problem.set('Too many wrong codes. Wait 15 minutes, then try again.');
        break;
      case 'unreachable':
        this.problem.set("Couldn't reach the server. Check the connection and try again.");
        break;
    }
    this.connecting.set(false);
  }
}
