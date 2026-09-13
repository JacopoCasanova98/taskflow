import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { httpValidationFields, isHttpProblem } from '../../core/http/http-problem';
import { BoardWorkspaceState } from '../boards/board-workspace/board-workspace-state';
import { TaskView } from './task-view';
import { TaskApi } from './task-api';
import { CreateTaskRequest, Task, UpdateTaskRequest } from './task.models';
import { planTaskPlacement, TaskDropListData } from './task-placement';

export type TaskField = 'title' | 'description' | 'priority' | 'dueDate';
export type TaskMutationResult =
  | { success: true }
  | {
      success: false;
      error: string;
      stale?: boolean;
      fields?: Partial<Record<TaskField, string>>;
    };
const fieldMessages: Record<TaskField, string> = {
  title: 'Enter a non-blank title of no more than 200 characters.',
  description: 'Use no more than 4000 characters for the description.',
  priority: 'Choose LOW, MEDIUM, or HIGH.',
  dueDate: 'Enter a valid date in YYYY-MM-DD format.',
};

@Injectable()
export class TaskManagement {
  private readonly workspace = inject(BoardWorkspaceState);
  private readonly taskView = inject(TaskView);
  private readonly api = inject(TaskApi);
  private readonly feedback = signal<{
    generation: number;
    message: string;
    loadingOnly: boolean;
  } | null>(null);
  readonly busy = this.workspace.writing;
  readonly creatingIn = signal<string | null>(null);
  readonly selectedId = signal<string | null>(null);
  readonly selected = computed(() => this.find(this.selectedId()));
  readonly notice = computed(() => {
    const feedback = this.feedback();
    return feedback?.generation === this.workspace.generation() &&
      (!feedback.loadingOnly || this.workspace.workspace().status === 'loading')
      ? feedback.message
      : '';
  });

  constructor() {
    effect(() => {
      this.workspace.generation();
      this.creatingIn.set(null);
      this.selectedId.set(null);
    });
    effect(() => {
      const state = this.workspace.workspace();
      if (state.status !== 'ready') return;
      const creating = this.creatingIn();
      if (creating && !state.columns.some((lane) => lane.column.id === creating))
        this.creatingIn.set(null);
      const selected = this.selectedId();
      if (selected && !this.find(selected)) this.selectedId.set(null);
    });
  }

  find(id: string | null): Task | null {
    const state = this.workspace.workspace();
    if (state.status !== 'ready') return null;
    return state.columns.flatMap((lane) => lane.tasks).find((task) => task.id === id) ?? null;
  }

  create(columnId: string, request: CreateTaskRequest): Promise<TaskMutationResult> {
    const state = this.workspace.workspace();
    if (state.status !== 'ready') return Promise.resolve(this.stale());
    const lane = state.columns.find((lane) => lane.column.id === columnId);
    if (!lane) return Promise.resolve(this.stale());
    return this.mutate(
      'create',
      () => this.api.createTask(columnId, request),
      (task, generation) => {
        if (
          task.columnId !== columnId ||
          task.position !== lane.tasks.length ||
          this.find(task.id)
        ) {
          this.reconcile('Tasks changed on the server. Reloading the board.');
          return;
        }
        this.workspace.updateColumns(generation, (lanes) =>
          lanes.map((entry) =>
            entry.column.id === columnId ? { ...entry, tasks: [...entry.tasks, task] } : entry,
          ),
        );
      },
    );
  }

  update(id: string, request: UpdateTaskRequest): Promise<TaskMutationResult> {
    const original = this.find(id);
    if (!original) return Promise.resolve(this.stale());
    return this.mutate(
      'update',
      () => this.api.updateTask(id, request),
      (task, generation) => {
        if (
          task.id !== id ||
          task.columnId !== original.columnId ||
          task.position !== original.position
        ) {
          this.reconcile('Tasks changed on the server. Reloading the board.');
          return;
        }
        this.workspace.updateColumns(generation, (lanes) =>
          lanes.map((lane) =>
            lane.column.id === original.columnId
              ? { ...lane, tasks: lane.tasks.map((entry) => (entry.id === id ? task : entry)) }
              : lane,
          ),
        );
      },
    );
  }

  delete(id: string): Promise<TaskMutationResult> {
    const original = this.find(id);
    if (!original) return Promise.resolve(this.stale());
    return this.mutate(
      'delete',
      () => this.api.deleteTask(id),
      (_, generation) => {
        this.workspace.updateColumns(generation, (lanes) =>
          lanes.map((lane) =>
            lane.column.id === original.columnId
              ? {
                  ...lane,
                  tasks: lane.tasks
                    .filter((task) => task.id !== id)
                    .map((task, position) => ({ ...task, position })),
                }
              : lane,
          ),
        );
        if (this.selectedId() === id) this.selectedId.set(null);
      },
    );
  }

