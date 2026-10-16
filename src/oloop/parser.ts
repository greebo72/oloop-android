/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import * as yaml from 'js-yaml';
import { Project, Task, TaskType, TaskStatus, Comment, TASK_TYPES } from './types';

export const CURRENT_FORMAT_VERSION = 1;

export const KNOWN_TASK_KEYS = new Set([
  'formatVersion',
  'id',
  'projectId',
  'type',
  'title',
  'status',
  'labels',
  'imagePath',
  'createdAt',
  'createdBy',
  'updatedAt',
  'updatedBy',
  'comments',
]);

export const KNOWN_PROJECT_KEYS = new Set([
  'formatVersion',
  'id',
  'name',
  'createdAt',
  'updatedAt',
]);

export interface ParseTaskResult {
  task?: Task;
  error?: string;
}

export interface ParseProjectResult {
  project?: Project;
  error?: string;
}

/**
 * Split markdown file into YAML frontmatter string and body.
 */
export function splitFrontmatter(content: string): { frontmatter: string; body: string } {
  // Normalize Windows CRLF to LF
  const normalized = content.replace(/\r\n/g, '\n');
  const match = normalized.match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/);
  if (!match) {
    throw new Error('Missing or malformed YAML frontmatter (expected "---" delimiters)');
  }
  return {
    frontmatter: match[1],
    body: match[2].trim(),
  };
}

/**
 * Parse YAML string while ensuring timestamps stay as strings (never Date objects).
 * js-yaml JSON_SCHEMA does not convert ISO timestamps to Date objects.
 */
function safeLoadYaml(yamlStr: string): Record<string, unknown> {
  const loaded = yaml.load(yamlStr, {
    schema: yaml.JSON_SCHEMA,
    json: true,
  });

  if (!loaded || typeof loaded !== 'object' || Array.isArray(loaded)) {
    throw new Error('Frontmatter must be a YAML mapping (key-value dictionary)');
  }
  return loaded as Record<string, unknown>;
}

/**
 * Parse a project.md file content.
 */
export function parseProject(content: string, folderName: string): ParseProjectResult {
  try {
    const { frontmatter, body } = splitFrontmatter(content);
    const data = safeLoadYaml(frontmatter);

    // Check unknown keys
    for (const key of Object.keys(data)) {
      if (!KNOWN_PROJECT_KEYS.has(key)) {
        return { error: `Unknown frontmatter key in project.md: '${key}'` };
      }
    }

    // formatVersion check
    const formatVersion = data.formatVersion === undefined ? 1 : Number(data.formatVersion);
    if (!Number.isInteger(formatVersion) || formatVersion < 1) {
      return { error: `Invalid formatVersion: expected positive integer, got ${data.formatVersion}` };
    }
    if (formatVersion > CURRENT_FORMAT_VERSION) {
      return {
        error: `Unsupported formatVersion ${formatVersion} in project.md (app supports up to ${CURRENT_FORMAT_VERSION})`,
      };
    }

    if (!data.id || typeof data.id !== 'string') {
      return { error: 'project.md missing required string field: "id"' };
    }
    if (!data.name || typeof data.name !== 'string') {
      return { error: 'project.md missing required string field: "name"' };
    }

    return {
      project: {
        formatVersion,
        id: data.id,
        name: data.name,
        folderName,
        description: body,
        createdAt: typeof data.createdAt === 'string' ? data.createdAt : undefined,
        updatedAt: typeof data.updatedAt === 'string' ? data.updatedAt : undefined,
      },
    };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return { error: `Failed to parse project.md: ${msg}` };
  }
}

/**
 * Parse a task file (<task-id>.md).
 * Validates filename match, projectId match (if expectedProjectId provided), types, and schema rules.
 */
