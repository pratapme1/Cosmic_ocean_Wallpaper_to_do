const ONE_DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Get full due datetime by combining due_date and due_time
 */
function getDueDateTime(task) {
  if (!task.due_date) return null;

  // Parse the date (could be YYYY-MM-DD string or ISO string)
  let dueDate = new Date(task.due_date);

  // If due_time is provided, combine with due_date
  if (task.due_time) {
    const [hours, minutes, seconds] = task.due_time.split(':').map(Number);
    dueDate.setHours(hours || 0, minutes || 0, seconds || 0, 0);
  }

  return dueDate;
}

function isOverdue(task) {
  const due = getDueDateTime(task);
  if (!due) return false;
  return due < new Date();
}

function isDueToday(task) {
  const due = getDueDateTime(task);
  if (!due) return false;
  const now = new Date();
  return due.toDateString() === now.toDateString();
}

function isDueWithin(task, hours) {
  const due = getDueDateTime(task);
  if (!due) return false;
  const now = new Date();
  const diffMs = due - now;
  return diffMs > 0 && diffMs < (hours * 60 * 60 * 1000);
}

/**
 * Get minutes until due (negative = overdue)
 */
function getMinutesUntilDue(task) {
  const due = getDueDateTime(task);
  if (!due) return Infinity; // No due date = lowest priority
  return (due - new Date()) / (1000 * 60);
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
 * @param {Object} context - { location: 'home'|'work', timeOfDay: 'morning'|'evening' }
 */
function calculateTaskScore(task, context = {}) {
  let score = 0;

  // 1. Critical Deadlines
  if (isOverdue(task)) {
    score += 1000;
  } else if (isDueToday(task)) {
    score += 500;
  } else if (isDueWithin(task, 48)) {
    score += 200;
  }

  // 2. User Priority (1=High, 2=Medium, 3=Low)
  // Convert to score: Higher priority (lower number) = higher score
  // Priority 1 (High) -> 300 points
  // Priority 2 (Medium) -> 200 points
  // Priority 3 (Low) -> 100 points
  const priorityScore = {
    1: 300,  // High
    2: 200,  // Medium
    3: 100   // Low
  };
  score += priorityScore[task.priority] || 200; // Default to Medium if not set

  // 3. Task Age
  score += Math.min(daysSince(task.created_at), 14);

  // 4. Context Awareness (Phase 3)
  if (context.location && task.context_location) {
    if (context.location === task.context_location) {
      score += 300; // Significant boost for location match
    }
  }

  if (context.timeOfDay && task.context_time) {
    if (context.timeOfDay === task.context_time) {
      score += 150; // Moderate boost for time match
    }
  }

  return score;
}

/**
 * Sorts tasks by score (Descending) and returns the top "One Thing".
 * @param {Array} tasks
 * @param {Object} context - { location, timeOfDay }
 */
function prioritizeTasks(tasks, context = {}) {
  const scoredTasks = tasks.map(t => ({
    ...t,
    score: calculateTaskScore(t, context),
    _minutesUntilDue: getMinutesUntilDue(t)  // For tie-breaking
  }));

  // Sort by score descending, then by due time ascending (sooner = higher priority)
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

module.exports = { calculateTaskScore, prioritizeTasks, daysSince };
