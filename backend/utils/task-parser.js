/**
 * Comprehensive NLP Task Parser
 *
 * Extracts structured metadata from natural language task descriptions.
 * Based on cosmic-ocean-intelligence-spec requirements.
 *
 * Features:
 * - Context tags (@home, @work, @gym, etc.)
 * - Energy level detection (high, medium, low)
 * - Priority inference (P1, P2, P3)
 * - Category detection (work, personal, health, etc.)
 * - Recurring pattern recognition
 * - Time context (morning, afternoon, evening)
 * - Duration parsing (30m, 1h, quick, long)
 * - Date/time parsing (chrono-node integration)
 */

const chrono = require('chrono-node');
const {
  CONTEXT_TAGS,
  HIGH_ENERGY_KEYWORDS,
  LOW_ENERGY_KEYWORDS,
  HIGH_PRIORITY_KEYWORDS,
  LOW_PRIORITY_KEYWORDS,
  CATEGORY_PATTERNS,
  RECURRING_PATTERNS,
  TIME_CONTEXT_PATTERNS,
  DURATION_KEYWORDS
} = require('./nlp-patterns');

/**
 * Main task parser function
 * @param {string} rawTitle - The raw task input from user
 * @returns {Object} Parsed task metadata
 */
function parseTask(rawTitle) {
  if (!rawTitle || typeof rawTitle !== 'string') {
    return createEmptyResult();
  }

  const original = rawTitle.trim();
  let workingTitle = original;
  let confidence = 1.0;

  // Track what we extracted
  const extractions = [];

  // 1. Extract context tags
  const { contexts, cleanedText: afterContexts } = extractContextTags(workingTitle);
  workingTitle = afterContexts;
  if (contexts.length > 0) {
    extractions.push('context');
  }

  // 2. Extract recurring patterns (before chrono to avoid conflicts)
  const { recurring, cleanedText: afterRecurring } = extractRecurring(workingTitle);
  workingTitle = afterRecurring;
  if (recurring) {
    extractions.push('recurring');
  }

  // 3. Extract time context (morning, afternoon, evening)
  const { timeContext, suggestedHour, cleanedText: afterTimeContext } = extractTimeContext(workingTitle);
  workingTitle = afterTimeContext;
  if (timeContext) {
    extractions.push('timeContext');
  }

  // 4. Extract duration (including natural language like "quick", "long")
  const { estimateMinutes, cleanedText: afterDuration } = extractDuration(workingTitle);
  workingTitle = afterDuration;
  if (estimateMinutes !== null) {
    extractions.push('duration');
  }

  // 5. Extract date/time using chrono-node
  const { dueDate, dueTime, cleanedText: afterDateTime } = extractDateTime(workingTitle, suggestedHour);
  workingTitle = afterDateTime;
  if (dueDate) {
    extractions.push('date');
  }

  // 6. Detect energy level
  const energy = detectEnergy(original);
  if (energy !== 'medium') {
    extractions.push('energy');
  }

  // 7. Infer priority
  const { priority, priorityReason } = inferPriority(original, dueDate);
  if (priority !== 2) {
    extractions.push('priority');
  }

  // 8. Detect category
  const category = detectCategory(original, contexts);
  if (category !== 'general') {
    extractions.push('category');
  }

  // 9. Clean up title
  const title = cleanTitle(workingTitle);

  // 10. Calculate confidence based on how much we extracted
  confidence = calculateConfidence(extractions, title, original);

  return {
    title,
    rawTitle: original,
    dueDate,
    dueTime,
    estimateMinutes,
    priority,
    priorityReason,
    energy,
    context: contexts,
    category,
    recurring,
    timeContext,
    confidence,
    extractedFields: extractions
  };
}

/**
 * Extract @context tags from the input
 */
function extractContextTags(text) {
  const contexts = [];
  let cleanedText = text;

  for (const tag of CONTEXT_TAGS) {
    const regex = new RegExp(`\\s*${tag}\\b`, 'gi');
    if (regex.test(cleanedText)) {
      contexts.push(tag.toLowerCase());
      cleanedText = cleanedText.replace(regex, ' ');
    }
  }

  return {
    contexts: [...new Set(contexts)], // Remove duplicates
    cleanedText: cleanedText.trim()
  };
}

/**
 * Extract recurring patterns
 */
