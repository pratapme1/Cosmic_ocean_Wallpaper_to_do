const { toZonedTime, formatInTimeZone } = require('date-fns-tz');
const { startOfDay, differenceInHours, differenceInMinutes, isBefore, format } = require('date-fns');

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Get full due datetime from task components
 * WARNING: Returns a Date object where the components match the User's Local Time
 * (e.g. if task is due 17:00, this Date object will say 17:00 in system local time)
 * This is intended to be compared against a Zoned "Now".
 */
function getDueDateTime(task) {
  if (!task.due_date) return null;

  // Parse the due date string (YYYY-MM-DD or ISO)
  // We assume this date string is in "User Local Time"
  let dueDate = new Date(task.due_date);

  // If due_time is provided, combine with due_date
  if (task.due_time) {
    const [hours, minutes, seconds] = task.due_time.split(':').map(Number);
    dueDate.setHours(hours || 0, minutes || 0, seconds || 0, 0);
  } else {
    // Default to end of day? Or start? 
    // Classic logic for "Due Today" usually implies strictly date matching.
    // We'll set it to midnight for date comparisons, but for sorting it might matter.
    // Let's keep existing behavior: 00:00:00 if undefined, but logic handles day matching.
    dueDate.setHours(0, 0, 0, 0);
  }

  return dueDate;
}

/**
 * Check if task is overdue using User's Timezone
 * @param {Object} task
 * @param {string} userTimezone - e.g. 'Asia/Tokyo'
 */
function isOverdue(task, userTimezone = 'UTC') {
  const due = getDueDateTime(task);
  if (!due) return false;

  const now = new Date();
  const userNow = toZonedTime(now, userTimezone);

  // If task has specific time, compare directly
  if (task.due_time) {
    return isBefore(due, userNow);
  }

  // If task has NO time, it's overdue if the date is strictly BEFORE user's today
  // e.g. Due yesterday
  const userTodayStart = startOfDay(userNow);
  const dueStart = startOfDay(due);

  return isBefore(dueStart, userTodayStart);
}

/**
 * Check if task is due today using User's Timezone
 */
function isDueToday(task, userTimezone = 'UTC') {
  const due = getDueDateTime(task);
  if (!due) return false;

  const now = new Date();
  // Safe comparison using formatInTimeZone
  const userDateStr = formatInTimeZone(now, userTimezone, 'yyyy-MM-dd');
  const taskDateStr = format(due, 'yyyy-MM-dd'); // due is already in "User Time" components

  return userDateStr === taskDateStr;
}

function isDueWithin(task, hours, userTimezone = 'UTC') {
  const due = getDueDateTime(task);
  if (!due) return false;

  const now = new Date();
  const userNow = toZonedTime(now, userTimezone);

  const diffHours = differenceInHours(due, userNow);
  return diffHours >= 0 && diffHours < hours;
}

/**
 * Get minutes until due (negative = overdue)
 */
function getMinutesUntilDue(task, userTimezone = 'UTC') {
  const due = getDueDateTime(task);
  if (!due) return Infinity;

  const now = new Date();
  const userNow = toZonedTime(now, userTimezone);

  return differenceInMinutes(due, userNow);
}

function daysSince(dateStr) {
  if (!dateStr) return 0;
  const date = new Date(dateStr);
  const now = new Date();
  const diffTime = Math.abs(now - date);
  return Math.ceil(diffTime / ONE_DAY_MS);
}

/**
 * Calculates a priority score for a task.
 * Higher score = Higher priority.
 * @param {Object} task 
 * @param {Object} context - { location: 'home'|'work', timeOfDay: 'morning'|'evening', timezone: 'UTC' }
 */
function calculateTaskScore(task, context = {}) {
  let score = 0;
  const timezone = context.timezone || 'UTC';

  // 1. Critical Deadlines (Timezone Aware!)
  if (isOverdue(task, timezone)) {
    score += 1000;
  } else if (isDueToday(task, timezone)) {
    score += 500;
  } else if (isDueWithin(task, 48, timezone)) {
    score += 200;
  }

  // 2. User Priority (1=High, 2=Medium, 3=Low)
  const priorityScore = {
    1: 300,  // High
    2: 200,  // Medium
    3: 100   // Low
  };
  score += priorityScore[task.priority] || 200;

  // 3. Task Age
  score += Math.min(daysSince(task.created_at), 14);

  // 4. Context Awareness (Phase 3)
  if (context.location && task.context_location) {
    if (context.location === task.context_location) {
      score += 300;
    }
  }

  if (context.timeOfDay && task.context_time) {
    if (context.timeOfDay === task.context_time) {
      score += 150;
    }
  }

  return score;
}

/**
 * Sorts tasks by score (Descending) and returns the top "One Thing".
 * @param {Array} tasks
 * @param {Object} context - { location, timeOfDay, timezone }
 */
function prioritizeTasks(tasks, context = {}) {
  const timezone = context.timezone || 'UTC';

  const scoredTasks = tasks.map(t => ({
    ...t,
    score: calculateTaskScore(t, context),
    _minutesUntilDue: getMinutesUntilDue(t, timezone)
  }));

  // Sort by score descending, then by due time ascending
  scoredTasks.sort((a, b) => {
    // Primary: Higher score first
    if (b.score !== a.score) {
      return b.score - a.score;
    }
    // Secondary: Sooner due time first (smaller minutes = higher priority)
    return a._minutesUntilDue - b._minutesUntilDue;
  });

  return scoredTasks;
}

module.exports = { calculateTaskScore, prioritizeTasks, daysSince, isDueToday, isOverdue };
