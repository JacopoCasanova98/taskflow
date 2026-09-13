import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { TaskApi } from './task-api';
import { Task } from './task.models';

describe('TaskApi', () => {
  it('reads the exact response in server order', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const response: Task[] = ['z', 'a'].map((id, position) => ({
      id,
      position,
      createdAt: '2026-09-11T10:00:00Z',
      updatedAt: '2026-09-11T10:00:00Z',
      columnId: 'parent',
      title: id,
      description: null,
      priority: 'MEDIUM',
      dueDate: null,
    }));
    const received = vi.fn();
    TestBed.inject(TaskApi).listTasks('parent').subscribe(received);
    const request = http.expectOne('/api/columns/parent/tasks');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(received).toHaveBeenCalledExactlyOnceWith(response);
    http.verify();
  });
});

describe('TaskApi CRUD', () => {
  let api: TaskApi;
  let http: HttpTestingController;
  const content = {
    title: 'Fix login',
    description: 'Plain text',
    priority: 'HIGH' as const,
    dueDate: '2026-09-30',
  };
  const task: Task = {
    ...content,
    id: 'task',
    columnId: 'column',
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
    api = TestBed.inject(TaskApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('searches with encoded HttpParams and returns Task responses', () => {
    const next = vi.fn();
    api.searchBoardTasks('board/id', 'Login  %_\\ &+?').subscribe(next);
    const request = http.expectOne((r) => r.url === '/api/boards/board%2Fid/tasks/search');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('Login  %_\\ &+?');
    expect(request.request.urlWithParams).toContain('%25');
    expect(request.request.urlWithParams).toContain('%26');
    expect(request.request.urlWithParams).toContain('%2B');
    request.flush([task]);
    expect(next).toHaveBeenCalledExactlyOnceWith([task]);
  });
  it('gets one canonical Task directly', () => {
    const next = vi.fn();
    api.getTask('task').subscribe(next);
    const request = http.expectOne('/api/tasks/task');
    expect(request.request.method).toBe('GET');
    request.flush(task);
    expect(next).toHaveBeenCalledExactlyOnceWith(task);
  });
  it('creates with only content and accepts 201 plus Location', () => {
    const next = vi.fn();
    api.createTask('column', content).subscribe(next);
    const request = http.expectOne('/api/columns/column/tasks');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(content);
    request.flush(task, {
      status: 201,
      statusText: 'Created',
      headers: { Location: '/api/tasks/task' },
    });
    expect(next).toHaveBeenCalledExactlyOnceWith(task);
  });
  it('allows omitted priority in the create contract', () => {
    api.createTask('column', { title: 'Default', description: null, dueDate: null }).subscribe();
    const request = http.expectOne('/api/columns/column/tasks');
    expect(request.request.body).toEqual({ title: 'Default', description: null, dueDate: null });
    request.flush(task);
  });
  it('updates with full PUT content and permits clearing nullable fields', () => {
    const body = { ...content, description: null, dueDate: null };
    const next = vi.fn();
    api.updateTask('task', body).subscribe(next);
    const request = http.expectOne('/api/tasks/task');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush({ ...task, ...body });
    expect(next).toHaveBeenCalledExactlyOnceWith({ ...task, ...body });
  });
  it('places with only columnId and final position and receives the canonical Task', () => {
    const next = vi.fn();
    api.placeTask('task/id', { columnId: 'target', position: 0 }).subscribe(next);
    const request = http.expectOne('/api/tasks/task%2Fid/placement');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ columnId: 'target', position: 0 });
    request.flush(task);
    expect(next).toHaveBeenCalledExactlyOnceWith(task);
  });
  it('deletes without a request body and accepts bodyless 204', () => {
    const complete = vi.fn();
    api.deleteTask('task').subscribe({ complete });
    const request = http.expectOne('/api/tasks/task');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(complete).toHaveBeenCalledOnce();
  });
});