function extractRecurring(text) {
  let cleanedText = text;

  for (const { pattern, interval, dayOfWeek, dayOfMonth } of RECURRING_PATTERNS) {
    const match = text.match(pattern);
    if (match) {
      cleanedText = cleanedText.replace(pattern, '').trim();

      // Handle dynamic day of month extraction
      let actualDayOfMonth = dayOfMonth;
      if (dayOfMonth === '$1' && match[1]) {
        actualDayOfMonth = parseInt(match[1]);
      }

      return {
        recurring: {
          interval,
          dayOfWeek,
          dayOfMonth: actualDayOfMonth
        },
        cleanedText
      };
    }
  }

  return { recurring: null, cleanedText };
}

/**
 * Extract time context (morning, afternoon, evening)
 */
function extractTimeContext(text) {
  for (const { pattern, timeContext, suggestedHour } of TIME_CONTEXT_PATTERNS) {
    if (pattern.test(text)) {
      return {
        timeContext,
        suggestedHour,
        cleanedText: text.replace(pattern, '').trim()
      };
    }
  }

  return { timeContext: null, suggestedHour: null, cleanedText: text };
}

/**
 * Extract duration from text (numeric and natural language)
 */
function extractDuration(text) {
  let cleanedText = text;
  let estimateMinutes = null;

  // 1. Check NUMERIC patterns FIRST (before keywords like "quick")
  // Pattern: "1h30m", "1.5h", "90m", "90 minutes", "1 hour"
  const numericPatterns = [
    { regex: /\b(\d+(?:\.\d+)?)\s*h(?:ours?)?\s*(\d+)?\s*m(?:in(?:ute)?s?)?\b/i, handler: (m) => (parseFloat(m[1]) * 60) + (m[2] ? parseInt(m[2]) : 0) },
    { regex: /\b(\d+)\s*m(?:in(?:ute)?s?)?\b/i, handler: (m) => parseInt(m[1]) },
    { regex: /\b(\d+(?:\.\d+)?)\s*h(?:ours?)?\b/i, handler: (m) => Math.round(parseFloat(m[1]) * 60) },
    { regex: /\b(\d+)\s*minutes?\b/i, handler: (m) => parseInt(m[1]) },
    { regex: /\b(\d+)\s*hours?\b/i, handler: (m) => parseInt(m[1]) * 60 }
  ];

  for (const { regex, handler } of numericPatterns) {
    const match = cleanedText.match(regex);
    if (match) {
      estimateMinutes = handler(match);
      cleanedText = cleanedText.replace(regex, '').trim();
      break;
    }
  }

  // 2. If no numeric pattern found, check natural language keywords
  if (estimateMinutes === null) {
    const lowerText = text.toLowerCase();
    for (const [keyword, minutes] of Object.entries(DURATION_KEYWORDS)) {
      const regex = new RegExp(`\\b${keyword}\\b`, 'gi');
      if (regex.test(lowerText)) {
        estimateMinutes = minutes;
        cleanedText = cleanedText.replace(regex, '').trim();
        break;
      }
    }
  }

  return { estimateMinutes, cleanedText };
}

/**
 * Extract date/time using chrono-node
 */
function extractDateTime(text, suggestedHour = null) {
  // Use custom reference date for chrono
  const refDate = new Date();

  const results = chrono.parse(text, refDate, { forwardDate: true });

  if (results.length > 0) {
    const result = results[0];
    let date = result.start.date();

    // If we have a suggested hour from time context and chrono didn't get a specific time
    if (suggestedHour !== null && !result.start.isCertain('hour')) {
      date.setHours(suggestedHour, 0, 0, 0);
    }

    // Extract time string if available
    let dueTime = null;
    if (result.start.isCertain('hour')) {
      const hours = date.getHours();
      const minutes = date.getMinutes();
      dueTime = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
    }

    // Clean the text by removing the date portion
    const cleanedText = text.replace(result.text, '').trim();

    return { dueDate: date, dueTime, cleanedText };
  }

  return { dueDate: null, dueTime: null, cleanedText: text };
}

/**
 * Detect energy level from keywords
 */