export function parseTask(
  content: string,
  fileName: string,
  expectedProjectId?: string
): ParseTaskResult {
  try {
    const { frontmatter, body } = splitFrontmatter(content);
    const data = safeLoadYaml(frontmatter);

    // 1. Check for unknown keys
    for (const key of Object.keys(data)) {
      if (!KNOWN_TASK_KEYS.has(key)) {
        return { error: `Unknown frontmatter key: '${key}'` };
      }
    }

    // 2. formatVersion check
    const formatVersion = data.formatVersion === undefined ? 1 : Number(data.formatVersion);
    if (!Number.isInteger(formatVersion) || formatVersion < 1) {
      return { error: `Invalid formatVersion: expected positive integer, got ${data.formatVersion}` };
    }
    if (formatVersion > CURRENT_FORMAT_VERSION) {
      return {
        error: `Unsupported formatVersion ${formatVersion} (app supports up to ${CURRENT_FORMAT_VERSION})`,
      };
    }

    // 3. ID and file name match
    if (!data.id || typeof data.id !== 'string') {
      return { error: 'Missing required string field: "id"' };
    }
    const expectedFileName = `${data.id}.md`;
    if (fileName !== expectedFileName) {
      return {
        error: `File name mismatch: file is "${fileName}", but id is "${data.id}" (expected "${expectedFileName}")`,
      };
    }

    // 4. Project ID match
    if (!data.projectId || typeof data.projectId !== 'string') {
      return { error: 'Missing required string field: "projectId"' };
    }
    if (expectedProjectId && data.projectId !== expectedProjectId) {
      return {
        error: `Project ID mismatch: task has projectId "${data.projectId}", but parent project is "${expectedProjectId}"`,
      };
    }

    // 5. Task Type validation
    if (!data.type || typeof data.type !== 'string' || !TASK_TYPES.includes(data.type as TaskType)) {
      return {
        error: `Invalid task type "${data.type}". Expected one of: ${TASK_TYPES.join(', ')}`,
      };
    }

    // 6. Title validation
    if (typeof data.title !== 'string' || data.title.trim().length === 0) {
      return { error: 'Missing or empty required string field: "title"' };
    }

    // 7. Status validation
    if (data.status !== 'open' && data.status !== 'closed') {
      return {
        error: `Invalid status "${data.status}". Expected "open" or "closed"`,
      };
    }

    // 8. Labels validation
    let labels: string[] = [];
    if (data.labels !== undefined) {
      if (!Array.isArray(data.labels)) {
        return { error: 'Field "labels" must be an array of strings' };
      }
      labels = (data.labels as unknown[])
        .map((l) => (typeof l === 'string' ? l.trim() : ''))
        .filter((l) => l.length > 0);
      // Remove duplicates
      labels = Array.from(new Set(labels));
    }

    // 9. imagePath validation
    let imagePath: string | null = null;
    if (data.imagePath !== undefined && data.imagePath !== null) {
      if (typeof data.imagePath !== 'string') {
        return { error: 'Field "imagePath" must be a string or null' };
      }
      imagePath = data.imagePath;
    }

    // 10. Timestamps validation (must be strings)
    if (!data.createdAt || typeof data.createdAt !== 'string') {
      return { error: 'Missing or invalid required timestamp string field: "createdAt"' };
    }
    if (!data.createdBy || typeof data.createdBy !== 'string') {
      return { error: 'Missing required string field: "createdBy"' };
    }
    if (!data.updatedAt || typeof data.updatedAt !== 'string') {
      return { error: 'Missing or invalid required timestamp string field: "updatedAt"' };
    }
    if (!data.updatedBy || typeof data.updatedBy !== 'string') {
      return { error: 'Missing required string field: "updatedBy"' };
    }

    // 11. Comments validation
    const comments: Comment[] = [];
    if (data.comments !== undefined) {
      if (!Array.isArray(data.comments)) {
        return { error: 'Field "comments" must be an array' };
      }
      for (let i = 0; i < data.comments.length; i++) {
        const c = data.comments[i];
        if (!c || typeof c !== 'object') {
          return { error: `Invalid comment at index ${i}: must be an object` };
        }
        const commentObj = c as Record<string, unknown>;
        if (typeof commentObj.body !== 'string' || commentObj.body.trim().length === 0) {
          return { error: `Comment at index ${i} has missing or empty "body"` };
        }
        if (typeof commentObj.createdAt !== 'string') {
          return { error: `Comment at index ${i} has missing or invalid "createdAt"` };
        }
        comments.push({
          body: commentObj.body,
          createdAt: commentObj.createdAt,
        });
      }
    }

    return {
      task: {
        formatVersion,
        id: data.id,
        projectId: data.projectId,
        type: data.type as TaskType,
        title: data.title,
        status: data.status as TaskStatus,
        labels,
        imagePath,
        createdAt: data.createdAt,
        createdBy: data.createdBy,
        updatedAt: data.updatedAt,
        updatedBy: data.updatedBy,
        comments,
        description: body,
      },
    };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return { error: `Failed to parse task file: ${msg}` };
  }
}

