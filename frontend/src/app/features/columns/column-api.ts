import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import {
  Column,
  CreateColumnRequest,
  RenameColumnRequest,
  ReorderColumnsRequest,
} from './column.models';

@Injectable({ providedIn: 'root' })
export class ColumnApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  createColumn(boardId: string, request: CreateColumnRequest) {
    return this.http.post<Column>(
      this.base + '/boards/' + encodeURIComponent(boardId) + '/columns',
      request,
    );
  }
  renameColumn(columnId: string, request: RenameColumnRequest) {
    return this.http.patch<Column>(this.base + '/columns/' + encodeURIComponent(columnId), request);
  }
  deleteColumn(columnId: string) {
    return this.http.delete<void>(this.base + '/columns/' + encodeURIComponent(columnId));
  }
  reorderColumns(boardId: string, request: ReorderColumnsRequest) {
    return this.http.put<Column[]>(
      this.base + '/boards/' + encodeURIComponent(boardId) + '/columns/order',
      request,
    );
  }
  listColumns(boardId: string) {
    return this.http.get<Column[]>(
      this.base + '/boards/' + encodeURIComponent(boardId) + '/columns',
    );
  }
}
