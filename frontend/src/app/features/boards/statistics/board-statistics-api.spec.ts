import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { BoardStatisticsApi } from './board-statistics-api';
import { BoardStatistics } from './board-statistics.models';

describe('BoardStatisticsApi', () => {
  it('GETs the encoded Board with HttpParams asOf and no manually attached security headers', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const data: BoardStatistics = {
      totalTasks: 0,
      overdueTasks: 0,
      priorityDistribution: { low: 0, medium: 0, high: 0 },
      statusDistribution: [],
    };
    let received: BoardStatistics | undefined;
    TestBed.inject(BoardStatisticsApi)
      .getStatistics('board/id', '2026-09-13')
      .subscribe((value) => (received = value));
    const request = http.expectOne('/api/boards/board%2Fid/statistics?asOf=2026-09-13');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['asOf']);
    expect(request.request.headers.keys()).toEqual([]);
    request.flush(data);
    expect(received).toEqual(data);
    http.verify();
  });
});
