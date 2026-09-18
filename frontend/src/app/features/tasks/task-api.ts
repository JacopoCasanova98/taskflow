import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { Task, CreateTaskRequest, UpdateTaskRequest, TaskPlacementRequest } from './task.models';

@Injectable({ providedIn: 'root' })
export class TaskApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  searchBoardTasks(boardId: string, query: string) {
    return this.http.get<Task[]>(
      this.base + '/boards/' + encodeURIComponent(boardId) + '/tasks/search',
      {
        params: new HttpParams().set('q', query),
      },
    );
  }
  getTask(taskId: string) {
    return this.http.get<Task>(this.base + '/tasks/' + encodeURIComponent(taskId));
  }
  createTask(columnId: string, request: CreateTaskRequest) {
    return this.http.post<Task>(
      this.base + '/columns/' + encodeURIComponent(columnId) + '/tasks',
      request,
    );
  }
  updateTask(taskId: string, request: UpdateTaskRequest) {
    return this.http.put<Task>(this.base + '/tasks/' + encodeURIComponent(taskId), request);
  }
  placeTask(taskId: string, request: TaskPlacementRequest) {
    return this.http.put<Task>(this.base + '/tasks/' + encodeURIComponent(taskId) + '/placement', {
      columnId: request.columnId,
      position: request.position,
    });
  }
  deleteTask(taskId: string) {
    return this.http.delete<void>(this.base + '/tasks/' + encodeURIComponent(taskId));
  }
  listTasks(columnId: string) {
    return this.http.get<Task[]>(this.base + '/columns/' + encodeURIComponent(columnId) + '/tasks');
  }
  listBoardTasks(boardId: string) {
    return this.http.get<Task[]>(this.base + '/boards/' + encodeURIComponent(boardId) + '/tasks');
  }
}
