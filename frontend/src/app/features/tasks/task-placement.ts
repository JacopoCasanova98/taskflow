import { WorkspaceColumn } from '../boards/board-workspace/board-workspace-state';
import { Task } from './task.models';

export interface TaskDropListData {
  readonly columnId: string;
  readonly tasks: readonly Task[];
}

/** Final drop indexes use the server's remove-then-insert semantics. */
export function planTaskPlacement(
  columns: readonly WorkspaceColumn[],
  sourceColumnId: string,
  targetColumnId: string,
  taskId: string,
  previousIndex: number,
  position: number,
) {
  const source = columns.find((lane) => lane.column.id === sourceColumnId);
  const target = columns.find((lane) => lane.column.id === targetColumnId);
  if (
    !source ||
    !target ||
    !Number.isInteger(previousIndex) ||
    !Number.isInteger(position) ||
    source.tasks[previousIndex]?.id !== taskId ||
    position < 0 ||
    position > target.tasks.length - (source === target ? 1 : 0) ||
    (source === target && previousIndex === position)
  )
    return null;

  const sourceTasks = [...source.tasks];
  const [moved] = sourceTasks.splice(previousIndex, 1);
  const targetTasks = source === target ? sourceTasks : [...target.tasks];
  targetTasks.splice(position, 0, { ...moved, columnId: targetColumnId });
  const resequence = (tasks: readonly Task[]) =>
    tasks.map((task, position) => ({ ...task, position }));
  const snapshot = source === target ? [source] : [source, target];
  return {
    taskId,
    request: { columnId: targetColumnId, position },
    snapshot,
    columns: columns.map((lane) =>
      lane === source
        ? { ...lane, tasks: resequence(sourceTasks) }
        : lane === target
          ? { ...lane, tasks: resequence(targetTasks) }
          : lane,
    ),
  };
}
