/**
 * Privacy Filter Service
 * Epic 10 - Phase 1 - Task 2
 *
 * Filters and transforms tasks for wallpaper display based on privacy settings.
 *
 * Privacy Levels:
 * - public: Show full task title (default)
 * - category: Show "{Category} task" instead of title
 * - initials: Show first character + "..."
 * - hidden: Don't show on wallpaper at all
 * - custom: Show privacy_display field
 *
 * User Preferences:
 * - hide_all_tasks_mode: If true, show no tasks on wallpaper
 * - auto_hide_work_tasks: If true, hide work category tasks outside work hours
 * - work_hours_start/end: Define work hours for auto-hide
 */

/**
 * Capitalize first letter of string
 * @param {string} str
 * @returns {string}
 */
function capitalize(str) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Check if current time is within work hours
 * @param {string} workStart - Work hours start (e.g., '09:00')
 * @param {string} workEnd - Work hours end (e.g., '17:00')
 * @param {string} timezone - User's timezone
 * @returns {boolean}
 */
function isWithinWorkHours(workStart, workEnd, timezone = 'UTC') {
  try {
    const now = new Date();

    // Get current time in user's timezone
    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });

    const currentTime = formatter.format(now);
    const [currentHour, currentMinute] = currentTime.split(':').map(Number);
    const currentMinutes = currentHour * 60 + currentMinute;

    // Parse work hours
    const [startHour, startMinute] = (workStart || '09:00').toString().split(':').map(Number);
    const [endHour, endMinute] = (workEnd || '17:00').toString().split(':').map(Number);

    const startMinutes = startHour * 60 + (startMinute || 0);
    const endMinutes = endHour * 60 + (endMinute || 0);

    return currentMinutes >= startMinutes && currentMinutes < endMinutes;
  } catch (err) {
    console.error('[PrivacyFilter] Error checking work hours:', err.message);
    return true; // Default to showing tasks if time check fails
  }
}

/**
 * Get display title based on privacy level
 * @param {object} task - Task object
 * @returns {string|null} Display title or null if hidden
 */
function getPrivacyDisplayTitle(task) {
  // If not marked private, show full title
  if (!task.is_private) {
    return task.title;
  }

  const level = task.privacy_level || 'public';

  switch (level) {
    case 'public':
      return task.title;

    case 'category':
      const category = task.category || 'General';
      return `${capitalize(category)} task`;

    case 'initials':
      if (!task.title || task.title.length === 0) {
        return 'Task';
      }
      return task.title.charAt(0).toUpperCase() + '...';

    case 'hidden':
      return null; // Will be filtered out

    case 'custom':
      return task.privacy_display || 'Private task';

    default:
      return task.title;
  }
}

/**
 * Filter and transform tasks for wallpaper display
 * @param {array} tasks - Array of task objects
 * @param {object} userPrefs - User privacy preferences
 * @param {string} timezone - User's timezone
 * @returns {array} Filtered and transformed tasks
 */
function filterTasksForWallpaper(tasks, userPrefs = {}, timezone = 'UTC') {
  if (!tasks || !Array.isArray(tasks)) {
    return [];
  }

  // Check master switch - hide all tasks
  if (userPrefs.hide_all_tasks_mode === true) {
    console.log('[PrivacyFilter] hide_all_tasks_mode enabled - returning empty array');
    return [];
  }

  // Check auto-hide work tasks setting
  const autoHideWork = userPrefs.auto_hide_work_tasks === true;
  const inWorkHours = autoHideWork ?
    isWithinWorkHours(userPrefs.work_hours_start, userPrefs.work_hours_end, timezone) :
    true;

  const filteredTasks = tasks
    .map(task => {
      // Skip null/undefined tasks
      if (!task) return null;

      // Auto-hide work tasks outside work hours
      if (autoHideWork && task.category === 'work' && !inWorkHours) {
        console.log(`[PrivacyFilter] Auto-hiding work task outside work hours: ${task.title}`);
        return null;
      }

      // Get privacy-filtered display title
      const displayTitle = getPrivacyDisplayTitle(task);

      // If title is null (hidden), skip this task
      if (displayTitle === null) {
        console.log(`[PrivacyFilter] Hiding task with privacy_level=hidden: ${task.title}`);
        return null;
      }

      // Return task with display title
      return {
        ...task,
        displayTitle: displayTitle,
        originalTitle: task.title // Keep original for reference
      };
    })
    .filter(task => task !== null);

  console.log(`[PrivacyFilter] Filtered ${tasks.length} tasks to ${filteredTasks.length} for display`);

  return filteredTasks;
}

/**
 * Get privacy stats for debugging/logging
 * @param {array} tasks - Original tasks
 * @param {array} filteredTasks - Filtered tasks
 * @returns {object} Stats object
 */
function getPrivacyStats(tasks, filteredTasks) {
  const original = tasks || [];
  const filtered = filteredTasks || [];

  const stats = {
    totalTasks: original.length,
    displayedTasks: filtered.length,
    hiddenTasks: original.length - filtered.length,
    byPrivacyLevel: {
      public: 0,
      category: 0,
      initials: 0,
      hidden: 0,
      custom: 0
    }
  };

  original.forEach(task => {
    if (task && task.is_private) {
      const level = task.privacy_level || 'public';
      if (stats.byPrivacyLevel.hasOwnProperty(level)) {
        stats.byPrivacyLevel[level]++;
      }
    }
  });

  return stats;
}

/**
 * Apply default privacy level from user preferences to new task
 * @param {object} task - Task object
 * @param {object} userPrefs - User preferences
 * @returns {object} Task with default privacy applied
 */
function applyDefaultPrivacy(task, userPrefs = {}) {
  if (!task) return task;

  // Only apply defaults if not explicitly set
  if (task.privacy_level === undefined || task.privacy_level === null) {
    const defaultLevel = userPrefs.default_privacy_level || 'public';
    task.privacy_level = defaultLevel;

    // If default is not public, mark as private
    if (defaultLevel !== 'public') {
      task.is_private = true;
    }
  }

  return task;
}

module.exports = {
  filterTasksForWallpaper,
  getPrivacyDisplayTitle,
  isWithinWorkHours,
  getPrivacyStats,
  applyDefaultPrivacy
};