/**
 * Format a Task into the exact YAML frontmatter + Markdown body layout.
 * Strictly adheres to key order:
 * formatVersion: 1
 * id: ...
 * projectId: ...
 * type: ...
 * title: ...
 * status: ...
 * labels:
 * imagePath: ...
 * createdAt: ...
 * createdBy: ...
 * updatedAt: ...
 * updatedBy: ...
 * comments:
 */
export function serializeTask(task: Task): string {
  const lines: string[] = ['---'];

  lines.push(`formatVersion: ${task.formatVersion || 1}`);
  lines.push(`id: "${task.id}"`);
  lines.push(`projectId: "${task.projectId}"`);
  lines.push(`type: ${task.type}`);
  lines.push(`title: ${JSON.stringify(task.title)}`);
  lines.push(`status: ${task.status}`);

  // labels
  if (!task.labels || task.labels.length === 0) {
    lines.push('labels: []');
  } else {
    lines.push('labels:');
    for (const label of task.labels) {
      lines.push(`  - ${JSON.stringify(label)}`);
    }
  }

  // imagePath
  if (task.imagePath === null || task.imagePath === undefined) {
    lines.push('imagePath: null');
  } else {
    lines.push(`imagePath: ${JSON.stringify(task.imagePath)}`);
  }

  // Timestamps & actors
  lines.push(`createdAt: "${task.createdAt}"`);
  lines.push(`createdBy: ${JSON.stringify(task.createdBy)}`);
  lines.push(`updatedAt: "${task.updatedAt}"`);
  lines.push(`updatedBy: ${JSON.stringify(task.updatedBy)}`);

  // comments
  if (!task.comments || task.comments.length === 0) {
    lines.push('comments: []');
  } else {
    lines.push('comments:');
    for (const comment of task.comments) {
      lines.push(`  - body: ${JSON.stringify(comment.body)}`);
      lines.push(`    createdAt: "${comment.createdAt}"`);
    }
  }

  lines.push('---');

  if (task.description && task.description.trim().length > 0) {
    lines.push('');
    lines.push(task.description.trim());
  }
  lines.push('');

  return lines.join('\n');
}

/**
 * Format a Project into YAML frontmatter + Markdown body.
 */
export function serializeProject(project: Project): string {
  const lines: string[] = ['---'];
  lines.push(`formatVersion: ${project.formatVersion || 1}`);
  lines.push(`id: "${project.id}"`);
  lines.push(`name: ${JSON.stringify(project.name)}`);
  if (project.createdAt) {
    lines.push(`createdAt: "${project.createdAt}"`);
  }
  if (project.updatedAt) {
    lines.push(`updatedAt: "${project.updatedAt}"`);
  }
  lines.push('---');

  if (project.description && project.description.trim().length > 0) {
    lines.push('');
    lines.push(project.description.trim());
  }
  lines.push('');

  return lines.join('\n');
}

/**
 * Helper to generate current UTC ISO string: e.g. "2026-10-16T12:00:00Z"
 */
export function nowUtcIso(): string {
  const now = new Date();
  return now.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

/**
 * Helper to generate standard UUID v4
 */
export function generateUuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
