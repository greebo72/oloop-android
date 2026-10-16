/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export type TaskType = 'todo' | 'question' | 'decision' | 'feature' | 'bug';

export type TaskStatus = 'open' | 'closed';

export interface Comment {
  body: string;
  createdAt: string; // ISO 8601 UTC string (e.g., "2026-10-16T12:00:00Z")
}

export interface Task {
  formatVersion: number; // integer, currently 1
  id: string; // UUID4
  projectId: string;
  type: TaskType;
  title: string;
  status: TaskStatus;
  labels: string[];
  imagePath: string | null;
  createdAt: string; // ISO 8601 UTC string
  createdBy: string;
  updatedAt: string; // ISO 8601 UTC string
  updatedBy: string;
  comments: Comment[];
  description: string; // Markdown body
}

export interface Project {
  formatVersion: number;
  id: string;
  name: string;
  folderName: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
  tasksCount?: number;
  openTasksCount?: number;
  latestTaskUpdatedAt?: string;
}

export type GroupingMode = 'none' | 'created' | 'modified' | 'type' | 'status';

export interface UnreadableFile {
  fileName: string;
  folderName: string;
  reason: string;
  rawContent?: string;
}

export interface ConflictInfo {
  taskId: string;
  projectId: string;
  currentDiskUpdatedAt: string;
  loadedUpdatedAt: string;
  diskTask: Task;
  memoryTask: Task;
}

export const TASK_TYPES: TaskType[] = ['todo', 'question', 'decision', 'feature', 'bug'];

export const CLOSED_STATUS_LABELS: Record<TaskType, string> = {
  todo: 'Done',
  question: 'Answered',
  decision: 'Decided',
  feature: 'Implemented',
  bug: 'Fixed',
};

export const TYPE_BADGE_COLORS: Record<TaskType, { bg: string; text: string; border: string }> = {
  todo: { bg: 'bg-emerald-50 dark:bg-emerald-950/40', text: 'text-emerald-700 dark:text-emerald-300', border: 'border-emerald-200 dark:border-emerald-800' },
  question: { bg: 'bg-amber-50 dark:bg-amber-950/40', text: 'text-amber-700 dark:text-amber-300', border: 'border-amber-200 dark:border-amber-800' },
  decision: { bg: 'bg-purple-50 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300', border: 'border-purple-200 dark:border-purple-800' },
  feature: { bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-700 dark:text-blue-300', border: 'border-blue-200 dark:border-blue-800' },
  bug: { bg: 'bg-rose-50 dark:bg-rose-950/40', text: 'text-rose-700 dark:text-rose-300', border: 'border-rose-200 dark:border-rose-800' },
};
