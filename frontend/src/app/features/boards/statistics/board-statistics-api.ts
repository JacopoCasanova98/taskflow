import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { BoardStatistics } from './board-statistics.models';

@Injectable({ providedIn: 'root' })
export class BoardStatisticsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL) + '/boards';

  getStatistics(boardId: string, asOf: string) {
    return this.http.get<BoardStatistics>(
      this.base + '/' + encodeURIComponent(boardId) + '/statistics',
      {
        params: new HttpParams().set('asOf', asOf),
      },
    );
  }
}
