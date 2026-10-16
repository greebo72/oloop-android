/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { Project, Task, UnreadableFile, ConflictInfo } from './types';
import { parseProject, parseTask, serializeTask, serializeProject, nowUtcIso } from './parser';

export interface StorageAdapter {
  isFileSystemAccessSupported(): boolean;
  selectDirectory(): Promise<string>;
  getRootName(): string;
  loadProjects(): Promise<{ projects: Project[]; unreadable: UnreadableFile[] }>;
  loadTasks(projectId: string, projectFolderName: string): Promise<{ tasks: Task[]; unreadable: UnreadableFile[] }>;
  getTaskRaw(projectFolderName: string, taskId: string): Promise<string | null>;
  saveTask(
    projectFolderName: string,
    task: Task,
    lastKnownUpdatedAt?: string,
    forceOverwrite?: boolean
  ): Promise<{ success: boolean; conflict?: ConflictInfo; error?: string }>;
  saveProject(project: Project): Promise<{ success: boolean; error?: string }>;
  getBackupContent(projectFolderName: string, taskId: string): Promise<string | null>;
}

// Default sample demo workspace with all 5 types of tasks across 2 projects
export const INITIAL_FIXTURE_FILES: Record<string, string> = {
  'mobile-app/project.md': `---
formatVersion: 1
id: "proj-mobile-client"
name: "Mobile Client (Android)"
createdAt: "2026-10-10T09:00:00Z"
updatedAt: "2026-10-16T11:00:00Z"
---

Native Android client app for oloop cross-project task tracking.
`,
  'mobile-app/00000000-0000-4000-a000-000000000001.md': `---
formatVersion: 1
id: "00000000-0000-4000-a000-000000000001"
projectId: "proj-mobile-client"
type: todo
title: "Implement SAF folder permission persistence"
status: open
labels:
  - "storage"
  - "saf"
imagePath: null
createdAt: "2026-10-14T08:30:00Z"
createdBy: "Sample User"
updatedAt: "2026-10-16T10:15:00Z"
updatedBy: "Sample User"
comments:
  - body: "Remember to call takePersistableUriPermission right after ACTION_OPEN_DOCUMENT_TREE."
    createdAt: "2026-10-14T09:00:00Z"
---

The Storage Access Framework requires calling \`contentResolver.takePersistableUriPermission(uri, flags)\`
so the folder remains accessible after phone reboot.
`,
  'mobile-app/00000000-0000-4000-a000-000000000002.md': `---
formatVersion: 1
id: "00000000-0000-4000-a000-000000000002"
projectId: "proj-mobile-client"
type: question
title: "Should we support dark mode theme switching?"
status: open
labels:
  - "ux"
  - "theme"
imagePath: null
createdAt: "2026-10-15T14:00:00Z"
createdBy: "Alex Developer"
updatedAt: "2026-10-15T14:30:00Z"
updatedBy: "Alex Developer"
comments:
  - body: "Material 3 automatically derives dynamic color schemes based on system settings."
    createdAt: "2026-10-15T14:30:00Z"
---

Check if we follow system theme by default or offer an in-app toggle in Settings.
`,
  'mobile-app/00000000-0000-4000-a000-000000000003.md': `---
formatVersion: 1
id: "00000000-0000-4000-a000-000000000003"
projectId: "proj-mobile-client"
type: decision
title: "Use SnakeYAML for Android Kotlin YAML parsing"
status: closed
labels:
  - "architecture"
imagePath: null
createdAt: "2026-10-12T10:00:00Z"
createdBy: "Sample User"
updatedAt: "2026-10-13T16:00:00Z"
updatedBy: "Sample User"
comments:
  - body: "SnakeYAML configured with custom Constructor prevents ISO dates from turning into java.util.Date objects."
    createdAt: "2026-10-13T16:00:00Z"
---

SnakeYAML is battle-tested, lightweight and lets us configure scalar resolvers to treat timestamps strictly as strings.
`,
  'mobile-app/00000000-0000-4000-a000-000000000004.md': `---
formatVersion: 1
id: "00000000-0000-4000-a000-000000000004"
projectId: "proj-mobile-client"
type: feature
title: "Split view navigation for tablet and landscape screens"
status: open
labels:
  - "ui"
  - "tablet"
imagePath: null
createdAt: "2026-10-16T08:00:00Z"
createdBy: "Sample User"
updatedAt: "2026-10-16T12:00:00Z"
updatedBy: "Sample User"
comments: []
---

On wide screens (>= 768px), show the project list / task list side-by-side with the active task detail view.
`,
  'mobile-app/00000000-0000-4000-a000-000000000005.md': `---
formatVersion: 1
id: "00000000-0000-4000-a000-000000000005"
projectId: "proj-mobile-client"
type: bug
title: "YAML parser converts timestamp to Date object"
status: closed
labels:
  - "bug"
  - "yaml"
imagePath: null
createdAt: "2026-10-11T12:00:00Z"
createdBy: "Sarah QA"
updatedAt: "2026-10-12T09:20:00Z"
updatedBy: "Sample User"
comments:
  - body: "Resolved by forcing JSON_SCHEMA string scalar preservation in parser."
    createdAt: "2026-10-12T09:20:00Z"
---

Fixed: timestamps now strictly remain ISO strings \`YYYY-MM-DDTHH:MM:SSZ\` across read/write cycles.
`,
  'cli-tool/project.md': `---
formatVersion: 1
id: "proj-cli-engine"
name: "oloop Desktop CLI"
createdAt: "2026-09-01T12:00:00Z"
updatedAt: "2026-10-15T18:00:00Z"
---

The core command-line tool maintaining the exact same markdown & yaml frontmatter repository.
`,
  'cli-tool/11111111-1111-4111-b111-111111111111.md': `---
formatVersion: 1
id: "11111111-1111-4111-b111-111111111111"
projectId: "proj-cli-engine"
type: feature
title: "Filter by label in CLI summary table"
status: open
labels:
  - "cli"
  - "filtering"
imagePath: null
createdAt: "2026-10-01T10:00:00Z"
createdBy: "Core Dev"
updatedAt: "2026-10-14T11:45:00Z"
updatedBy: "Core Dev"
comments: []
---

Enable \`oloop list --label=backend\` to narrow output to matching tasks.
`,
};