  async drop(event: CdkDragDrop<TaskDropListData, TaskDropListData, string>): Promise<void> {
    if (!this.taskView.manual()) return;
    if (event.previousContainer === event.container && event.previousIndex === event.currentIndex)
      return;
    const state = this.workspace.workspace();
    if (state.status !== 'ready' || this.busy()) return;
    // Only current canonical lane data can initiate a write, including after a reload.
    const source = state.columns.find(
      (lane) => lane.column.id === event.previousContainer.data.columnId,
    );
    const target = state.columns.find((lane) => lane.column.id === event.container.data.columnId);
    if (
      source?.tasks !== event.previousContainer.data.tasks ||
      target?.tasks !== event.container.data.tasks
    )
      return;
    const plan = planTaskPlacement(
      state.columns,
      event.previousContainer.data.columnId,
      event.container.data.columnId,
      event.item.data,
      event.previousIndex,
      event.currentIndex,
    );
    if (!plan) return;
    const pending = this.workspace.beginWrite();
    if (!pending) return;
    const { generation } = pending;
    const rollback = () =>
      this.workspace.updateColumns(generation, (columns) =>
        columns.map(
          (lane) => plan.snapshot.find((original) => original.column.id === lane.column.id) ?? lane,
        ),
      );
    this.feedback.set(null);
    this.workspace.updateColumns(generation, () => plan.columns);
    try {
      const task = await firstValueFrom(this.api.placeTask(plan.taskId, plan.request));
      if (generation !== this.workspace.generation()) return;
      if (
        !task ||
        task.id !== plan.taskId ||
        task.columnId !== plan.request.columnId ||
        task.position !== plan.request.position
      ) {
        rollback();
        this.reconcile('Tasks changed on the server. Reloading the board.');
        return;
      }
      this.workspace.updateColumns(generation, (columns) =>
        columns.map((lane) =>
          lane.column.id === task.columnId
            ? { ...lane, tasks: lane.tasks.map((entry) => (entry.id === task.id ? task : entry)) }
            : lane,
        ),
      );
      this.workspace.mutationConfirmed(generation);
    } catch (error: unknown) {
      if (generation !== this.workspace.generation()) return;
      rollback();
      if (isHttpProblem(error, 404, 'TASK_NOT_FOUND')) {
        this.reconcile('This task is no longer available.', false);
      } else if (
        isHttpProblem(error, 409, 'TASK_PLACEMENT_CONFLICT') ||
        isHttpProblem(error, 404, 'COLUMN_NOT_FOUND')
      ) {
        this.reconcile('Tasks changed on the server. Reloading the board.');
      } else {
        this.feedback.set({
          generation,
          loadingOnly: false,
          message: "We couldn't move the task. Please try again.",
        });
      }
    } finally {
      this.workspace.endWrite(pending);
    }
  }

  private async mutate<T>(
    operation: 'create' | 'update' | 'delete',
    request: () => Observable<T>,
    apply: (response: T, generation: number) => void,
  ): Promise<TaskMutationResult> {
    const pending = this.workspace.beginWrite();
    if (!pending) return this.stale();
    const { generation } = pending;
    this.feedback.set(null);
    try {
      const response = await firstValueFrom(request());
      if (generation !== this.workspace.generation()) return this.stale();
      apply(response, generation);
      this.workspace.mutationConfirmed(generation);
      if (generation === this.workspace.generation() && this.taskView.search.active())
        this.taskView.search.refresh();
      return generation === this.workspace.generation() ? { success: true } : this.stale();
    } catch (error: unknown) {
      if (generation !== this.workspace.generation()) return this.stale();
      if (isHttpProblem(error, 404, 'TASK_NOT_FOUND')) {
        this.reconcile('This task is no longer available.', false);
        return this.stale();
      }
      if (isHttpProblem(error, 404, 'COLUMN_NOT_FOUND')) {
        this.reconcile('Tasks changed on the server. Reloading the board.');
        return this.stale();
      }
      const violations = httpValidationFields(error);
      if (violations !== null && operation !== 'delete') {
        const fields: Partial<Record<TaskField, string>> = {};
        for (const field of violations) {
          if (Object.hasOwn(fieldMessages, field))
            fields[field as TaskField] = fieldMessages[field as TaskField];
        }
        return { success: false, error: 'Check the task fields and try again.', fields };
      }
      return { success: false, error: "We couldn't " + operation + ' the task. Please try again.' };
    } finally {
      this.workspace.endWrite(pending);
    }
  }

  private reconcile(message: string, loadingOnly = true): void {
    this.creatingIn.set(null);
    this.selectedId.set(null);
    this.workspace.retry();
    this.feedback.set({ generation: this.workspace.generation(), message, loadingOnly });
  }

  private stale(): TaskMutationResult {
    return { success: false, stale: true, error: '' };
  }
}
