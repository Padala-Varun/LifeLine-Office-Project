import { describePlatform, isIos, toReading } from './battery';
import { joinCodeFrom } from './reporter';

describe('join link', () => {
  it('takes the code from the link, and copes with none', () => {
    expect(joinCodeFrom('#join=ABCD2345')).toBe('ABCD2345');
    expect(joinCodeFrom('')).toBe('');
    expect(joinCodeFrom('#other=1')).toBe('');
  });
});

describe('battery helpers', () => {
  it('turns the browser fraction into a whole percentage', () => {
    expect(toReading({ level: 0.29, charging: false })).toEqual({ level: 29, charging: false });
    expect(toReading({ level: 0.24, charging: true })).toEqual({ level: 24, charging: true });
    expect(toReading({ level: 1, charging: true }).level).toBe(100);
  });

  it('recognises iPhones, and iPads that pretend to be Macs', () => {
    expect(isIos({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)', maxTouchPoints: 5 })).toBe(true);
    expect(isIos({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X)', maxTouchPoints: 5 })).toBe(true);
    expect(isIos({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X)', maxTouchPoints: 0 })).toBe(false);
  });

  it('names the system and browser', () => {
    expect(describePlatform('Mozilla/5.0 (Linux; Android 10; K) Chrome/140.0.0.0 Mobile Safari/537.36')).toEqual({
      system: 'Android',
      browser: 'Chrome',
    });
    expect(describePlatform('Mozilla/5.0 (Windows NT 10.0) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0')).toEqual({
      system: 'Windows',
      browser: 'Edge',
    });
  });
});