/**
 * In-memory / LocalStorage storage adapter.
 * Stores files with exact paths like `mobile-app/project.md` and supports `.backup/` files.
 */
export class LocalStorageAdapter implements StorageAdapter {
  private files: Map<string, string> = new Map();
  private rootName: string = 'Local Demo Workspace (/sdcard/oloop)';

  constructor() {
    this.init();
  }

  private init() {
    const saved = localStorage.getItem('oloop_virtual_fs');
    if (saved) {
      try {
        const obj = JSON.parse(saved);
        for (const [k, v] of Object.entries(obj)) {
          this.files.set(k, v as string);
        }
      } catch {
        this.resetToFixtures();
      }
    } else {
      this.resetToFixtures();
    }
  }

  public resetToFixtures() {
    this.files.clear();
    for (const [k, v] of Object.entries(INITIAL_FIXTURE_FILES)) {
      this.files.set(k, v);
    }
    this.persist();
  }

  private persist() {
    const obj: Record<string, string> = {};
    for (const [k, v] of this.files.entries()) {
      obj[k] = v;
    }
    localStorage.setItem('oloop_virtual_fs', JSON.stringify(obj));
  }

  isFileSystemAccessSupported(): boolean {
    return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
  }

  async selectDirectory(): Promise<string> {
    return this.rootName;
  }

  getRootName(): string {
    return this.rootName;
  }

  setRootName(name: string) {
    this.rootName = name;
  }

