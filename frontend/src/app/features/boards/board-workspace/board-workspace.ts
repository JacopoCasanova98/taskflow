import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { ColumnControls } from '../../columns/column-controls';
import { ColumnManagement } from '../../columns/column-management';
import { BoardWorkspaceState } from './board-workspace-state';

@Component({
  selector: 'app-board-workspace',
  imports: [RouterLink, ColumnControls],
  providers: [BoardWorkspaceState, ColumnManagement],
  templateUrl: './board-workspace.html',
  styleUrls: ['../board-controls.scss', './board-workspace.scss'],
})
export class BoardWorkspace {
  readonly columns = inject(ColumnManagement);
  readonly state = inject(BoardWorkspaceState);

  constructor() {
    this.state.connect(
      inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('boardId')!)),
    );
  }
}
