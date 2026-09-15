import { BoardStatisticsApi } from '../statistics/board-statistics-api';
import { of as statisticsOf } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { BoardWorkspace } from './board-workspace';

const board = { id: 'one', name: 'Product roadmap', createdAt: '', updatedAt: '' };
describe('Board workspace page', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let http: HttpTestingController;
  let element: HTMLElement;
  const params = new BehaviorSubject(convertToParamMap({ boardId: 'one' }));
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        // This suite isolates its existing workspace behavior; statistics HTTP is covered separately.
        {
          provide: BoardStatisticsApi,
          useValue: {
            getStatistics: () =>
              statisticsOf({
                totalTasks: 0,
                overdueTasks: 0,
                priorityDistribution: { low: 0, medium: 0, high: 0 },
                statusDistribution: [],
              }),
          },
        },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    fixture = TestBed.createComponent(BoardWorkspace);
    http = TestBed.inject(HttpTestingController);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });
  afterEach(() => http.verify());
  it('announces loading and renders the Board header with zero-column guidance', async () => {
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Loading board…');
    expect(element.textContent).not.toContain('no columns');
    http.expectOne('/api/boards/one').flush(board);
    http.expectOne('/api/boards/one/columns').flush([]);
    await fixture.whenStable();
    expect(element.querySelector('h1')?.textContent).toBe(board.name);
    expect(element.textContent).toContain('This board has no columns yet.');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/boards');
    expect(element.querySelector('app-column-controls button')?.textContent?.trim()).toBe(
      'Add column',
    );
  });
  it('renders semantic lanes and Task summaries in server order with empty-column guidance', async () => {
    http.expectOne('/api/boards/one').flush(board);
    http.expectOne('/api/boards/one/columns').flush([
      { id: 'z', name: 'Planning', position: 0 },
      { id: 'a', name: 'Doing', position: 1 },
    ]);
    http.expectOne('/api/columns/a/tasks').flush([]);
    http.expectOne('/api/columns/z/tasks').flush([
      {
        id: 'z',
        title: 'Second alphabetically',
        position: 0,
        priority: 'HIGH',
        dueDate: '2026-09-12',
        description: 'Hidden description',
      },
      { id: 'a', title: 'First alphabetically', position: 1 },
    ]);
    await fixture.whenStable();
    expect(
      Array.from(element.querySelectorAll('app-column-controls h2'), (h) => h.textContent),
    ).toEqual(['Planning', 'Doing']);
    expect(
      Array.from(element.querySelectorAll('li .task-title'), (button) =>
        button.textContent?.trim(),
      ),
    ).toEqual(['Second alphabetically', 'First alphabetically']);
    expect(element.querySelectorAll('app-column-controls section[aria-labelledby]').length).toBe(2);
    expect(element.textContent).toContain('No tasks yet.');
    for (const hidden of ['HIGH', '2026-09-12', 'Hidden description'])
      expect(element.textContent).not.toContain(hidden);
    expect(element.querySelector('input:not([type="search"]), form, [draggable]')).toBeNull();
    expect(element.textContent).toContain('Add task');
    expect(element.querySelector('li button')?.getAttribute('aria-label')).toContain('View task:');
  });
  it('shows a safe not-found message and real back link', async () => {
    http
      .expectOne('/api/boards/one')
      .flush(
        { code: 'BOARD_NOT_FOUND', detail: 'Secret' },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();
    expect(element.querySelector('h1')?.textContent).toBe('Board not found');
    expect(element.textContent).toContain('This board is no longer available.');
    expect(element.textContent).not.toContain('Secret');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/boards');
  });
  it('announces safe errors and retries through a real button', async () => {
    http
      .expectOne('/api/boards/one')
      .flush({ detail: 'Secret' }, { status: 500, statusText: 'Failure' });
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent).toBe(
      "We couldn't load this board.",
    );
    expect(element.textContent).not.toContain('Secret');
    const retry = element.querySelector('button')!;
    expect(retry.textContent).toBe('Retry');
    retry.click();
    await fixture.whenStable();
    expect(element.textContent).toContain('Loading board…');
    http.expectOne('/api/boards/one').flush(board);
    http.expectOne('/api/boards/one/columns').flush([]);
    await fixture.whenStable();
    expect(element.querySelector('h1')?.textContent).toBe(board.name);
  });
});
