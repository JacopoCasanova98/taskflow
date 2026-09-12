import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { Column } from './column.models';

@Injectable({ providedIn: 'root' })
export class ColumnApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  listColumns(boardId: string) {
    return this.http.get<Column[]>(
      this.base + '/boards/' + encodeURIComponent(boardId) + '/columns',
    );
  }
}
