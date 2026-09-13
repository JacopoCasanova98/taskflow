import { BoardStatisticsPanel } from '../statistics/board-statistics';
import { BoardStatisticsState } from '../statistics/board-statistics-state';
import { TaskLocalDay } from '../../tasks/task-local-day';
import { TaskDetails } from '../../tasks/task-details/task-details';
import { CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TaskView } from '../../tasks/task-view';
import { TaskViewControls } from '../../tasks/task-view-controls';
import { distinctUntilChanged, tap, map } from 'rxjs';
import { TaskList } from '../../tasks/task-list';
import { TaskManagement } from '../../tasks/task-management';
import { ColumnControls } from '../../columns/column-controls';
import { ColumnManagement } from '../../columns/column-management';
import { BoardWorkspaceState } from './board-workspace-state';

@Component({
  selector: 'app-board-workspace',
  imports: [
    BoardStatisticsPanel,
    RouterLink,
    ColumnControls,
    TaskList,
    CdkDropListGroup,
    TaskDetails,
    TaskViewControls,
  ],
  providers: [
    BoardStatisticsState,
    BoardWorkspaceState,
    ColumnManagement,
    TaskManagement,
    TaskView,
    TaskLocalDay,
  ],
  templateUrl: './board-workspace.html',
  styleUrls: ['../board-controls.scss', './board-workspace.scss'],
})
export class BoardWorkspace {
  readonly taskView = inject(TaskView);
  readonly tasks = inject(TaskManagement);
  readonly columns = inject(ColumnManagement);
  readonly state = inject(BoardWorkspaceState);

  constructor() {
    this.taskView.connect(this.state);
    this.state.connect(
      inject(ActivatedRoute).paramMap.pipe(
        map((params) => params.get('boardId')!),
        distinctUntilChanged(),
        tap(() => this.taskView.reset()),
      ),
    );
  }
}
