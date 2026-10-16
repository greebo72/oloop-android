/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useMemo, useTransition } from 'react';
import {
  Folder,
  Settings as SettingsIcon,
  Plus,
  ArrowLeft,
  CheckCircle2,
  Circle,
  Clock,
  Tag,
  MessageSquare,
  FileText,
  AlertTriangle,
  RotateCcw,
  Save,
  Download,
  Copy,
  Check,
  Terminal,
  Smartphone,
  Tablet,
  Layout,
  Layers,
  Code2,
  BookOpen,
  Send,
  Eye,
  Edit3,
  RefreshCw,
  FolderOpen,
  User,
  ShieldAlert,
} from 'lucide-react';
import { marked } from 'marked';
import {
  Project,
  Task,
  TaskType,
  GroupingMode,
  UnreadableFile,
  ConflictInfo,
  TASK_TYPES,
  CLOSED_STATUS_LABELS,
  TYPE_BADGE_COLORS,
} from './oloop/types';
import {
  LocalStorageAdapter,
  FileSystemAccessAdapter,
  StorageAdapter,
} from './oloop/storage';
import { nowUtcIso, generateUuid, serializeTask } from './oloop/parser';
import { formatRelativeAge, getDateBucket, DATE_BUCKET_ORDER, DateBucket } from './oloop/dateUtils';
import { runOloopTestSuite, TestSuiteSummary } from './oloop/testRunner';
import { ANDROID_PROJECT_FILES, AndroidFile } from './oloop/androidSourceFiles';

// Singleton storage adapter
const localAdapter = new LocalStorageAdapter();

