/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export function formatRelativeAge(isoTimestamp: string): string {
  try {
    const then = new Date(isoTimestamp).getTime();
    if (isNaN(then)) return isoTimestamp;
    const now = Date.now();
    const diffMs = Math.max(0, now - then);

    const seconds = Math.floor(diffMs / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (seconds < 60) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days === 1) return '1 day ago';
    if (days < 30) return `${days} days ago`;
    const months = Math.floor(days / 30);
    if (months < 12) return `${months}mo ago`;
    const years = Math.floor(days / 365);
    return `${years}y ago`;
  } catch {
    return isoTimestamp;
  }
}

export type DateBucket = 'Today' | 'Yesterday' | 'This week' | 'This month' | 'Older';

export function getDateBucket(isoTimestamp: string): DateBucket {
  try {
    const target = new Date(isoTimestamp);
    if (isNaN(target.getTime())) return 'Older';

    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const yesterdayStart = new Date(todayStart);
    yesterdayStart.setDate(yesterdayStart.getDate() - 1);

    const weekStart = new Date(todayStart);
    // Start of current week (Monday)
    const day = todayStart.getDay();
    const diffToMonday = (day === 0 ? -6 : 1) - day;
    weekStart.setDate(weekStart.getDate() + diffToMonday);

    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

    if (target >= todayStart) return 'Today';
    if (target >= yesterdayStart) return 'Yesterday';
    if (target >= weekStart) return 'This week';
    if (target >= monthStart) return 'This month';
    return 'Older';
  } catch {
    return 'Older';
  }
}

export const DATE_BUCKET_ORDER: DateBucket[] = [
  'Today',
  'Yesterday',
  'This week',
  'This month',
  'Older',
];
