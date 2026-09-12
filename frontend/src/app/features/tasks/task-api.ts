import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { Task } from './task.models';

@Injectable({ providedIn: 'root' })
export class TaskApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  listTasks(columnId: string) {
    return this.http.get<Task[]>(this.base + '/columns/' + encodeURIComponent(columnId) + '/tasks');
  }
}