export default function App() {
  // Navigation & View Mode
  const [activeTab, setActiveTab] = useState<'app' | 'tests' | 'code' | 'docs'>('app');
  const [viewLayout, setViewLayout] = useState<'auto' | 'portrait' | 'landscape'>('auto');
  const [windowDimensions, setWindowDimensions] = useState({
    width: typeof window !== 'undefined' ? window.innerWidth : 1024,
    height: typeof window !== 'undefined' ? window.innerHeight : 768,
  });

  useEffect(() => {
    const handleResize = () => {
      setWindowDimensions({
        width: window.innerWidth,
        height: window.innerHeight,
      });
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Effective orientation: if 'portrait', always portrait; if 'landscape', always landscape;
  // if 'auto', portrait when width < 768 or height > width
  const isLandscapeEffective = useMemo(() => {
    if (viewLayout === 'portrait') return false;
    if (viewLayout === 'landscape') return true;
    return windowDimensions.width >= 768 && windowDimensions.width > windowDimensions.height;
  }, [viewLayout, windowDimensions]);

  // Storage and Data State
  const [adapter, setAdapter] = useState<StorageAdapter>(localAdapter);
  const [storageType, setStorageType] = useState<'virtual' | 'saf_fs'>('virtual');
  const [rootFolderName, setRootFolderName] = useState<string>(localAdapter.getRootName());
  const [userActor, setUserActor] = useState<string>(() => localStorage.getItem('oloop_user_actor') || 'Sample User');

  const [projects, setProjects] = useState<Project[]>([]);
  const [unreadableFiles, setUnreadableFiles] = useState<UnreadableFile[]>([]);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);

  // When switching to landscape, ensure first task is selected so 70% pane is populated
  useEffect(() => {
    if (isLandscapeEffective) {
      if (!selectedProject && projects.length > 0) {
        handleSelectProject(projects[0]);
      } else if (!selectedTask && tasks.length > 0) {
        setSelectedTask(tasks[0]);
      }
    }
  }, [isLandscapeEffective, selectedProject, selectedTask, projects, tasks]);

  // Filters & Grouping
  const [showClosed, setShowClosed] = useState<boolean>(false);
  const [groupingMode, setGroupingMode] = useState<GroupingMode>('none');

  // Edit State & Unsaved Changes
  const [editTitle, setEditTitle] = useState<string>('');
  const [editDescription, setEditDescription] = useState<string>('');
  const [editLabels, setEditLabels] = useState<string>('');
  const [isMarkdownPreview, setIsMarkdownPreview] = useState<boolean>(false);
  const [newCommentInput, setNewCommentInput] = useState<string>('');

  // Modals
  const [showSettingsModal, setShowSettingsModal] = useState<boolean>(false);
  const [showNewTaskModal, setShowNewTaskModal] = useState<boolean>(false);
  const [showActorModal, setShowActorModal] = useState<boolean>(false);
  const [pendingActorAction, setPendingActorAction] = useState<(() => void) | null>(null);
  const [actorModalInput, setActorModalInput] = useState<string>('');
  const [conflictState, setConflictState] = useState<ConflictInfo | null>(null);
  const [showUnsavedChangesModal, setShowUnsavedChangesModal] = useState<boolean>(false);
  const [pendingNavigation, setPendingNavigation] = useState<(() => void) | null>(null);

  // Banner message
  const [bannerMessage, setBannerMessage] = useState<{ text: string; type: 'success' | 'error' | 'info' } | null>(null);

  // New task form
  const [newTaskType, setNewTaskType] = useState<TaskType>('todo');
  const [newTaskTitle, setNewTaskTitle] = useState<string>('');
  const [newTaskDescription, setNewTaskDescription] = useState<string>('');
  const [newTaskLabels, setNewTaskLabels] = useState<string>('');

  // Test runner state
  const [testSummary, setTestSummary] = useState<TestSuiteSummary | null>(null);
  const [isRunningTests, setIsRunningTests] = useState<boolean>(false);

  // Android Code Viewer State
  const [selectedCodeFile, setSelectedCodeFile] = useState<AndroidFile>(ANDROID_PROJECT_FILES[0]);
  const [copiedCode, setCopiedCode] = useState<boolean>(false);

  // Backup viewer
  const [viewingBackup, setViewingBackup] = useState<{ taskId: string; content: string } | null>(null);

  // Initialize
  useEffect(() => {
    loadAllData();
    // Auto-run tests on startup
    runTests();
  }, [adapter]);

  // Sync selected task to edit state
  useEffect(() => {
    if (selectedTask) {
      setEditTitle(selectedTask.title);
      setEditDescription(selectedTask.description);
      setEditLabels(selectedTask.labels.join(', '));
      setIsMarkdownPreview(false);
      setNewCommentInput('');
    }
  }, [selectedTask]);

  const hasUnsavedChanges = useMemo(() => {
    if (!selectedTask) return false;
    const currentLabels = editLabels
      .split(',')
      .map((l) => l.trim())
      .filter((l) => l.length > 0);
    const labelsChanged =
      currentLabels.length !== selectedTask.labels.length ||
      currentLabels.some((l, idx) => l !== selectedTask.labels[idx]);

    return (
      editTitle.trim() !== selectedTask.title.trim() ||
      editDescription.trim() !== selectedTask.description.trim() ||
      labelsChanged
    );
  }, [selectedTask, editTitle, editDescription, editLabels]);

  const loadAllData = async () => {
    try {
      const { projects: loadedProjects, unreadable: loadedUnreadable } = await adapter.loadProjects();
      setProjects(loadedProjects);
      setUnreadableFiles(loadedUnreadable);

      if (loadedProjects.length > 0) {
        // Automatically select the first project if none is active
        const currentProjId = selectedProject?.id;
        const targetProj = currentProjId
          ? loadedProjects.find((p) => p.id === currentProjId) || loadedProjects[0]
          : loadedProjects[0];

        setSelectedProject(targetProj);
        const { tasks: loadedTasks } = await adapter.loadTasks(targetProj.id, targetProj.folderName);
        setTasks(loadedTasks);

        if (selectedTask) {
          const refreshedTask = loadedTasks.find((t) => t.id === selectedTask.id);
          if (refreshedTask) setSelectedTask(refreshedTask);
          else if (loadedTasks.length > 0) setSelectedTask(loadedTasks[0]);
        } else if (loadedTasks.length > 0) {
          setSelectedTask(loadedTasks[0]);
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      showBanner(`Failed to load data: ${msg}`, 'error');
    }
  };

  const showBanner = (text: string, type: 'success' | 'error' | 'info' = 'info') => {
    setBannerMessage({ text, type });
    setTimeout(() => {
      setBannerMessage((current) => (current?.text === text ? null : current));
    }, 4000);
  };

  // Safe navigation guard for unsaved changes
  const guardNavigation = (action: () => void) => {
    if (hasUnsavedChanges) {
      setPendingNavigation(() => action);
      setShowUnsavedChangesModal(true);
    } else {
      action();
    }
  };

  // Actor guard
  const requireActor = (callback: () => void) => {
    if (!userActor || userActor.trim().length === 0) {
      setPendingActorAction(() => callback);
      setActorModalInput('');
      setShowActorModal(true);
      return false;
    }
    return true;
  };

  const handleSaveActor = (name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setUserActor(trimmed);
    localStorage.setItem('oloop_user_actor', trimmed);
    setShowActorModal(false);
    if (pendingActorAction) {
      const action = pendingActorAction;
      setPendingActorAction(null);
      action();
    }
    showBanner(`User name saved: "${trimmed}"`, 'success');
  };

  // Open directory via HTML5 File System Access API (matches Android SAF ACTION_OPEN_DOCUMENT_TREE)
  const handlePickDirectory = async () => {
    try {
      const fsAdapter = new FileSystemAccessAdapter();
      const folder = await fsAdapter.selectDirectory();
      setAdapter(fsAdapter);
      setStorageType('saf_fs');
      setRootFolderName(folder);
      setSelectedProject(null);
      setSelectedTask(null);
      showBanner(`Opened local directory: ${folder}`, 'success');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      if (!msg.includes('aborted') && !msg.includes('cancelled')) {
        showBanner(`Could not open directory: ${msg}`, 'error');
      }
    }
  };

  const handleResetToFixtures = () => {
    localAdapter.resetToFixtures();
    setAdapter(localAdapter);
    setStorageType('virtual');
    setRootFolderName(localAdapter.getRootName());
    setSelectedProject(null);
    setSelectedTask(null);
    loadAllData();
    showBanner('Reset workspace to default oloop fixtures', 'success');
  };

  const handleSelectProject = (proj: Project) => {
    guardNavigation(async () => {
      setSelectedProject(proj);
      const { tasks: projTasks, unreadable: projUnreadable } = await adapter.loadTasks(proj.id, proj.folderName);
      setTasks(projTasks);
      setUnreadableFiles(projUnreadable);
      if (projTasks.length > 0) {
        setSelectedTask(projTasks[0]);
      } else {
        setSelectedTask(null);
      }
    });
  };

  const handleSelectTask = (task: Task) => {
    guardNavigation(() => {
      setSelectedTask(task);
    });
  };

  // Save Task Edits (Title, Description, Labels)
  const handleSaveTaskEdits = async () => {
    if (!selectedTask || !selectedProject) return;
    if (!requireActor(handleSaveTaskEdits)) return;

    const trimmedTitle = editTitle.trim();
    if (!trimmedTitle) {
      showBanner('Title cannot be empty', 'error');
      return;
    }

    const cleanLabels = editLabels
      .split(',')
      .map((l) => l.trim())
      .filter((l) => l.length > 0);
    const uniqueLabels = Array.from(new Set(cleanLabels));

    const updatedTask: Task = {
      ...selectedTask,
      title: trimmedTitle,
      description: editDescription.trim(),
      labels: uniqueLabels,
      updatedAt: nowUtcIso(),
      updatedBy: userActor,
    };

    const res = await adapter.saveTask(selectedProject.folderName, updatedTask, selectedTask.updatedAt);
    if (res.success) {
      setSelectedTask(updatedTask);
      showBanner('Task saved (backup copy created)', 'success');
      loadAllData();
    } else if (res.conflict) {
      setConflictState(res.conflict);
    } else {
      showBanner(res.error || 'Failed to save task', 'error');
    }
  };

  // Toggle Task Status (Close / Reopen)
  const handleToggleTaskStatus = async (taskToToggle: Task) => {
    if (!selectedProject) return;
    if (!requireActor(() => handleToggleTaskStatus(taskToToggle))) return;

    const newStatus = taskToToggle.status === 'open' ? 'closed' : 'open';
    const updatedTask: Task = {
      ...taskToToggle,
      status: newStatus,
      updatedAt: nowUtcIso(),
      updatedBy: userActor,
    };

    const res = await adapter.saveTask(selectedProject.folderName, updatedTask, taskToToggle.updatedAt);
    if (res.success) {
      if (selectedTask?.id === taskToToggle.id) {
        setSelectedTask(updatedTask);
      }
      showBanner(
        `Task marked as ${newStatus === 'closed' ? CLOSED_STATUS_LABELS[taskToToggle.type] : 'Open'}`,
        'success'
      );
      loadAllData();
    } else if (res.conflict) {
      setConflictState(res.conflict);
    } else {
      showBanner(res.error || 'Failed to update status', 'error');
    }
  };

  // Add Comment
  const handleAddComment = async () => {
    if (!selectedTask || !selectedProject || !newCommentInput.trim()) return;
    if (!requireActor(handleAddComment)) return;

    const commentText = newCommentInput.trim();
    const now = nowUtcIso();
    const updatedComments = [
      ...selectedTask.comments,
      {
        body: commentText,
        createdAt: now,
      },
    ];

    const updatedTask: Task = {
      ...selectedTask,
      comments: updatedComments,
      updatedAt: now,
      updatedBy: userActor,
    };

    const res = await adapter.saveTask(selectedProject.folderName, updatedTask, selectedTask.updatedAt);
    if (res.success) {
      setSelectedTask(updatedTask);
      setNewCommentInput('');
      showBanner('Comment added', 'success');
      loadAllData();
    } else if (res.conflict) {
      setConflictState(res.conflict);
    } else {
      showBanner(res.error || 'Failed to add comment', 'error');
    }
  };

  // Create New Task
  const handleCreateTask = async () => {
    if (!selectedProject) return;
    if (!requireActor(handleCreateTask)) return;

    const trimmedTitle = newTaskTitle.trim();
    if (!trimmedTitle) {
      showBanner('Title is required', 'error');
      return;
    }

    const cleanLabels = newTaskLabels
      .split(',')
      .map((l) => l.trim())
      .filter((l) => l.length > 0);
    const uniqueLabels = Array.from(new Set(cleanLabels));
    const now = nowUtcIso();
    const newId = generateUuid();

    const createdTask: Task = {
      formatVersion: 1,
      id: newId,
      projectId: selectedProject.id,
      type: newTaskType,
      title: trimmedTitle,
      status: 'open',
      labels: uniqueLabels,
      imagePath: null,
      createdAt: now,
      createdBy: userActor,
      updatedAt: now,
      updatedBy: userActor,
      comments: [],
      description: newTaskDescription.trim(),
    };

    const res = await adapter.saveTask(selectedProject.folderName, createdTask);
    if (res.success) {
      setShowNewTaskModal(false);
      setNewTaskTitle('');
      setNewTaskDescription('');
      setNewTaskLabels('');
      setNewTaskType('todo');
      setSelectedTask(createdTask);
      showBanner(`Created new ${createdTask.type} task`, 'success');
      loadAllData();
    } else {
      showBanner(res.error || 'Failed to create task', 'error');
    }
  };

  // View Backup File
  const handleViewBackup = async (taskId: string) => {
    if (!selectedProject) return;
    const backupContent = await adapter.getBackupContent(selectedProject.folderName, taskId);
    if (backupContent) {
      setViewingBackup({ taskId, content: backupContent });
    } else {
      showBanner('No backup file found (.backup/*.bak is created upon first edit)', 'info');
    }
  };

  // Resolve Conflict: Reload from disk
  const handleResolveConflictReload = () => {
    if (!conflictState) return;
    setSelectedTask(conflictState.diskTask);
    setConflictState(null);
    showBanner('Reloaded latest task version from disk', 'info');
    loadAllData();
  };

  // Resolve Conflict: Force overwrite
  const handleResolveConflictOverwrite = async () => {
    if (!conflictState || !selectedProject) return;
    const res = await adapter.saveTask(
      selectedProject.folderName,
      conflictState.memoryTask,
      undefined,
      true
    );
    if (res.success) {
      setSelectedTask(conflictState.memoryTask);
      setConflictState(null);
      showBanner('Overwrote file on disk as requested', 'success');
      loadAllData();
    } else {
      showBanner(res.error || 'Failed to overwrite', 'error');
    }
  };

  // Run Test Suite
  const runTests = async () => {
    setIsRunningTests(true);
    const summary = await runOloopTestSuite();
    setTestSummary(summary);
    setIsRunningTests(false);
  };

  // Copy code helper
  const handleCopyCode = () => {
    navigator.clipboard.writeText(selectedCodeFile.code);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  // Export / Download Android Codebase as Zip / bundle
  const handleDownloadAndroidProject = () => {
    const textBlob = new Blob(
      [
        `# oloop Native Android Client Source Bundle\n\n` +
          `Generated for: ${userActor}\n\n` +
          ANDROID_PROJECT_FILES.map((f) => `// ================================\n// FILE: ${f.path}\n// ================================\n\n${f.code}\n\n`).join('\n')
      ],
      { type: 'text/plain;charset=utf-8' }
    );
    const url = URL.createObjectURL(textBlob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'oloop-android-kotlin-source.txt';
    a.click();
    URL.revokeObjectURL(url);
    showBanner('Downloaded complete Android Kotlin sources', 'success');
  };

  // Filter tasks: hide closed by default unless showClosed is toggled
  const filteredTasks = useMemo(() => {
    let list = tasks;
    if (!showClosed) {
      list = list.filter((t) => t.status === 'open');
    }
    // Sort within any group by updatedAt descending
    return [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }, [tasks, showClosed]);

  // Grouped Tasks
  const groupedTasks = useMemo(() => {
    if (groupingMode === 'none') {
      return [{ groupName: '', tasks: filteredTasks }];
    }

    if (groupingMode === 'type') {
      return TASK_TYPES.map((type) => ({
        groupName: `${type.toUpperCase()} (${filteredTasks.filter((t) => t.type === type).length})`,
        type,
        tasks: filteredTasks.filter((t) => t.type === type),
      })).filter((g) => g.tasks.length > 0);
    }

    if (groupingMode === 'status') {
      const openGroup = filteredTasks.filter((t) => t.status === 'open');
      const closedGroup = filteredTasks.filter((t) => t.status === 'closed');
      const res = [];
      if (openGroup.length > 0) res.push({ groupName: `OPEN (${openGroup.length})`, tasks: openGroup });
      if (closedGroup.length > 0) res.push({ groupName: `CLOSED (${closedGroup.length})`, tasks: closedGroup });
      return res;
    }

    if (groupingMode === 'created') {
      const map: Record<DateBucket, Task[]> = {
        Today: [],
        Yesterday: [],
        'This week': [],
        'This month': [],
        Older: [],
      };
      for (const t of filteredTasks) {
        const bucket = getDateBucket(t.createdAt);
        map[bucket].push(t);
      }
      return DATE_BUCKET_ORDER.map((bucket) => ({
        groupName: `${bucket} (${map[bucket].length})`,
        tasks: map[bucket],
      })).filter((g) => g.tasks.length > 0);
    }

    if (groupingMode === 'modified') {
      const map: Record<DateBucket, Task[]> = {
        Today: [],
        Yesterday: [],
        'This week': [],
        'This month': [],
        Older: [],
      };
      for (const t of filteredTasks) {
        const bucket = getDateBucket(t.updatedAt);
        map[bucket].push(t);
      }
      return DATE_BUCKET_ORDER.map((bucket) => ({
        groupName: `${bucket} (${map[bucket].length})`,
        tasks: map[bucket],
      })).filter((g) => g.tasks.length > 0);
    }

    return [{ groupName: '', tasks: filteredTasks }];
  }, [filteredTasks, groupingMode]);

  // Render markdown helper
  const renderedDescriptionHtml = useMemo(() => {
    if (!selectedTask?.description) return '';
    try {
      return marked.parse(selectedTask.description) as string;
    } catch {
      return selectedTask.description;
    }
  }, [selectedTask?.description]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-600 selection:text-white">
      {/* Top Main App Bar */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-30 px-4 py-2.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-emerald-600 flex items-center justify-center font-bold text-white shadow-md shadow-emerald-950">
            <span className="text-sm font-black tracking-tighter">ol</span>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-base tracking-tight text-white">oloop</span>
              <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-emerald-950 text-emerald-400 font-semibold border border-emerald-800">
                Android / SAF Client
              </span>
            </div>
            <div className="text-xs text-slate-400 flex items-center gap-1.5 truncate max-w-xs md:max-w-md">
              <Folder className="w-3 h-3 text-slate-500" />
              <span className="truncate">{rootFolderName}</span>
              <span className="text-slate-600">•</span>
              <span className="text-slate-400">Actor: <strong className="text-slate-200">{userActor || '(not set)'}</strong></span>
            </div>
          </div>
        </div>

        {/* Global Navigation Tabs */}
        <div className="flex items-center gap-1 bg-slate-800/80 p-1 rounded-xl border border-slate-700/60 text-xs font-medium">
          <button
            onClick={() => setActiveTab('app')}
            className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
              activeTab === 'app'
                ? 'bg-emerald-600 text-white shadow-sm font-semibold'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Android App</span>
          </button>
          <button
            onClick={() => setActiveTab('tests')}
            className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
              activeTab === 'tests'
                ? 'bg-indigo-600 text-white shadow-sm font-semibold'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            <span>Test Suite ({testSummary?.passed || 0}/{testSummary?.total || 0})</span>
          </button>
          <button
            onClick={() => setActiveTab('code')}
            className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
              activeTab === 'code'
                ? 'bg-indigo-600 text-white shadow-sm font-semibold'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Code2 className="w-3.5 h-3.5" />
            <span>Kotlin Sources</span>
          </button>
          <button
            onClick={() => setActiveTab('docs')}
            className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
              activeTab === 'docs'
                ? 'bg-indigo-600 text-white shadow-sm font-semibold'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Assumptions & Spec</span>
          </button>
        </div>

        {/* Settings & Layout Controls */}
        <div className="flex items-center gap-2">
          {activeTab === 'app' && (
            <div className="flex items-center gap-1 bg-slate-800 p-1 rounded-lg border border-slate-700/60 text-xs">
              <button
                onClick={() => setViewLayout('portrait')}
                title="Hochkant (100% Vollbild Stack mit Back-Button)"
                className={`px-2 py-1 rounded flex items-center gap-1 transition ${
                  viewLayout === 'portrait'
                    ? 'bg-emerald-600 text-white font-semibold'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Hochkant</span>
              </button>
              <button
                onClick={() => {
                  setViewLayout('landscape');
                  if (!selectedProject && projects.length > 0) {
                    handleSelectProject(projects[0]);
                  } else if (!selectedTask && tasks.length > 0) {
                    setSelectedTask(tasks[0]);
                  }
                }}
                title="Querformat (30% Liste / 70% Inhalt Split)"
                className={`px-2 py-1 rounded flex items-center gap-1 transition ${
                  viewLayout === 'landscape'
                    ? 'bg-emerald-600 text-white font-semibold'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                <Layout className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">30 / 70</span>
              </button>
              <button
                onClick={() => setViewLayout('auto')}
                title="Automatisch nach Fenstergröße anpassen"
                className={`px-2 py-1 rounded text-[11px] font-medium transition ${
                  viewLayout === 'auto' ? 'bg-slate-700 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                Auto
              </button>
            </div>
          )}

          <button
            onClick={() => setShowSettingsModal(true)}
            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 transition"
            title="Settings (Data folder & Actor)"
          >
            <SettingsIcon className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Global Alert Banner */}
      {bannerMessage && (
        <div
          className={`px-4 py-2 text-xs flex items-center justify-between transition-all ${
            bannerMessage.type === 'success'
              ? 'bg-emerald-950/80 border-b border-emerald-800 text-emerald-200'
              : bannerMessage.type === 'error'
              ? 'bg-rose-950/80 border-b border-rose-800 text-rose-200'
              : 'bg-blue-950/80 border-b border-blue-800 text-blue-200'
          }`}
        >
          <div className="flex items-center gap-2">
            {bannerMessage.type === 'success' && <Check className="w-3.5 h-3.5" />}
            {bannerMessage.type === 'error' && <AlertTriangle className="w-3.5 h-3.5" />}
            <span>{bannerMessage.text}</span>
          </div>
          <button onClick={() => setBannerMessage(null)} className="text-slate-400 hover:text-white">
            ✕
          </button>
        </div>
      )}

      {/* Main Content Areas */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {activeTab === 'app' && (
          <div className="flex-1 flex justify-center bg-slate-950 p-2 sm:p-3 overflow-hidden">
            {/* Main Application Frame */}
            <div
              className={`flex-1 transition-all flex flex-col bg-slate-900 rounded-xl border border-slate-800 shadow-xl overflow-hidden ${
                !isLandscapeEffective ? 'max-w-xl mx-auto w-full' : 'w-full'
              }`}
            >
              {/* Android System Status Bar Simulation */}
              <div className="bg-slate-950 px-4 py-1.5 flex items-center justify-between text-[11px] text-slate-400 select-none border-b border-slate-800 flex-shrink-0">
                <span>12:00</span>
                <div className="flex items-center gap-2">
                  <span className="text-[10px] text-slate-400">
                    {isLandscapeEffective ? 'Landscape (30% Liste / 70% Inhalt)' : 'Hochkant (Vollbild)'}
                  </span>
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                </div>
              </div>

              {/* In-App View Router */}
              <div className="flex-1 flex overflow-hidden w-full">
                {/* Left Master Pane: Projects or Tasks List (30% in Landscape, Full-screen in Portrait when no task selected) */}
                <div
                  style={isLandscapeEffective ? { width: '30%', minWidth: '280px', maxWidth: '420px', flexShrink: 0 } : undefined}
                  className={`flex flex-col border-r border-slate-800/80 transition-all min-w-0 h-full overflow-hidden ${
                    isLandscapeEffective
                      ? 'flex-shrink-0'
                      : selectedTask
                      ? 'hidden' // In portrait mode, hide list completely when task is selected
                      : 'w-full'
                  }`}
                >
                  {!selectedProject ? (
                    // SCREEN 1: PROJECTS LIST
                    <div className="flex-1 flex flex-col overflow-hidden bg-slate-900">
                      <div className="p-3.5 border-b border-slate-800 flex items-center justify-between bg-slate-900/60">
                        <div>
                          <h1 className="text-base font-bold text-white tracking-tight">Projects</h1>
                          <p className="text-[11px] text-slate-400">
                            {projects.length} project{projects.length === 1 ? '' : 's'} discovered in root
                          </p>
                        </div>
                        <button
                          onClick={() => setShowSettingsModal(true)}
                          className="text-xs text-emerald-400 hover:text-emerald-300 font-medium flex items-center gap-1 px-2.5 py-1 rounded-md bg-emerald-950/60 border border-emerald-800/60"
                        >
                          <FolderOpen className="w-3.5 h-3.5" />
                          <span>Change Root</span>
                        </button>
                      </div>

                      {/* Unreadable Files Alert (if any) */}
                      {unreadableFiles.length > 0 && (
                        <div className="m-3 p-3 rounded-lg bg-rose-950/40 border border-rose-800/60 text-xs">
                          <div className="flex items-center gap-1.5 text-rose-300 font-semibold mb-1">
                            <ShieldAlert className="w-4 h-4" />
                            <span>Unreadable files ({unreadableFiles.length})</span>
                          </div>
                          <p className="text-[11px] text-rose-300/80 mb-2">
                            The following files violate the oloop schema or file rules and are ignored safely:
                          </p>
                          <div className="space-y-1 max-h-24 overflow-y-auto">
                            {unreadableFiles.map((u, i) => (
                              <div key={i} className="text-[11px] bg-rose-950/60 p-1.5 rounded font-mono text-rose-200">
                                <strong>{u.folderName}/{u.fileName}</strong>: {u.reason}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Projects List Content */}
                      <div className="flex-1 overflow-y-auto divide-y divide-slate-800/60">
                        {projects.length === 0 ? (
                          <div className="p-8 text-center text-slate-500">
                            <Folder className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p className="text-xs font-medium text-slate-400">No oloop projects found</p>
                            <p className="text-[11px] text-slate-500 mt-1 max-w-xs mx-auto">
                              Every direct subfolder with a valid <code>project.md</code> is loaded as a project.
                            </p>
                            <button
                              onClick={handleResetToFixtures}
                              className="mt-4 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs rounded-lg transition"
                            >
                              Load Sample Fixture Projects
                            </button>
                          </div>
                        ) : (
                          projects.map((proj) => (
                            <button
                              key={proj.id}
                              onClick={() => handleSelectProject(proj)}
                              className="w-full text-left p-3.5 hover:bg-slate-800/50 transition flex items-center justify-between group"
                            >
                              <div className="flex-1 min-w-0 pr-3">
                                <div className="flex items-center gap-2">
                                  <span className="font-semibold text-slate-100 text-sm group-hover:text-emerald-400 transition truncate">
                                    {proj.name}
                                  </span>
                                </div>
                                <div className="text-xs text-slate-400 mt-0.5 flex items-center gap-2">
                                  <span className="text-emerald-400 font-medium">
                                    {proj.openTasksCount ?? 0} open
                                  </span>
                                  <span className="text-slate-600">•</span>
                                  <span className="text-slate-500">{proj.tasksCount ?? 0} total</span>
                                  <span className="text-slate-600">•</span>
                                  <span className="text-slate-500 font-mono text-[10px] truncate">
                                    {proj.folderName}
                                  </span>
                                </div>
                              </div>
                              <div className="text-right flex-shrink-0 text-[11px] text-slate-500">
                                {proj.latestTaskUpdatedAt ? (
                                  <div className="flex items-center gap-1">
                                    <Clock className="w-3 h-3 text-slate-600" />
                                    <span>{formatRelativeAge(proj.latestTaskUpdatedAt)}</span>
                                  </div>
                                ) : (
                                  'Quiet'
                                )}
                              </div>
                            </button>
                          ))
                        )}
                      </div>
                    </div>
                  ) : (
                    // SCREEN 2: TASKS LIST IN ACTIVE PROJECT
                    <div className="flex-1 flex flex-col overflow-hidden bg-slate-900">
                      {/* Task List Header */}
                      <div className="p-3 border-b border-slate-800 bg-slate-900/90">
                        {/* Project Switcher Row */}
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">
                              <span>Projekt</span>
                              <button
                                onClick={() => guardNavigation(() => setSelectedProject(null))}
                                className="text-emerald-400 hover:text-emerald-300 text-[10px] font-semibold flex items-center gap-0.5"
                              >
                                <ArrowLeft className="w-3 h-3" />
                                <span>Alle Projekte</span>
                              </button>
                            </div>
                            <select
                              value={selectedProject.id}
                              onChange={(e) => {
                                const found = projects.find((p) => p.id === e.target.value);
                                if (found) handleSelectProject(found);
                              }}
                              className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white font-bold focus:outline-none focus:border-emerald-500 cursor-pointer"
                            >
                              {projects.map((p) => (
                                <option key={p.id} value={p.id}>
                                  {p.name} ({p.openTasksCount ?? 0} offen)
                                </option>
                              ))}
                            </select>
                          </div>
                        </div>

                        {/* Controls: Stats, Show closed, New Task */}
                        <div className="flex items-center justify-between gap-2">
                          <p className="text-[11px] text-slate-400">
                            {filteredTasks.length} task{filteredTasks.length === 1 ? '' : 's'}
                          </p>

                          <div className="flex items-center gap-2">
                            {/* Show Closed Switch */}
                            <label className="flex items-center gap-1 text-xs text-slate-300 cursor-pointer bg-slate-800/70 px-2 py-1 rounded-md border border-slate-700/60">
                              <input
                                type="checkbox"
                                checked={showClosed}
                                onChange={(e) => setShowClosed(e.target.checked)}
                                className="rounded bg-slate-950 border-slate-700 text-emerald-500 focus:ring-0 w-3.5 h-3.5"
                              />
                              <span className="text-[11px]">Erledigte</span>
                            </label>

                            <button
                              onClick={() => setShowNewTaskModal(true)}
                              className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs px-2.5 py-1 rounded-lg flex items-center gap-1 shadow font-medium transition flex-shrink-0"
                            >
                              <Plus className="w-3.5 h-3.5" />
                              <span>Neu</span>
                            </button>
                          </div>
                        </div>

                        {/* Grouping Filter Bar */}
                        <div className="mt-2.5 flex items-center gap-1 overflow-x-auto pb-0.5 text-[11px]">
                          <span className="text-slate-500 text-[10px] uppercase font-semibold mr-1">Group:</span>
                          {(['none', 'type', 'status', 'created', 'modified'] as GroupingMode[]).map((mode) => (
                            <button
                              key={mode}
                              onClick={() => setGroupingMode(mode)}
                              className={`px-2 py-0.5 rounded-md font-medium capitalize transition whitespace-nowrap ${
                                groupingMode === mode
                                  ? 'bg-emerald-600 text-white'
                                  : 'bg-slate-800 text-slate-400 hover:text-slate-200'
                              }`}
                            >
                              {mode === 'none'
                                ? 'None'
                                : mode === 'created'
                                ? 'Creation Date'
                                : mode === 'modified'
                                ? 'Mod Date'
                                : mode === 'type'
                                ? 'Task Type'
                                : 'Task Status'}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Tasks List */}
                      <div className="flex-1 overflow-y-auto divide-y divide-slate-800/60">
                        {groupedTasks.length === 0 || filteredTasks.length === 0 ? (
                          <div className="p-8 text-center text-slate-500">
                            <CheckCircle2 className="w-8 h-8 mx-auto mb-2 opacity-30 text-emerald-500" />
                            <p className="text-xs font-medium text-slate-300">No tasks to display</p>
                            <p className="text-[11px] text-slate-500 mt-1">
                              {showClosed ? 'Project has no tasks yet.' : 'All tasks are closed. Toggle "Show closed" to view them.'}
                            </p>
                            <button
                              onClick={() => setShowNewTaskModal(true)}
                              className="mt-3 px-3 py-1 bg-emerald-600 text-white text-xs rounded-md"
                            >
                              Create a Task
                            </button>
                          </div>
                        ) : (
                          groupedTasks.map((group, gIdx) => (
                            <div key={gIdx}>
                              {group.groupName && (
                                <div className="bg-slate-950/70 px-3 py-1 text-[10px] font-bold tracking-wider uppercase text-slate-400 border-b border-slate-800/60 sticky top-0 backdrop-blur z-10 flex items-center justify-between">
                                  <span>{group.groupName}</span>
                                </div>
                              )}
                              <div className="divide-y divide-slate-800/40">
                                {group.tasks.map((task) => {
                                  const isSelected = selectedTask?.id === task.id;
                                  const isClosed = task.status === 'closed';
                                  const badgeStyle = TYPE_BADGE_COLORS[task.type];

                                  return (
                                    <div
                                      key={task.id}
                                      onClick={() => handleSelectTask(task)}
                                      className={`p-3 hover:bg-slate-800/50 cursor-pointer transition flex items-start gap-2.5 ${
                                        isSelected ? 'bg-slate-800/80 border-l-2 border-emerald-500' : ''
                                      }`}
                                    >
                                      {/* Status Toggle Icon */}
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          handleToggleTaskStatus(task);
                                        }}
                                        title={isClosed ? 'Reopen task' : 'Close task'}
                                        className="mt-0.5 text-slate-500 hover:text-emerald-400 transition flex-shrink-0"
                                      >
                                        {isClosed ? (
                                          <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                                        ) : (
                                          <Circle className="w-4 h-4 hover:text-emerald-400" />
                                        )}
                                      </button>

                                      {/* Task Title & Labels */}
                                      <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-1.5 flex-wrap">
                                          <span
                                            className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded border ${badgeStyle.bg} ${badgeStyle.text} ${badgeStyle.border}`}
                                          >
                                            {task.type}
                                          </span>

                                          <span
                                            className={`text-xs font-medium truncate ${
                                              isClosed ? 'line-through text-slate-500' : 'text-slate-100'
                                            }`}
                                          >
                                            {task.title}
                                          </span>
                                        </div>

                                        {/* Labels and Age Row */}
                                        <div className="mt-1 flex items-center justify-between text-[11px] text-slate-500">
                                          <div className="flex items-center gap-1 flex-wrap">
                                            {task.labels.map((l, lIdx) => (
                                              <span
                                                key={lIdx}
                                                className="text-[10px] text-slate-400 bg-slate-800 px-1.5 py-0.2 rounded"
                                              >
                                                #{l}
                                              </span>
                                            ))}
                                            {task.comments.length > 0 && (
                                              <span className="flex items-center gap-0.5 text-[10px] text-slate-400 ml-1">
                                                <MessageSquare className="w-2.5 h-2.5" />
                                                <span>{task.comments.length}</span>
                                              </span>
                                            )}
                                          </div>

                                          <div className="flex items-center gap-1.5 text-[10px] text-slate-500 flex-shrink-0 ml-2">
                                            {isClosed && (
                                              <span className="font-semibold text-emerald-400/80">
                                                {CLOSED_STATUS_LABELS[task.type]}
                                              </span>
                                            )}
                                            <span>{formatRelativeAge(task.updatedAt)}</span>
                                          </div>
                                        </div>
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>

                {/* Right Pane: Task Detail View (70% in Landscape, Full-screen 100% in Portrait) */}
                <div
                  style={isLandscapeEffective ? { width: '70%', flex: '1 1 0%', minWidth: 0 } : undefined}
                  className={`flex flex-col bg-slate-900 overflow-hidden transition-all min-w-0 h-full ${
                    isLandscapeEffective
                      ? 'flex-1'
                      : selectedTask
                      ? 'w-full'
                      : 'hidden'
                  }`}
                >
                  {selectedTask ? (
                    // SCREEN 3: TASK DETAIL VIEW (BILDSCHIRMFÜLLEND IN PORTRAIT)
                    <div className="flex-1 flex flex-col overflow-hidden w-full">
                      {/* Detail App Bar */}
                      <div className="p-3 border-b border-slate-800 bg-slate-900/90 flex items-center justify-between flex-shrink-0 gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          {/* Back button: always visible in portrait and landscape */}
                          <button
                            onClick={() => guardNavigation(() => setSelectedTask(null))}
                            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition flex-shrink-0 shadow-sm"
                            title="Zurück zur Aufgabenliste"
                          >
                            <ArrowLeft className="w-4 h-4 text-emerald-400" />
                            <span>Zurück</span>
                          </button>

                          <span
                            className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded border ${
                              TYPE_BADGE_COLORS[selectedTask.type].bg
                            } ${TYPE_BADGE_COLORS[selectedTask.type].text} ${
                              TYPE_BADGE_COLORS[selectedTask.type].border
                            }`}
                          >
                            {selectedTask.type}
                          </span>
                          <span className="text-xs font-mono text-slate-400 truncate max-w-[120px] sm:max-w-xs">
                            {selectedTask.id}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 flex-shrink-0">
                          {/* Close / Reopen Toggle Button */}
                          <button
                            onClick={() => handleToggleTaskStatus(selectedTask)}
                            className={`text-xs px-2.5 py-1 rounded-lg font-medium transition flex items-center gap-1 ${
                              selectedTask.status === 'open'
                                ? 'bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700'
                                : 'bg-emerald-950 hover:bg-emerald-900 text-emerald-300 border border-emerald-800'
                            }`}
                          >
                            {selectedTask.status === 'open' ? (
                              <>
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                                <span>Close Task ({CLOSED_STATUS_LABELS[selectedTask.type]})</span>
                              </>
                            ) : (
                              <>
                                <RotateCcw className="w-3.5 h-3.5 text-amber-400" />
                                <span>Reopen Task</span>
                              </>
                            )}
                          </button>

                          {/* Save Changes Button */}
                          {hasUnsavedChanges && (
                            <button
                              onClick={handleSaveTaskEdits}
                              className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs px-3 py-1 rounded-lg font-medium flex items-center gap-1 shadow transition animate-pulse"
                            >
                              <Save className="w-3.5 h-3.5" />
                              <span>Save</span>
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Detail Body (Scrollable) */}
                      <div className="flex-1 overflow-y-auto p-4 space-y-4">
                        {/* Title Input */}
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                            Title
                          </label>
                          <input
                            type="text"
                            value={editTitle}
                            onChange={(e) => setEditTitle(e.target.value)}
                            placeholder="Task title..."
                            className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 transition"
                          />
                        </div>

                        {/* Labels Input */}
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1 flex items-center justify-between">
                            <span>Labels</span>
                            <span className="text-[10px] text-slate-500">Comma separated</span>
                          </label>
                          <div className="flex items-center gap-2">
                            <Tag className="w-4 h-4 text-slate-500 flex-shrink-0" />
                            <input
                              type="text"
                              value={editLabels}
                              onChange={(e) => setEditLabels(e.target.value)}
                              placeholder="e.g. backend, urgent, ui"
                              className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-emerald-500 transition"
                            />
                          </div>
                        </div>

                        {/* Description (Markdown editor & preview) */}
                        <div>
                          <div className="flex items-center justify-between mb-1">
                            <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                              Description (Markdown)
                            </label>
                            <div className="flex items-center gap-1 text-[11px] bg-slate-800 p-0.5 rounded border border-slate-700/60">
                              <button
                                onClick={() => setIsMarkdownPreview(false)}
                                className={`px-2 py-0.5 rounded ${
                                  !isMarkdownPreview ? 'bg-slate-700 text-white font-medium' : 'text-slate-400'
                                }`}
                              >
                                Edit
                              </button>
                              <button
                                onClick={() => setIsMarkdownPreview(true)}
                                className={`px-2 py-0.5 rounded flex items-center gap-1 ${
                                  isMarkdownPreview ? 'bg-slate-700 text-white font-medium' : 'text-slate-400'
                                }`}
                              >
                                <Eye className="w-3 h-3" />
                                <span>Preview</span>
                              </button>
                            </div>
                          </div>

                          {isMarkdownPreview ? (
                            <div
                              className="min-h-[140px] max-h-72 overflow-y-auto p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-200 prose prose-invert prose-xs max-w-none"
                              dangerouslySetInnerHTML={{
                                __html: renderedDescriptionHtml || '<em class="text-slate-500">No description provided</em>',
                              }}
                            />
                          ) : (
                            <textarea
                              rows={5}
                              value={editDescription}
                              onChange={(e) => setEditDescription(e.target.value)}
                              placeholder="Enter Markdown description..."
                              className="w-full bg-slate-950 border border-slate-700 rounded-lg p-3 text-xs text-slate-200 font-mono focus:outline-none focus:border-emerald-500 transition"
                            />
                          )}
                        </div>

                        {/* Optional Image */}
                        {selectedTask.imagePath && (
                          <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 text-xs">
                            <span className="font-semibold text-slate-400 text-[11px] uppercase block mb-1">
                              Image Attachment
                            </span>
                            <span className="font-mono text-emerald-400">{selectedTask.imagePath}</span>
                          </div>
                        )}

                        {/* Comments Section */}
                        <div className="pt-2 border-t border-slate-800">
                          <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-2 flex items-center gap-1">
                            <MessageSquare className="w-3.5 h-3.5" />
                            <span>Comments ({selectedTask.comments.length})</span>
                          </label>

                          <div className="space-y-2 mb-3">
                            {selectedTask.comments.length === 0 ? (
                              <p className="text-xs text-slate-500 italic">No comments yet on this task.</p>
                            ) : (
                              selectedTask.comments.map((c, cIdx) => (
                                <div key={cIdx} className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs">
                                  <p className="text-slate-200 leading-relaxed whitespace-pre-wrap">{c.body}</p>
                                  <span className="text-[10px] text-slate-500 block mt-1">
                                    {c.createdAt} ({formatRelativeAge(c.createdAt)})
                                  </span>
                                </div>
                              ))
                            )}
                          </div>

                          {/* Add Comment Input */}
                          <div className="flex gap-2">
                            <input
                              type="text"
                              value={newCommentInput}
                              onChange={(e) => setNewCommentInput(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') handleAddComment();
                              }}
                              placeholder="Write a comment..."
                              className="flex-1 bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-emerald-500 transition"
                            />
                            <button
                              onClick={handleAddComment}
                              disabled={!newCommentInput.trim()}
                              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-emerald-400 rounded-lg text-xs font-medium flex items-center gap-1 border border-slate-700 transition"
                            >
                              <Send className="w-3 h-3" />
                              <span>Comment</span>
                            </button>
                          </div>
                        </div>

                        {/* Metadata Footer */}
                        <div className="pt-3 border-t border-slate-800 text-[11px] text-slate-500 space-y-1 font-mono">
                          <div className="flex items-center justify-between">
                            <span>Created:</span>
                            <span className="text-slate-400">{selectedTask.createdAt} by {selectedTask.createdBy}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span>Updated:</span>
                            <span className="text-slate-400">{selectedTask.updatedAt} by {selectedTask.updatedBy}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span>Format Version:</span>
                            <span className="text-slate-400">{selectedTask.formatVersion}</span>
                          </div>
                          <div className="pt-1 flex items-center justify-between">
                            <button
                              onClick={() => handleViewBackup(selectedTask.id)}
                              className="text-[10px] text-indigo-400 hover:text-indigo-300 underline"
                            >
                              Inspect .backup/{selectedTask.id}.md.bak
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : (
                    // No Task Selected in Split View
                    <div className="flex-1 flex flex-col items-center justify-center p-8 text-slate-600 text-center">
                      <FileText className="w-12 h-12 mb-2 opacity-30" />
                      <p className="text-sm font-medium text-slate-400">Select a task to view details</p>
                      <p className="text-xs text-slate-600 mt-1 max-w-xs">
                        View rendered markdown, update status, add comments, or edit in place.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: AUTOMATED TEST SUITE */}
        {activeTab === 'tests' && (
          <div className="flex-1 overflow-y-auto p-4 md:p-8 max-w-5xl mx-auto w-full">
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  <span>oloop Automated Test Suite & Fixture Runner</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Enforces step 3 of the brief: runs all valid cases, invalid cases with stated failure reasons, and round-trip identity tests.
                </p>
              </div>

              <button
                onClick={runTests}
                disabled={isRunningTests}
                className="px-3.5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold flex items-center gap-2 shadow transition"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isRunningTests ? 'animate-spin' : ''}`} />
                <span>Re-run All Tests</span>
              </button>
            </div>

            {testSummary && (
              <div className="mb-6 grid grid-cols-3 gap-3">
                <div className="bg-slate-900 border border-slate-800 p-3 rounded-xl text-center">
                  <span className="text-2xl font-black text-white">{testSummary.total}</span>
                  <span className="block text-[11px] text-slate-400 uppercase font-semibold">Total Tests</span>
                </div>
                <div className="bg-slate-900 border border-emerald-900/50 p-3 rounded-xl text-center">
                  <span className="text-2xl font-black text-emerald-400">{testSummary.passed}</span>
                  <span className="block text-[11px] text-emerald-400 uppercase font-semibold">Passed</span>
                </div>
                <div className="bg-slate-900 border border-slate-800 p-3 rounded-xl text-center">
                  <span className={`text-2xl font-black ${testSummary.failed === 0 ? 'text-slate-400' : 'text-rose-400'}`}>
                    {testSummary.failed}
                  </span>
                  <span className="block text-[11px] text-slate-400 uppercase font-semibold">Failed</span>
                </div>
              </div>
            )}

            {/* Test Results Table */}
            <div className="bg-slate-900 rounded-xl border border-slate-800 divide-y divide-slate-800/80 overflow-hidden">
              {testSummary?.results.map((t) => (
                <div key={t.id} className="p-3.5 flex items-start justify-between gap-3 text-xs">
                  <div className="flex items-start gap-2.5">
                    {t.passed ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
                    ) : (
                      <AlertTriangle className="w-4 h-4 text-rose-400 flex-shrink-0 mt-0.5" />
                    )}
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-200">{t.name}</span>
                        <span className="text-[10px] uppercase px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 font-mono">
                          {t.category}
                        </span>
                      </div>
                      <p className={`text-[11px] mt-0.5 ${t.passed ? 'text-slate-400' : 'text-rose-300 font-medium'}`}>
                        {t.message}
                      </p>
                    </div>
                  </div>
                  <span className="text-[10px] font-mono text-slate-500">{t.durationMs}ms</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 3: KOTLIN SOURCES VIEWER */}
        {activeTab === 'code' && (
          <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
            {/* Sidebar with files */}
            <div className="w-full md:w-80 border-r border-slate-800 bg-slate-900/60 p-3 overflow-y-auto flex-shrink-0">
              <div className="flex items-center justify-between mb-3 px-1">
                <span className="text-xs font-bold text-slate-300 uppercase tracking-wider">
                  Android Project Files
                </span>
                <button
                  onClick={handleDownloadAndroidProject}
                  className="text-[11px] text-emerald-400 hover:text-emerald-300 flex items-center gap-1 font-semibold"
                >
                  <Download className="w-3 h-3" />
                  <span>Download Bundle</span>
                </button>
              </div>

              <div className="space-y-1">
                {ANDROID_PROJECT_FILES.map((file) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedCodeFile(file)}
                    className={`w-full text-left p-2 rounded-lg text-xs transition flex flex-col ${
                      selectedCodeFile.path === file.path
                        ? 'bg-indigo-600/20 border border-indigo-500/50 text-indigo-300'
                        : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <span className="font-mono font-medium text-slate-200">{file.name}</span>
                    <span className="text-[10px] text-slate-500 truncate">{file.path}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Code Display Area */}
            <div className="flex-1 flex flex-col overflow-hidden bg-slate-950">
              <div className="p-3 border-b border-slate-800 bg-slate-900/40 flex items-center justify-between">
                <div>
                  <h3 className="text-xs font-mono font-bold text-white">{selectedCodeFile.path}</h3>
                  <p className="text-[11px] text-slate-400">{selectedCodeFile.description}</p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={handleCopyCode}
                    className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded text-xs flex items-center gap-1 transition"
                  >
                    {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copiedCode ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              </div>

              <pre className="flex-1 p-4 font-mono text-xs text-slate-300 overflow-auto bg-slate-950 selection:bg-indigo-900 leading-relaxed">
                <code>{selectedCodeFile.code}</code>
              </pre>
            </div>
          </div>
        )}

        {/* TAB 4: ASSUMPTIONS & ARCHITECTURE DOC */}
        {activeTab === 'docs' && (
          <div className="flex-1 overflow-y-auto p-4 md:p-8 max-w-4xl mx-auto w-full prose prose-invert prose-sm">
            <h1 className="text-xl font-bold text-white mb-2">oloop Android Platform Architecture & Delivery Report</h1>
            <p className="text-slate-400 text-xs">
              Delivered as requested in step 1 of &ldquo;How to deliver&rdquo;: states assumptions, platform specifics, and library choices.
            </p>

            <div className="mt-4 p-4 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-300 space-y-3">
              <h2 className="text-sm font-bold text-emerald-400 uppercase tracking-wider">1. Assumptions & Storage Access Framework (SAF)</h2>
              <ul className="list-disc pl-5 space-y-1.5">
                <li>
                  <strong>Direct file paths (<code>/storage/...</code>) are forbidden on modern Android:</strong> We utilize Android&rsquo;s <code>ACTION_OPEN_DOCUMENT_TREE</code> with <code>takePersistableUriPermission</code>. All directories and markdown files are resolved via <code>DocumentFile</code> trees. This allows seamless operation even on cloud folders like Google Drive.
                </li>
                <li>
                  <strong>Safe & Atomic Writes:</strong> DocumentFile writes in Android SAF do not support POSIX atomic rename on all cloud providers. As specified in the platform notes, the writer creates a backup copy in <code>&lt;project&gt;/.backup/&lt;task-id&gt;.md.bak</code> before overwriting the target file directly using <code>openOutputStream(uri, "wt")</code>.
                </li>
                <li>
                  <strong>SnakeYAML Timestamp Preservation:</strong> Standard YAML parsers convert ISO dates like <code>2026-10-16T12:00:00Z</code> into <code>java.util.Date</code>. In our <code>OloopParser.kt</code>, we configure SnakeYAML with a custom <code>SafeConstructor</code> overriding <code>Tag.TIMESTAMP</code> so all scalar dates strictly remain pure <code>String</code> objects, preventing date alterations on re-writing.
                </li>
                <li>
                  <strong>Last Writer Wins & Concurrent Conflict Detection:</strong> Before every write, the file is re-read from disk/provider. If <code>updatedAt</code> on disk differs from the version loaded into memory, silent overwrite is aborted and a conflict resolution dialog prompts the user to either reload or overwrite.
                </li>
                <li>
                  <strong>Zero Network Access:</strong> As required by oloop, no internet permission is requested in <code>AndroidManifest.xml</code>.
                </li>
              </ul>

              <h2 className="text-sm font-bold text-emerald-400 uppercase tracking-wider pt-2">2. Libraries Used & Rationale</h2>
              <ul className="list-disc pl-5 space-y-1.5">
                <li><strong>androidx.compose.material3:</strong> Modern Material Design 3 design system for dense, scannable lists and adaptive layouts.</li>
                <li><strong>org.yaml:snakeyaml (2.2):</strong> Robust YAML frontmatter parser allowing strict control over scalar types.</li>
                <li><strong>androidx.documentfile:</strong> Standard SAF tree navigation and document stream access.</li>
                <li><strong>io.noties.markwon:</strong> Lightweight markdown renderer without heavyweight WebViews.</li>
                <li><strong>js-yaml & marked (Web client):</strong> Identical pure parser implementation running live inside the interactive AI Studio sandbox.</li>
              </ul>
            </div>
          </div>
        )}
      </main>

      {/* MODAL: New Task */}
      {showNewTaskModal && selectedProject && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-5 shadow-2xl">
            <h3 className="text-base font-bold text-white mb-3">
              New Task in {selectedProject.name}
            </h3>

            <div className="space-y-3 text-xs">
              {/* Type Selector */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase mb-1">Type</label>
                <div className="flex gap-1.5 flex-wrap">
                  {TASK_TYPES.map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setNewTaskType(t)}
                      className={`px-2.5 py-1 rounded-md text-xs font-semibold capitalize transition ${
                        newTaskType === t
                          ? 'bg-emerald-600 text-white shadow-sm'
                          : 'bg-slate-800 text-slate-400 hover:text-white'
                      }`}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              {/* Title */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase mb-1">
                  Title <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  value={newTaskTitle}
                  onChange={(e) => setNewTaskTitle(e.target.value)}
                  placeholder="Task title..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-emerald-500"
                />
              </div>

              {/* Labels */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase mb-1">
                  Labels (comma-separated)
                </label>
                <input
                  type="text"
                  value={newTaskLabels}
                  onChange={(e) => setNewTaskLabels(e.target.value)}
                  placeholder="e.g. storage, saf, ui"
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-emerald-500"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase mb-1">
                  Description (Markdown)
                </label>
                <textarea
                  rows={4}
                  value={newTaskDescription}
                  onChange={(e) => setNewTaskDescription(e.target.value)}
                  placeholder="Markdown description..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg p-3 text-white font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowNewTaskModal(false)}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleCreateTask}
                disabled={!newTaskTitle.trim()}
                className="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-white rounded-lg text-xs font-semibold shadow"
              >
                Create Task
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Settings */}
      {showSettingsModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-5 shadow-2xl">
            <h3 className="text-base font-bold text-white mb-3 flex items-center gap-2">
              <SettingsIcon className="w-4 h-4 text-emerald-400" />
              <span>oloop Settings</span>
            </h3>

            <div className="space-y-4 text-xs">
              {/* Actor */}
              <div className="bg-slate-950 p-3 rounded-xl border border-slate-800">
                <label className="block font-semibold text-slate-200 text-xs mb-1">
                  User Name (Actor)
                </label>
                <p className="text-[11px] text-slate-400 mb-2">
                  Written to <code>createdBy</code> and <code>updatedBy</code> in task frontmatter.
                </p>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={userActor}
                    onChange={(e) => setUserActor(e.target.value)}
                    placeholder="Enter your name..."
                    className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-white focus:outline-none focus:border-emerald-500 text-xs"
                  />
                  <button
                    onClick={() => handleSaveActor(userActor)}
                    className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold"
                  >
                    Save
                  </button>
                </div>
              </div>

              {/* Data Root Folder */}
              <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 space-y-2">
                <label className="block font-semibold text-slate-200 text-xs">
                  Data Root Folder
                </label>
                <p className="text-[11px] text-slate-400">
                  Current: <strong className="text-emerald-400">{rootFolderName}</strong>
                </p>

                <div className="flex flex-col gap-2 pt-1">
                  <button
                    onClick={handlePickDirectory}
                    className="w-full px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium flex items-center justify-center gap-1.5 border border-slate-700"
                  >
                    <FolderOpen className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Pick Real Folder (SAF / FileSystem Access)</span>
                  </button>
                  <button
                    onClick={handleResetToFixtures}
                    className="w-full px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-slate-400 hover:text-slate-200 rounded-lg text-[11px] flex items-center justify-center gap-1 border border-slate-800"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>Reset to Built-in Fixture Workspace</span>
                  </button>
                </div>
              </div>
            </div>

            <div className="mt-5 flex justify-end">
              <button
                onClick={() => setShowSettingsModal(false)}
                className="px-4 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Actor Prompt Required */}
      {showActorModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-sm p-5 shadow-2xl">
            <h3 className="text-base font-bold text-white mb-1 flex items-center gap-2">
              <User className="w-4 h-4 text-emerald-400" />
              <span>Enter Your Name</span>
            </h3>
            <p className="text-xs text-slate-400 mb-3">
              oloop requires a user name (Actor) before saving any tasks, to stamp <code>createdBy</code> and <code>updatedBy</code>.
            </p>

            <input
              type="text"
              autoFocus
              value={actorModalInput}
              onChange={(e) => setActorModalInput(e.target.value)}
              placeholder="Your name (e.g. Sample User)"
              className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-emerald-500 mb-4"
            />

            <div className="flex justify-end gap-2">
              <button
                onClick={() => handleSaveActor(actorModalInput)}
                disabled={!actorModalInput.trim()}
                className="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-white rounded-lg text-xs font-semibold"
              >
                Continue
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Concurrent Change Conflict */}
      {conflictState && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-amber-600/60 rounded-2xl w-full max-w-md p-5 shadow-2xl">
            <h3 className="text-base font-bold text-amber-400 mb-2 flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-amber-400" />
              <span>Concurrent Change Conflict</span>
            </h3>
            <p className="text-xs text-slate-300 leading-relaxed mb-3">
              This task was modified on disk by another process after you loaded it.
            </p>

            <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 text-[11px] font-mono space-y-1 mb-4">
              <div>
                <span className="text-slate-500">Disk updatedAt: </span>
                <span className="text-amber-400">{conflictState.currentDiskUpdatedAt}</span>
              </div>
              <div>
                <span className="text-slate-500">Your loaded updatedAt: </span>
                <span className="text-slate-400">{conflictState.loadedUpdatedAt}</span>
              </div>
            </div>

            <div className="flex justify-end gap-2 text-xs">
              <button
                onClick={handleResolveConflictReload}
                className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-medium"
              >
                Reload from Disk
              </button>
              <button
                onClick={handleResolveConflictOverwrite}
                className="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-500 text-white rounded-lg font-semibold"
              >
                Overwrite Disk
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Unsaved Changes Guard */}
      {showUnsavedChangesModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-sm p-5 shadow-2xl">
            <h3 className="text-base font-bold text-white mb-2">Unsaved Changes</h3>
            <p className="text-xs text-slate-400 mb-4">
              You have modified this task without saving. Navigating away will lose your unsaved edits.
            </p>
            <div className="flex justify-end gap-2 text-xs">
              <button
                onClick={() => setShowUnsavedChangesModal(false)}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg"
              >
                Keep Editing
              </button>
              <button
                onClick={() => {
                  setShowUnsavedChangesModal(false);
                  if (pendingNavigation) {
                    const nav = pendingNavigation;
                    setPendingNavigation(null);
                    nav();
                  }
                }}
                className="px-3.5 py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg font-semibold"
              >
                Discard Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Backup File Viewer */}
      {viewingBackup && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-5 shadow-2xl flex flex-col max-h-[80vh]">
            <h3 className="text-sm font-bold text-white mb-1">
              Backup Copy: .backup/{viewingBackup.taskId}.md.bak
            </h3>
            <p className="text-[11px] text-slate-400 mb-3">
              Generated automatically before the previous overwrite according to the oloop safety contract.
            </p>
            <pre className="flex-1 overflow-auto bg-slate-950 p-3 rounded-lg border border-slate-800 font-mono text-[11px] text-slate-300 whitespace-pre">
              {viewingBackup.content}
            </pre>
            <div className="mt-4 flex justify-end">
              <button
                onClick={() => setViewingBackup(null)}
                className="px-4 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
