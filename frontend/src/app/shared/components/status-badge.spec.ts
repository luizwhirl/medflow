import { TestBed } from '@angular/core/testing';

import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  it('exibe o rótulo em português do status', () => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', 'EM_FALTA');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('Em falta');
  });
});