function detectEnergy(text) {
  const lowerText = text.toLowerCase();

  // Check high energy keywords
  for (const keyword of HIGH_ENERGY_KEYWORDS) {
    if (lowerText.includes(keyword)) {
      return 'high';
    }
  }

  // Check low energy keywords
  for (const keyword of LOW_ENERGY_KEYWORDS) {
    if (lowerText.includes(keyword)) {
      return 'low';
    }
  }

  return 'medium';
}

/**
 * Infer priority from keywords and due date
 */
function inferPriority(text, dueDate) {
  const lowerText = text.toLowerCase();

  // Check high priority keywords
  for (const keyword of HIGH_PRIORITY_KEYWORDS) {
    if (lowerText.includes(keyword)) {
      return { priority: 1, priorityReason: `Contains "${keyword}"` };
    }
  }

  // Check low priority keywords
  for (const keyword of LOW_PRIORITY_KEYWORDS) {
    if (lowerText.includes(keyword)) {
      return { priority: 3, priorityReason: `Contains "${keyword}"` };
    }
  }

  // Check if due date is very soon (within 24 hours)
  if (dueDate) {
    const hoursUntilDue = (dueDate - new Date()) / (1000 * 60 * 60);
    if (hoursUntilDue < 0) {
      return { priority: 1, priorityReason: 'Overdue' };
    }
    if (hoursUntilDue < 24) {
      return { priority: 1, priorityReason: 'Due within 24 hours' };
    }
    if (hoursUntilDue < 72) {
      return { priority: 2, priorityReason: 'Due within 3 days' };
    }
  }

  return { priority: 2, priorityReason: 'Default priority' };
}

/**
 * Detect category from keywords and context tags
 */
function detectCategory(text, contexts) {
  const lowerText = text.toLowerCase();
  const scores = {};

  // Check each category's keywords
  for (const [category, keywords] of Object.entries(CATEGORY_PATTERNS)) {
    scores[category] = 0;
    for (const keyword of keywords) {
      if (lowerText.includes(keyword.toLowerCase())) {
        scores[category]++;
      }
    }
  }

  // Find the category with highest score
  let maxScore = 0;
  let detectedCategory = 'general';

  for (const [category, score] of Object.entries(scores)) {
    if (score > maxScore) {
      maxScore = score;
      detectedCategory = category;
    }
  }

  // Only return category if we have at least one match
  return maxScore > 0 ? detectedCategory : 'general';
}

/**
 * Clean up the final title
 */
function cleanTitle(text) {
  // Remove extra whitespace
  let title = text.replace(/\s+/g, ' ').trim();

  // Remove trailing/leading punctuation
  title = title.replace(/^[,:;.\-!?]+|[,:;.\-!?]+$/g, '').trim();

  // Capitalize first letter
  if (title.length > 0) {
    title = title.charAt(0).toUpperCase() + title.slice(1);
  }

  // Fallback if empty
  return title || 'New Task';
}

/**
 * Calculate confidence score based on extractions
 */
function calculateConfidence(extractions, title, original) {
  let confidence = 1.0;

  // Reduce confidence if title is too short
  if (title.length < 3) {
    confidence -= 0.3;
  }

  // Reduce confidence if title is same as original (nothing extracted)
  if (title === original && extractions.length === 0) {
    confidence -= 0.1;
  }

  // Increase confidence for each extraction
  confidence += extractions.length * 0.05;

  // Cap at 1.0
  return Math.min(1.0, Math.max(0.0, confidence));
}

/**
 * Create empty result for invalid input
 */
function createEmptyResult() {
  return {
    title: 'New Task',
    rawTitle: '',
    dueDate: null,
    dueTime: null,
    estimateMinutes: null,
    priority: 2,
    priorityReason: 'Default priority',
    energy: 'medium',
    context: [],
    category: 'general',
    recurring: null,
    timeContext: null,
    confidence: 0.0,
    extractedFields: []
  };
}

/**
 * Legacy compatibility function - wraps parseTask for backward compatibility
 * with the old time-parser.js interface
 */
function parseTimeEstimate(rawTitle) {
  const result = parseTask(rawTitle);
  return {
    title: result.title,
    dueDate: result.dueDate,
    estimateMinutes: result.estimateMinutes
  };
}

module.exports = {
  parseTask,
  parseTimeEstimate, // Backward compatibility
  extractContextTags,
  extractRecurring,
  extractTimeContext,
  extractDuration,
  extractDateTime,
  detectEnergy,
  inferPriority,
  detectCategory
};
