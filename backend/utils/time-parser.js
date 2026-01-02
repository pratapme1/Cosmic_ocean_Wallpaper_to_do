const chrono = require('chrono-node');

/**
 * Ported from PWA logic: Uses chrono-node to extract dates and times.
 * Returns the cleaned title, the parsed due_date, and estimate_minutes.
 */
function parseTimeEstimate(rawTitle) {
  if (!rawTitle || typeof rawTitle !== 'string') {
    return { title: 'New Task', dueDate: null, estimateMinutes: null };
  }
  let title = rawTitle.trim();
  let dueDate = null;
  let estimateMinutes = null;

  // 1. Extract Duration (e.g., "30m", "1h", "in 30m") - Pre-processing
  const durationPattern = /\s+(\d+(?:\.\d+)?)h\s*(\d+)?m?$/i;
  const minPattern = /\s+(?:in\s+)?(\d+)m(?:in)?$/i; // Handles "30m", "30min", "in 30m"
  const hourPattern = /\s+(?:in\s+)?(\d+(?:\.\d+)?)h$/i; // Handles "1h", "in 1h"

  let match;
  if ((match = title.match(durationPattern))) {
    const hours = parseFloat(match[1]);
    const mins = match[2] ? parseInt(match[2]) : 0;
    estimateMinutes = (hours * 60) + mins;
    title = title.replace(durationPattern, '').trim();
  } else if ((match = title.match(minPattern))) {
    estimateMinutes = parseInt(match[1]);
    title = title.replace(minPattern, '').trim();
  } else if ((match = title.match(hourPattern))) {
    estimateMinutes = Math.round(parseFloat(match[1]) * 60);
    title = title.replace(hourPattern, '').trim();
  }

  // 2. Extract Date/Time using Chrono
  const results = chrono.parse(title);
  if (results.length > 0) {
    dueDate = results[0].start.date();
    // Clean title by removing the date part found by chrono
    title = title.replace(results[0].text, '').trim();
  }

  return {
    title: title || "New Task", // Fallback if title became empty
    dueDate,
    estimateMinutes
  };
}

module.exports = { parseTimeEstimate };