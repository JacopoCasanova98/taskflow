import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { ColumnApi } from './column-api';
import { Column } from './column.models';

describe('ColumnApi', () => {
  it('reads the exact response in server order', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const response: Column[] = ['z', 'a'].map((id, position) => ({
      id,
      position,
      createdAt: '2026-09-11T10:00:00Z',
      updatedAt: '2026-09-11T10:00:00Z',
      name: id,
    }));
    const received = vi.fn();
    TestBed.inject(ColumnApi).listColumns('parent').subscribe(received);
    const request = http.expectOne('/api/boards/parent/columns');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(received).toHaveBeenCalledExactlyOnceWith(response);
    http.verify();
  });
});
