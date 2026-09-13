import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardApi } from './board-api';

describe('BoardApi', () => {
  let api: BoardApi;
  let http: HttpTestingController;
  const board = {
    id: '49a8b874-b795-42b0-80db-ed1c384c8301',
    name: 'Roadmap',
    createdAt: '2026-09-11T10:00:00Z',
    updatedAt: '2026-09-11T10:00:00Z',
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(BoardApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists the exact response in server order', () => {
    const response = [board, { ...board, id: 'second', name: 'Alpha' }];
    const received = vi.fn();
    api.listBoards().subscribe(received);
    const request = http.expectOne('/api/boards');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(received).toHaveBeenCalledExactlyOnceWith(response);
  });
  it('gets an owned Board by id', () => {
    const received = vi.fn();
    api.getBoard(board.id).subscribe(received);
    const request = http.expectOne('/api/boards/' + board.id);
    expect(request.request.method).toBe('GET');
    request.flush(board);
    expect(received).toHaveBeenCalledExactlyOnceWith(board);
  });
  it('creates using name only and returns the 201 Board body', () => {
    const received = vi.fn();
    api.createBoard({ name: board.name }).subscribe(received);
    const request = http.expectOne('/api/boards');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: board.name });
    request.flush(board, {
      status: 201,
      statusText: 'Created',
      headers: { Location: '/api/boards/' + board.id },
    });
    expect(received).toHaveBeenCalledExactlyOnceWith(board);
  });
  it('renames using PATCH and name only', () => {
    const response = { ...board, name: 'Renamed', updatedAt: '2026-09-11T11:00:00Z' };
    const received = vi.fn();
    api.renameBoard(board.id, { name: 'Renamed' }).subscribe(received);
    const request = http.expectOne('/api/boards/' + board.id);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ name: 'Renamed' });
    request.flush(response);
    expect(received).toHaveBeenCalledExactlyOnceWith(response);
  });
  it('deletes with no request body and accepts bodyless 204', () => {
    const complete = vi.fn();
    api.deleteBoard(board.id).subscribe({ complete });
    const request = http.expectOne('/api/boards/' + board.id);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(complete).toHaveBeenCalledTimes(1);
  });
  it('uses the injected API prefix', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/taskflow/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(BoardApi).listBoards().subscribe();
    http.expectOne('/taskflow/api/boards').flush([]);
  });
  it('propagates transport errors unchanged', () => {
    const error = vi.fn();
    api.listBoards().subscribe({ error });
    http
      .expectOne('/api/boards')
      .flush({ code: 'UNKNOWN' }, { status: 500, statusText: 'Server Error' });
    expect(error.mock.calls[0][0].status).toBe(500);
  });
});
