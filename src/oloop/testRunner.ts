/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { parseTask, parseProject, serializeTask, serializeProject } from './parser';
import { Task, Project, TaskType } from './types';
import { LocalStorageAdapter } from './storage';

export interface TestCaseResult {
  id: string;
  name: string;
  category: 'valid' | 'invalid' | 'roundtrip' | 'storage_conflict' | 'backup';
  passed: boolean;
  message: string;
  details?: string;
  durationMs: number;
}

export interface TestSuiteSummary {
  total: number;
  passed: number;
  failed: number;
  results: TestCaseResult[];
}

export async function runOloopTestSuite(): Promise<TestSuiteSummary> {
  const results: TestCaseResult[] = [];

  // Helper runner
  const test = async (
    id: string,
    name: string,
    category: TestCaseResult['category'],
    fn: () => Promise<void> | void
  ) => {
    const start = performance.now();
    try {
      await fn();
      results.push({
        id,
        name,
        category,
        passed: true,
        message: 'Passed as expected',
        durationMs: Math.round(performance.now() - start),
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      results.push({
        id,
        name,
        category,
        passed: false,
        message: msg,
        durationMs: Math.round(performance.now() - start),
      });
    }
  };

  // --- Category 1: Valid Fixtures ---
  await test('valid-all-types', 'Valid Tasks: parse all 5 task types (todo, question, decision, feature, bug)', 'valid', () => {
    const types: TaskType[] = ['todo', 'question', 'decision', 'feature', 'bug'];
    for (const t of types) {
      const uuid = '11111111-2222-3333-4444-555555555555';
      const raw = `---
formatVersion: 1
id: "${uuid}"
projectId: "proj-1"
type: ${t}
title: "Testing ${t}"
status: open
labels:
  - "unit-test"
imagePath: null
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T12:00:00Z"
updatedBy: "Tester"
comments: []
---

Body of the ${t}.
`;
      const res = parseTask(raw, `${uuid}.md`, 'proj-1');
      if (!res.task) throw new Error(`Failed to parse valid ${t}: ${res.error}`);
      if (res.task.type !== t) throw new Error(`Expected type ${t}, got ${res.task.type}`);
      if (res.task.description !== `Body of the ${t}.`) throw new Error('Body mismatch');
      if (res.task.createdAt !== '2026-10-16T10:00:00Z') throw new Error('Timestamp was altered!');
    }
  });

  await test('valid-missing-format-version', 'Valid Task: missing formatVersion defaults to 1', 'valid', () => {
    const uuid = '22222222-2222-4222-8222-222222222222';
    const raw = `---
id: "${uuid}"
projectId: "proj-1"
type: todo
title: "Default format version"
status: open
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---

Body
`;
    const res = parseTask(raw, `${uuid}.md`, 'proj-1');
    if (!res.task) throw new Error(`Failed to parse task without formatVersion: ${res.error}`);
    if (res.task.formatVersion !== 1) throw new Error(`Expected formatVersion 1, got ${res.task.formatVersion}`);
  });

  await test('valid-comments-and-labels', 'Valid Task: parses comments list and trims labels correctly', 'valid', () => {
    const uuid = '33333333-3333-4333-8333-333333333333';
    const raw = `---
formatVersion: 1
id: "${uuid}"
projectId: "proj-1"
type: question
title: "Comments test"
status: closed
labels:
  - "  label1  "
  - "label2"
  - "label1"
createdAt: "2026-10-16T08:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T09:00:00Z"
updatedBy: "Tester"
comments:
  - body: "First note"
    createdAt: "2026-10-16T08:30:00Z"
  - body: "Second note"
    createdAt: "2026-10-16T09:00:00Z"
---
`;
    const res = parseTask(raw, `${uuid}.md`, 'proj-1');
    if (!res.task) throw new Error(`Parse failed: ${res.error}`);
    if (res.task.comments.length !== 2) throw new Error(`Expected 2 comments, got ${res.task.comments.length}`);
    if (res.task.labels.length !== 2 || !res.task.labels.includes('label1')) {
      throw new Error(`Labels deduplication/trim failed: ${JSON.stringify(res.task.labels)}`);
    }
  });

  await test('valid-project-md', 'Valid Project: parse project.md frontmatter and description', 'valid', () => {
    const raw = `---
formatVersion: 1
id: "proj-alpha"
name: "Alpha Project"
createdAt: "2026-10-01T00:00:00Z"
updatedAt: "2026-10-16T00:00:00Z"
---

Project Alpha overview and guidelines.
`;
    const res = parseProject(raw, 'alpha-folder');
    if (!res.project) throw new Error(`Parse project failed: ${res.error}`);
    if (res.project.id !== 'proj-alpha' || res.project.name !== 'Alpha Project') {
      throw new Error('Project fields mismatch');
    }
  });

  // --- Category 2: Round-Trip Integrity ---
  await test('roundtrip-task', 'Round-trip: reading and writing back unchanged preserves all values and string timestamps', 'roundtrip', () => {
    const original: Task = {
      formatVersion: 1,
      id: '44444444-4444-4444-8444-444444444444',
      projectId: 'proj-roundtrip',
      type: 'feature',
      title: 'Roundtrip Test Task',
      status: 'open',
      labels: ['roundtrip', 'sync'],
      imagePath: 'screenshots/hero.png',
      createdAt: '2026-10-16T12:34:56Z',
      createdBy: 'Actor A',
      updatedAt: '2026-10-16T13:45:00Z',
      updatedBy: 'Actor B',
      comments: [
        {
          body: 'Automated comment during roundtrip',
          createdAt: '2026-10-16T13:00:00Z',
        },
      ],
      description: '# Header\n\nParagraph text with *emphasis* and `code`.\n- Item 1\n- Item 2',
    };

    const serialized = serializeTask(original);
    const parsed = parseTask(serialized, `${original.id}.md`, original.projectId);

    if (!parsed.task) {
      throw new Error(`Roundtrip re-parse failed: ${parsed.error}`);
    }

    const t = parsed.task;
    if (t.id !== original.id) throw new Error('ID mismatch in roundtrip');
    if (t.projectId !== original.projectId) throw new Error('projectId mismatch');
    if (t.type !== original.type) throw new Error('type mismatch');
    if (t.title !== original.title) throw new Error('title mismatch');
    if (t.status !== original.status) throw new Error('status mismatch');
    if (t.createdAt !== original.createdAt) throw new Error(`createdAt modified: ${t.createdAt} vs ${original.createdAt}`);
    if (t.updatedAt !== original.updatedAt) throw new Error(`updatedAt modified: ${t.updatedAt} vs ${original.updatedAt}`);
    if (t.createdBy !== original.createdBy) throw new Error('createdBy mismatch');
    if (t.updatedBy !== original.updatedBy) throw new Error('updatedBy mismatch');
    if (t.imagePath !== original.imagePath) throw new Error('imagePath mismatch');
    if (JSON.stringify(t.labels) !== JSON.stringify(original.labels)) throw new Error('labels mismatch');
    if (t.comments.length !== 1 || t.comments[0].body !== original.comments[0].body) {
      throw new Error('comments mismatch');
    }
    if (t.description !== original.description) throw new Error(`description mismatch: "${t.description}" vs "${original.description}"`);
  });

  // --- Category 3: Invalid Fixtures with Stated Reasons ---
  await test('invalid-future-format-version', 'Invalid: refuse higher formatVersion (e.g. 2)', 'invalid', () => {
    const raw = `---
formatVersion: 2
id: "55555555-5555-4555-8555-555555555555"
projectId: "proj-1"
type: todo
title: "Future format"
status: open
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, '55555555-5555-4555-8555-555555555555.md', 'proj-1');
    if (res.task) throw new Error('Should have rejected formatVersion: 2');
    if (!res.error?.includes('Unsupported formatVersion')) {
      throw new Error(`Expected unsupported formatVersion error, got: ${res.error}`);
    }
  });

  await test('invalid-filename-id-mismatch', 'Invalid: refuse file when filename does not match id', 'invalid', () => {
    const raw = `---
formatVersion: 1
id: "uuid-inside-file"
projectId: "proj-1"
type: todo
title: "Filename mismatch"
status: open
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, 'different-name.md', 'proj-1');
    if (res.task) throw new Error('Should have rejected filename mismatch');
    if (!res.error?.includes('File name mismatch')) {
      throw new Error(`Expected filename mismatch error, got: ${res.error}`);
    }
  });

  await test('invalid-project-id-mismatch', 'Invalid: refuse task whose projectId does not match folder project.md', 'invalid', () => {
    const raw = `---
formatVersion: 1
id: "66666666-6666-4666-8666-666666666666"
projectId: "proj-foreign"
type: todo
title: "Foreign task"
status: open
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, '66666666-6666-4666-8666-666666666666.md', 'proj-current');
    if (res.task) throw new Error('Should have rejected projectId mismatch');
    if (!res.error?.includes('Project ID mismatch')) {
      throw new Error(`Expected Project ID mismatch error, got: ${res.error}`);
    }
  });

  await test('invalid-unknown-frontmatter-key', 'Invalid: refuse task with unknown frontmatter key', 'invalid', () => {
    const raw = `---
formatVersion: 1
id: "77777777-7777-4777-8777-777777777777"
projectId: "proj-1"
type: todo
title: "Unknown key test"
status: open
priority: "high"
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, '77777777-7777-4777-8777-777777777777.md', 'proj-1');
    if (res.task) throw new Error('Should have rejected unknown frontmatter key');
    if (!res.error?.includes("Unknown frontmatter key: 'priority'")) {
      throw new Error(`Expected unknown key error, got: ${res.error}`);
    }
  });

  await test('invalid-task-type', 'Invalid: refuse task with unrecognized type (e.g. "chore")', 'invalid', () => {
    const raw = `---
formatVersion: 1
id: "88888888-8888-4888-8888-888888888888"
projectId: "proj-1"
type: chore
title: "Invalid type"
status: open
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, '88888888-8888-4888-8888-888888888888.md', 'proj-1');
    if (res.task) throw new Error('Should have rejected invalid task type');
    if (!res.error?.includes('Invalid task type')) {
      throw new Error(`Expected invalid task type error, got: ${res.error}`);
    }
  });

  await test('invalid-task-status', 'Invalid: refuse task with invalid status (e.g. "in-progress")', 'invalid', () => {
    const raw = `---
formatVersion: 1
id: "99999999-9999-4999-8999-999999999999"
projectId: "proj-1"
type: bug
title: "Invalid status"
status: in-progress
createdAt: "2026-10-16T10:00:00Z"
createdBy: "Tester"
updatedAt: "2026-10-16T10:00:00Z"
updatedBy: "Tester"
---
`;
    const res = parseTask(raw, '99999999-9999-4999-8999-999999999999.md', 'proj-1');
    if (res.task) throw new Error('Should have rejected invalid status');
    if (!res.error?.includes('Invalid status')) {
      throw new Error(`Expected invalid status error, got: ${res.error}`);
    }
  });

  // --- Category 4: Storage, Backup & Conflict Rules ---
  await test('storage-backup-creation', 'Backup: overwriting an existing file creates a backup in <project>/.backup/<id>.md.bak', 'backup', async () => {
    const adapter = new LocalStorageAdapter();
    const taskId = '00000000-0000-4000-a000-000000000001';
    const folder = 'mobile-app';

    // Original content before modification
    const originalRaw = await adapter.getTaskRaw(folder, taskId);
    if (!originalRaw) throw new Error('Original task not found in test adapter');

    const originalParsed = parseTask(originalRaw, `${taskId}.md`, 'proj-mobile-client');
    if (!originalParsed.task) throw new Error('Original task parse error');

    // Modify task
    const modifiedTask: Task = {
      ...originalParsed.task,
      title: 'Updated title to trigger backup',
      updatedAt: '2026-10-16T14:00:00Z',
      updatedBy: 'Test Runner',
    };

    const saveRes = await adapter.saveTask(folder, modifiedTask, originalParsed.task.updatedAt);
    if (!saveRes.success) throw new Error(`Save failed: ${saveRes.error}`);

    // Check that backup file was created
    const backupContent = await adapter.getBackupContent(folder, taskId);
    if (!backupContent) throw new Error('Backup file .backup/<id>.md.bak was not created!');
    if (!backupContent.includes('Implement SAF folder permission persistence')) {
      throw new Error('Backup does not contain original content!');
    }
  });

  await test('storage-conflict-detection', 'Conflict Detection: re-reading file before write detects modified updatedAt on disk', 'storage_conflict', async () => {
    const adapter = new LocalStorageAdapter();
    const taskId = '00000000-0000-4000-a000-000000000002';
    const folder = 'mobile-app';

    // User A reads task at T1
    const raw = await adapter.getTaskRaw(folder, taskId);
    const parsed = parseTask(raw!, `${taskId}.md`, 'proj-mobile-client');
    const userALoadedUpdatedAt = parsed.task!.updatedAt;

    // Simulate concurrent User B (or CLI) modifying file on disk first
    const diskTask: Task = {
      ...parsed.task!,
      title: 'Modified by CLI concurrently',
      updatedAt: '2026-10-16T15:30:00Z',
      updatedBy: 'CLI Tool',
    };
    await adapter.saveTask(folder, diskTask, userALoadedUpdatedAt, true);

    // Now User A tries to save with outdated lastKnownUpdatedAt
    const userAChanges: Task = {
      ...parsed.task!,
      title: 'User A edits concurrently',
      updatedAt: '2026-10-16T15:35:00Z',
      updatedBy: 'User A',
    };

    const attemptRes = await adapter.saveTask(folder, userAChanges, userALoadedUpdatedAt);
    if (attemptRes.success) {
      throw new Error('Save should have been blocked due to concurrent conflict!');
    }
    if (!attemptRes.conflict) {
      throw new Error('Expected conflict info in response');
    }
    if (attemptRes.conflict.currentDiskUpdatedAt !== '2026-10-16T15:30:00Z') {
      throw new Error('Conflict info did not capture current disk updatedAt');
    }
  });

  const passed = results.filter((r) => r.passed).length;
  const failed = results.filter((r) => !r.passed).length;

  return {
    total: results.length,
    passed,
    failed,
    results,
  };
}