  async loadProjects(): Promise<{ projects: Project[]; unreadable: UnreadableFile[] }> {
    const projectFolders = new Set<string>();
    for (const path of this.files.keys()) {
      const parts = path.split('/');
      if (parts.length >= 2) {
        projectFolders.add(parts[0]);
      }
    }

    const projects: Project[] = [];
    const unreadable: UnreadableFile[] = [];

    for (const folder of Array.from(projectFolders)) {
      const projectFilePath = `${folder}/project.md`;
      const content = this.files.get(projectFilePath);
      if (!content) {
        // Direct subfolder without project.md is ignored according to spec
        continue;
      }

      const res = parseProject(content, folder);
      if (res.project) {
        // Compute task counts
        const { tasks } = await this.loadTasks(res.project.id, folder);
        res.project.tasksCount = tasks.length;
        res.project.openTasksCount = tasks.filter((t) => t.status === 'open').length;

        // Most recent updatedAt across its tasks
        let latest = res.project.updatedAt || res.project.createdAt || '';
        for (const t of tasks) {
          if (!latest || t.updatedAt > latest) {
            latest = t.updatedAt;
          }
        }
        res.project.latestTaskUpdatedAt = latest;

        projects.push(res.project);
      } else {
        unreadable.push({
          fileName: 'project.md',
          folderName: folder,
          reason: res.error || 'Failed to parse project.md',
          rawContent: content,
        });
      }
    }

    // Sort projects by latestTaskUpdatedAt descending
    projects.sort((a, b) => (b.latestTaskUpdatedAt || '').localeCompare(a.latestTaskUpdatedAt || ''));
    return { projects, unreadable };
  }

  async loadTasks(
    projectId: string,
    projectFolderName: string
  ): Promise<{ tasks: Task[]; unreadable: UnreadableFile[] }> {
    const tasks: Task[] = [];
    const unreadable: UnreadableFile[] = [];

    const prefix = `${projectFolderName}/`;
    for (const [path, content] of this.files.entries()) {
      if (path.startsWith(prefix)) {
        const relative = path.slice(prefix.length);
        if (relative === 'project.md' || relative.startsWith('.backup/')) {
          continue;
        }
        if (relative.endsWith('.md') && !relative.includes('/')) {
          const res = parseTask(content, relative, projectId);
          if (res.task) {
            tasks.push(res.task);
          } else {
            unreadable.push({
              fileName: relative,
              folderName: projectFolderName,
              reason: res.error || 'Invalid task file',
              rawContent: content,
            });
          }
        }
      }
    }

    return { tasks, unreadable };
  }

  async getTaskRaw(projectFolderName: string, taskId: string): Promise<string | null> {
    const path = `${projectFolderName}/${taskId}.md`;
    return this.files.get(path) || null;
  }

  async saveTask(
    projectFolderName: string,
    task: Task,
    lastKnownUpdatedAt?: string,
    forceOverwrite = false
  ): Promise<{ success: boolean; conflict?: ConflictInfo; error?: string }> {
    const filePath = `${projectFolderName}/${task.id}.md`;
    const backupPath = `${projectFolderName}/.backup/${task.id}.md.bak`;

    // 1. Re-read disk file for concurrent modification detection
    const existingRaw = this.files.get(filePath);
    if (existingRaw) {
      const diskParsed = parseTask(existingRaw, `${task.id}.md`, task.projectId);
      if (diskParsed.task) {
        if (!forceOverwrite && lastKnownUpdatedAt && diskParsed.task.updatedAt !== lastKnownUpdatedAt) {
          return {
            success: false,
            conflict: {
              taskId: task.id,
              projectId: task.projectId,
              currentDiskUpdatedAt: diskParsed.task.updatedAt,
              loadedUpdatedAt: lastKnownUpdatedAt,
              diskTask: diskParsed.task,
              memoryTask: task,
            },
          };
        }
      }

      // 2. Before overwriting an existing file, copy it to <project>/.backup/<file name>.bak
      this.files.set(backupPath, existingRaw);
    }

    // 3. Write new file (atomic replacement simulated)
    const serialized = serializeTask(task);
    this.files.set(filePath, serialized);
    this.persist();

    return { success: true };
  }

  async saveProject(project: Project): Promise<{ success: boolean; error?: string }> {
    const filePath = `${project.folderName}/project.md`;
    const serialized = serializeProject(project);
    this.files.set(filePath, serialized);
    this.persist();
    return { success: true };
  }

  async getBackupContent(projectFolderName: string, taskId: string): Promise<string | null> {
    const backupPath = `${projectFolderName}/.backup/${taskId}.md.bak`;
    return this.files.get(backupPath) || null;
  }

