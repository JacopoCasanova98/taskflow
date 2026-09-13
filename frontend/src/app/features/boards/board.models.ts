export interface Board {
  readonly id: string;
  readonly name: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}
export interface CreateBoardRequest {
  name: string;
}
export interface RenameBoardRequest {
  name: string;
}
