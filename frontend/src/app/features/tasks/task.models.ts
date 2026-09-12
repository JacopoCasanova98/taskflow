export interface Task {
  readonly id: string;
  readonly columnId: string;
  readonly title: string;
  readonly description: string | null;
  readonly priority: 'LOW' | 'MEDIUM' | 'HIGH';
  readonly dueDate: string | null;
  readonly position: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}
