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

describe('ColumnApi mutations', () => {
  let api: ColumnApi;
  let http: HttpTestingController;
  const column: Column = {
    id: 'c',
    name: 'Doing',
    position: 0,
    createdAt: 'created',
    updatedAt: 'updated',
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(ColumnApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('creates with name only and returns the canonical 201 body', () => {
    const received = vi.fn();
    api.createColumn('b', { name: 'Doing' }).subscribe(received);
    const request = http.expectOne('/api/boards/b/columns');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Doing' });
    request.flush(column, { status: 201, statusText: 'Created' });
    expect(received).toHaveBeenCalledExactlyOnceWith(column);
  });
  it('renames with PATCH and name only', () => {
    const received = vi.fn();
    api.renameColumn('c', { name: 'Doing' }).subscribe(received);
    const request = http.expectOne('/api/columns/c');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ name: 'Doing' });
    request.flush(column);
    expect(received).toHaveBeenCalledExactlyOnceWith(column);
  });
  it('deletes without a body and accepts 204', () => {
    const complete = vi.fn();
    api.deleteColumn('c').subscribe({ complete });
    const request = http.expectOne('/api/columns/c');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(complete).toHaveBeenCalledOnce();
  });
  it('persists the complete order with PUT and returns server order', () => {
    const received = vi.fn();
    api.reorderColumns('b', { columnIds: ['c', 'a'] }).subscribe(received);
    const request = http.expectOne('/api/boards/b/columns/order');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ columnIds: ['c', 'a'] });
    const response = [column, { ...column, id: 'a', position: 1 }];
    request.flush(response);
    expect(received).toHaveBeenCalledExactlyOnceWith(response);
  });
});
