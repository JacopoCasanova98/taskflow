export interface CreateColumnRequest {
  name: string;
}
export interface RenameColumnRequest {
  name: string;
}
export interface ReorderColumnsRequest {
  columnIds: readonly string[];
}

export interface Column {
  readonly id: string;
  readonly name: string;
  readonly position: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}
