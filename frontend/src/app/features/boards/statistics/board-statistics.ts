import { Component, inject } from '@angular/core';
import { priorityLabels } from '../../tasks/task-priority';
import { BoardStatisticsState } from './board-statistics-state';

@Component({
  selector: 'app-board-statistics',
  templateUrl: './board-statistics.html',
  styleUrl: './board-statistics.scss',
})
export class BoardStatisticsPanel {
  readonly statistics = inject(BoardStatisticsState);
  readonly priorityLabels = priorityLabels;
}
