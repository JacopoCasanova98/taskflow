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
