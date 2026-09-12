import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { BoardWorkspaceState } from './board-workspace-state';

@Component({
  selector: 'app-board-workspace',
  imports: [RouterLink],
  providers: [BoardWorkspaceState],
  templateUrl: './board-workspace.html',
  styleUrls: ['../board-controls.scss', './board-workspace.scss'],
})
export class BoardWorkspace {
  readonly state = inject(BoardWorkspaceState);

  constructor() {
    this.state.connect(
      inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('boardId')!)),
    );
  }
}
