import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { Board, CreateBoardRequest, RenameBoardRequest } from './board.models';

@Injectable({ providedIn: 'root' })
export class BoardApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL) + '/boards';

  listBoards() {
    return this.http.get<Board[]>(this.base);
  }
  getBoard(boardId: string) {
    return this.http.get<Board>(this.base + '/' + encodeURIComponent(boardId));
  }
  createBoard(request: CreateBoardRequest) {
    return this.http.post<Board>(this.base, request);
  }
  renameBoard(boardId: string, request: RenameBoardRequest) {
    return this.http.patch<Board>(this.base + '/' + encodeURIComponent(boardId), request);
  }
  deleteBoard(boardId: string) {
    return this.http.delete<void>(this.base + '/' + encodeURIComponent(boardId));
  }
}
