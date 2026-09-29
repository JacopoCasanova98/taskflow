import { Component, inject, linkedSignal } from '@angular/core';
import { priorityLabels } from '../../tasks/task-priority';
import { BoardStatisticsState, StatisticsState } from './board-statistics-state';
import { BoardStatistics } from './board-statistics.models';

@Component({
  selector: 'app-board-statistics',
  templateUrl: './board-statistics.html',
  styleUrl: './board-statistics.scss',
})
export class BoardStatisticsPanel {
  readonly statistics = inject(BoardStatisticsState);
  // Presentation-only snapshot: keep the overview in place during background refresh.
  // The panel is destroyed with its workspace, so snapshots cannot cross boards.
  readonly data = linkedSignal<StatisticsState, BoardStatistics | null>({
    source: () => this.statistics.state(),
    computation: (state, previous) =>
      state.status === 'ready' ? state.data : (previous?.value ?? null),
  });
  readonly priorityLabels = priorityLabels;
}
