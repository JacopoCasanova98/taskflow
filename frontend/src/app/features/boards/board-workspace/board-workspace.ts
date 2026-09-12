import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { TaskList } from '../../tasks/task-list';
import { TaskManagement } from '../../tasks/task-management';
import { ColumnControls } from '../../columns/column-controls';
import { ColumnManagement } from '../../columns/column-management';
import { BoardWorkspaceState } from './board-workspace-state';

@Component({
  selector: 'app-board-workspace',
  imports: [RouterLink, ColumnControls, TaskList],
  providers: [BoardWorkspaceState, ColumnManagement, TaskManagement],
  templateUrl: './board-workspace.html',
  styleUrls: ['../board-controls.scss', './board-workspace.scss'],
})
export class BoardWorkspace {
  readonly tasks = inject(TaskManagement);
  readonly columns = inject(ColumnManagement);
  readonly state = inject(BoardWorkspaceState);

  constructor() {
    this.state.connect(
      inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('boardId')!)),
    );
  }
}