  // Inject a raw test file for test runner or simulation
  setRawFile(path: string, content: string) {
    this.files.set(path, content);
    this.persist();
  }

  deleteRawFile(path: string) {
    this.files.delete(path);
    this.persist();
  }

  getAllFiles(): Record<string, string> {
    const res: Record<string, string> = {};
    for (const [k, v] of this.files.entries()) {
      res[k] = v;
    }
    return res;
  }
}

/**
 * Native Browser File System Access API Adapter (HTML5 showDirectoryPicker)
 * Real directory access corresponding to Android SAF (Storage Access Framework).
 */
export class FileSystemAccessAdapter implements StorageAdapter {
  private dirHandle: FileSystemDirectoryHandle | null = null;
  private rootName: string = '';

  constructor(handle?: FileSystemDirectoryHandle) {
    if (handle) {
      this.dirHandle = handle;
      this.rootName = handle.name;
    }
  }

  isFileSystemAccessSupported(): boolean {
    return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
  }

  getRootName(): string {
    return this.rootName || 'No folder chosen';
  }

  async selectDirectory(): Promise<string> {
    if (!this.isFileSystemAccessSupported()) {
      throw new Error('File System Access API is not supported in this browser environment.');
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handle = await (window as any).showDirectoryPicker({
      mode: 'readwrite',
    });
    this.dirHandle = handle;
    this.rootName = handle.name;
    return handle.name;
  }

  async loadProjects(): Promise<{ projects: Project[]; unreadable: UnreadableFile[] }> {
    if (!this.dirHandle) return { projects: [], unreadable: [] };

    const projects: Project[] = [];
    const unreadable: UnreadableFile[] = [];

    // Iterate direct subdirectories
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    for await (const entry of (this.dirHandle as any).values()) {
      if (entry.kind === 'directory') {
        const folderName = entry.name;
        if (folderName.startsWith('.')) continue;

        try {
          const subDir = entry as FileSystemDirectoryHandle;
          let projectFileHandle: FileSystemFileHandle | null = null;
          try {
            projectFileHandle = await subDir.getFileHandle('project.md');
          } catch {
            // No project.md -> not an oloop project folder
            continue;
          }

          const file = await projectFileHandle.getFile();
          const content = await file.text();
          const parseRes = parseProject(content, folderName);

          if (parseRes.project) {
            const { tasks } = await this.loadTasks(parseRes.project.id, folderName);
            parseRes.project.tasksCount = tasks.length;
            parseRes.project.openTasksCount = tasks.filter((t) => t.status === 'open').length;

            let latest = parseRes.project.updatedAt || parseRes.project.createdAt || '';
            for (const t of tasks) {
              if (!latest || t.updatedAt > latest) {
                latest = t.updatedAt;
              }
            }
            parseRes.project.latestTaskUpdatedAt = latest;
            projects.push(parseRes.project);
          } else {
            unreadable.push({
              fileName: 'project.md',
              folderName,
              reason: parseRes.error || 'Failed to parse project.md',
              rawContent: content,
            });
          }
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : String(err);
          unreadable.push({
            fileName: 'project.md',
            folderName,
            reason: `I/O error: ${msg}`,
          });
        }
      }
    }

    projects.sort((a, b) => (b.latestTaskUpdatedAt || '').localeCompare(a.latestTaskUpdatedAt || ''));
    return { projects, unreadable };
  }

  async loadTasks(
    projectId: string,
    projectFolderName: string
  ): Promise<{ tasks: Task[]; unreadable: UnreadableFile[] }> {
    if (!this.dirHandle) return { tasks: [], unreadable: [] };

    const tasks: Task[] = [];
    const unreadable: UnreadableFile[] = [];

    try {
      const subDir = await this.dirHandle.getDirectoryHandle(projectFolderName);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      for await (const entry of (subDir as any).values()) {
        if (entry.kind === 'file' && entry.name.endsWith('.md') && entry.name !== 'project.md') {
          try {
            const fileHandle = entry as FileSystemFileHandle;
            const file = await fileHandle.getFile();
            const content = await file.text();
            const res = parseTask(content, entry.name, projectId);
            if (res.task) {
              tasks.push(res.task);
            } else {
              unreadable.push({
                fileName: entry.name,
                folderName: projectFolderName,
                reason: res.error || 'Invalid task file',
                rawContent: content,
              });
            }
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : String(err);
            unreadable.push({
              fileName: entry.name,
              folderName: projectFolderName,
              reason: `Failed to read: ${msg}`,
            });
          }
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      unreadable.push({
        fileName: '*.md',
        folderName: projectFolderName,
        reason: `Could not access directory: ${msg}`,
      });
    }

    return { tasks, unreadable };
  }

  async getTaskRaw(projectFolderName: string, taskId: string): Promise<string | null> {
    if (!this.dirHandle) return null;
    try {
      const subDir = await this.dirHandle.getDirectoryHandle(projectFolderName);
      const fileHandle = await subDir.getFileHandle(`${taskId}.md`);
      const file = await fileHandle.getFile();
      return await file.text();
    } catch {
      return null;
    }
  }

  async saveTask(
    projectFolderName: string,
    task: Task,
    lastKnownUpdatedAt?: string,
    forceOverwrite = false
  ): Promise<{ success: boolean; conflict?: ConflictInfo; error?: string }> {
    if (!this.dirHandle) return { success: false, error: 'No directory selected' };

    try {
      const subDir = await this.dirHandle.getDirectoryHandle(projectFolderName);
      const targetFileName = `${task.id}.md`;

      // 1. Check for existing file & detect conflict
      let existingContent: string | null = null;
      try {
        const existingHandle = await subDir.getFileHandle(targetFileName);
        const existingFile = await existingHandle.getFile();
        existingContent = await existingFile.text();
      } catch {
        // File does not exist yet (create scenario)
      }

      if (existingContent) {
        const diskParsed = parseTask(existingContent, targetFileName, task.projectId);
        if (diskParsed.task) {
          if (!forceOverwrite && lastKnownUpdatedAt && diskParsed.task.updatedAt !== lastKnownUpdatedAt) {
            return {
              success: false,
              conflict: {
                taskId: task.id,
                projectId: task.projectId,
                currentDiskUpdatedAt: diskParsed.task.updatedAt,
                loadedUpdatedAt: lastKnownUpdatedAt,
                diskTask: diskParsed.task,
                memoryTask: task,
              },
            };
          }
        }

        // 2. Backup copy to <project>/.backup/<file name>.bak
        try {
          const backupDir = await subDir.getDirectoryHandle('.backup', { create: true });
          const backupFileHandle = await backupDir.getFileHandle(`${targetFileName}.bak`, { create: true });
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const backupWritable = await (backupFileHandle as any).createWritable();
          await backupWritable.write(existingContent);
          await backupWritable.close();
        } catch (backupErr) {
          console.warn('Could not write backup file:', backupErr);
        }
      }

      // 3. Safe write to temporary file then write to target
      const serialized = serializeTask(task);
      const targetHandle = await subDir.getFileHandle(targetFileName, { create: true });
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const writable = await (targetHandle as any).createWritable();
      await writable.write(serialized);
      await writable.close();

      return { success: true };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { success: false, error: `Write failed: ${msg}` };
    }
  }

  async saveProject(project: Project): Promise<{ success: boolean; error?: string }> {
    if (!this.dirHandle) return { success: false, error: 'No directory selected' };
    try {
      const subDir = await this.dirHandle.getDirectoryHandle(project.folderName, { create: true });
      const serialized = serializeProject(project);
      const handle = await subDir.getFileHandle('project.md', { create: true });
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const writable = await (handle as any).createWritable();
      await writable.write(serialized);
      await writable.close();
      return { success: true };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return { success: false, error: `Failed to save project: ${msg}` };
    }
  }

  async getBackupContent(projectFolderName: string, taskId: string): Promise<string | null> {
    if (!this.dirHandle) return null;
    try {
      const subDir = await this.dirHandle.getDirectoryHandle(projectFolderName);
      const backupDir = await subDir.getDirectoryHandle('.backup');
      const backupHandle = await backupDir.getFileHandle(`${taskId}.md.bak`);
      const file = await backupHandle.getFile();
      return await file.text();
    } catch {
      return null;
    }
  }
}
