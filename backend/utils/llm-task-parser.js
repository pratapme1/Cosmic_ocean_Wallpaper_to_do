/**
 * LLM Task Parser - Claude-powered natural language task parsing
 *
 * Handles complex task inputs like:
 * - "Email manager by 5pm tomorrow about budget review"
 * - "Call mom urgently she's in hospital"
 * - "Workout in 30 minutes high energy"
 *
 * Features:
 * - Semantic understanding via Claude Haiku
 * - Hallucination validation (strips invented dates/times)
 * - Graceful fallback to pattern-based parser
 * - Rate limiting (handled at endpoint level)
 *
 * @module utils/llm-task-parser
 */

const Anthropic = require('@anthropic-ai/sdk');
const { parseTask } = require('./task-parser'); // Fallback parser

// Initialize Claude client (lazy-loaded)
let anthropic = null;

/**
 * Initialize Claude client (called on first use)
 */
function initializeClaude() {
  if (!anthropic && process.env.ANTHROPIC_API_KEY) {
    anthropic = new Anthropic({
      apiKey: process.env.ANTHROPIC_API_KEY,
    });
  }
  return !!anthropic;
}

/**
 * Build LLM prompt with strict anti-hallucination rules
 *
 * @param {string} input - User's task input
 * @param {object} context - Current date/time context
 * @returns {string} - Formatted prompt
 */
function buildPrompt(input, context) {
  const { today, currentTime, dayOfWeek } = context;

  return `Parse this task and extract structured data.

INPUT: "${input}"
TODAY: ${today} (${dayOfWeek})
CURRENT TIME: ${currentTime}

Extract these fields:

1. task: The action/task description (remove date/time/priority words)
2. dueDate: YYYY-MM-DD format. Calculate relative dates from TODAY (${today}).
   - Only set if a date is mentioned (today, tomorrow, yesterday, weekday, "next week", explicit date, etc.)
   - Weekday: TODAY is ${dayOfWeek}. If input mentions ${dayOfWeek}, use ${today}. Otherwise find next occurrence.
   - "in X minutes/hours" → set to today (or tomorrow if crosses midnight)
   - If no date mentioned → null
3. dueTime: HH:MM 24-hour format
   - "at 3pm" → "15:00", "in 30 min" → add 30 min to ${currentTime}
   - If no time mentioned → null
4. estimateMinutes: Task duration only (NOT "in X minutes" - that's due time)
   - "30 minute meeting" → 30
5. priority: 1=urgent/now/within 2hrs, 2=normal (DEFAULT), 3=someday/distant future
6. category: work|personal|health|finance|learning|social|errands|general
7. energyLevel: high|medium|low (default: medium)
8. contextTags: Array of @mentions like ["@home", "@office"]
9. isRecurring: true if "every day", "weekly", etc.
10. recurringPattern: daily|weekly|monthly|null

IMPORTANT:
- Do NOT invent dates/times not in the input
- "in X minutes" is a DUE TIME, not duration
- Default priority is 2 unless urgent or explicitly low priority

Respond with JSON only:
{
  "task": "string",
  "dueDate": "YYYY-MM-DD or null",
  "dueTime": "HH:MM or null",
  "estimateMinutes": number or null,
  "priority": 1|2|3,
  "category": "string",
  "energyLevel": "high|medium|low",
  "contextTags": [],
  "isRecurring": false,
  "recurringPattern": null,
  "confidence": 0.0-1.0
}`;
}

/**
 * Validate LLM response and strip hallucinations
 *
 * @param {object} llmResponse - Raw LLM response
 * @param {string} input - Original user input
 * @returns {object} - Validated response
 */
function validateAndClean(llmResponse, input) {
  const inputLower = input.toLowerCase();

  // Date validation - strip if not mentioned
  const dateWords = [
    // Common dates and abbreviations
    'tomorrow', 'today', 'yesterday', 'tmrw', 'tday', 'ystrdy',
    'tmw', 'tmr', 'tmrw', // More abbreviations for tomorrow
    'nxt', 'next', // "nxt week", "next week"

    // Weekday names
    'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday',
    'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun',

    // Relative time references
    'next week', 'next month', 'this week', 'this weekend', 'weekend',
    'last week', 'last month',

    // End of period references
    'end of day', 'eod', 'end of week', 'eow', 'end of month', 'eom',
    'by end of', // "by end of day", "by end of week"

    // Due/deadline keywords
    'due', 'deadline', 'before', 'until',

    // Month names
    'jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec',

    // Regex patterns
    /\d{1,2}\/\d{1,2}/, // "12/25"
    /\d{4}-\d{2}-\d{2}/, // "2026-01-15"
    /\bon (monday|tuesday|wednesday|thursday|friday|saturday|sunday)/i, // "on friday"
    /\b(due|deadline|by)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)/i, // "due friday"
    /\bfinish (this|next) week/i, // "finish this week"
    /\bin\s+\d+\s*days?\b/i, // "in 3 days", "in 1 day"
    /\b\d+\s*days?\s*(from now|away)\b/i, // "3 days from now", "5 days away"
    /\bin\s+\d+\s*(min|mins|minute|minutes|m)\b/i, // "in 30 min", "in 10 minutes"
    /\bin\s+\d+\s*(hr|hrs|hour|hours|h)\b/i, // "in 2 hours", "in 1h"
    /\bin\s+\d+(\.\d+)?\s*(hr|hrs|hour|hours|h)\b/i, // "in 1.5 hours"
  ];
  const hasDateMention = dateWords.some(word => {
    if (word instanceof RegExp) {
      return word.test(inputLower);
    }
    return inputLower.includes(word);
  });

  // Time validation - define timeWords first for use in both checks
  const timeWords = ['at', 'by', 'in', 'morning', 'afternoon', 'evening', 'noon', 'midnight', 'pm', 'am', /\d{1,2}:\d{2}/, /\d{1,2}(am|pm)/];

  // FIX v1.6.0: Don't strip date if we have a time expression
  // "standup at 9am" has a time, so the LLM setting today's date is CORRECT behavior
  const hasTimeExpression = timeWords.some(word => {
    if (word instanceof RegExp) {
      return word.test(inputLower);
    }
    return inputLower.includes(word);
  });

  if (!hasDateMention && !hasTimeExpression && llmResponse.dueDate) {
    console.warn(`[LLM Parser] Hallucinated date detected. Input: "${input}", LLM date: ${llmResponse.dueDate}`);
    llmResponse.dueDate = null;
  }
  const hasTimeMention = timeWords.some(word => {
    if (word instanceof RegExp) {
      return word.test(inputLower);
    }
    return inputLower.includes(word);
  });

  if (!hasTimeMention && llmResponse.dueTime) {
    console.warn(`[LLM Parser] Hallucinated time detected. Input: "${input}", LLM time: ${llmResponse.dueTime}`);
    llmResponse.dueTime = null;
  }

  // Clean task name - remove trailing prepositions
  if (llmResponse.task) {
    llmResponse.task = llmResponse.task
      .replace(/\s+(by|at|in|on|for)$/i, '')
      .trim();
  }

  // Validate priority range
  if (![1, 2, 3].includes(llmResponse.priority)) {
    llmResponse.priority = 2;
  }

  // FIX 2026-01-06: URGENT KEYWORD PRIORITY UPGRADE
  // If input contains urgent keywords like "now", "asap", "urgent", force Priority 1
  // This fixes cases where LLM doesn't detect urgency correctly
  const urgentKeywords = ['now', 'asap', 'urgent', 'immediately', 'critical', 'emergency'];
  const hasUrgentKeyword = urgentKeywords.some(keyword => inputLower.includes(keyword));

  if (hasUrgentKeyword && llmResponse.priority !== 1) {
    const foundKeyword = urgentKeywords.find(keyword => inputLower.includes(keyword));
    console.log(`[LLM Parser] Urgency keyword "${foundKeyword}" detected → Upgrading to Priority 1`);
    llmResponse.priority = 1;
  }

  // SEMANTIC TIME-BASED PRIORITY UPGRADE
  // If LLM set a due_time, check if it's within 2 hours → auto-upgrade to Priority 1
  // This fixes the issue where LLM parses the time correctly but doesn't infer urgency from it
  // NOTE: This function is called from validateAndClean, which doesn't have access to timezone
  // Priority upgrade uses UTC time - this is acceptable since it's relative time (2 hours is same in any TZ)
  if (llmResponse.dueTime && llmResponse.priority !== 1) {
    try {
      const now = new Date();
      const [hours, minutes] = llmResponse.dueTime.split(':').map(Number);
      const dueDateTime = new Date();
      dueDateTime.setHours(hours, minutes, 0, 0);

      // Handle case where due time is tomorrow (e.g., "in 10 minutes" at 23:55 → 00:05)
      if (dueDateTime < now) {
        dueDateTime.setDate(dueDateTime.getDate() + 1);
      }

      const diffMinutes = (dueDateTime - now) / 60000;

      // If due within 2 hours (120 minutes), upgrade to HIGH priority
      if (diffMinutes > 0 && diffMinutes <= 120) {
        console.log(`[LLM Parser] Auto-upgrading priority: due in ${Math.round(diffMinutes)}min → Priority 1 (HIGH)`);
        llmResponse.priority = 1;
      }
    } catch (e) {
      // If time parsing fails, keep original priority
      console.warn(`[LLM Parser] Could not parse due_time for priority upgrade: ${llmResponse.dueTime}`);
    }
  }

  // Validate category
  const validCategories = ['work', 'personal', 'health', 'finance', 'learning', 'social', 'errands', 'other'];
  if (!validCategories.includes(llmResponse.category)) {
    llmResponse.category = 'other';
  }

  // Validate energy level
  const validEnergy = ['high', 'medium', 'low'];
  if (!validEnergy.includes(llmResponse.energyLevel)) {
    llmResponse.energyLevel = 'medium';
  }

  return llmResponse;
}

/**
 * Parse task using Gemini LLM
 *
 * @param {string} input - User's natural language input
 * @param {string} userTimezone - User's timezone (e.g., 'America/New_York', 'Asia/Kolkata')
 * @returns {Promise<object>} - Parsed task data
 */
async function parseLLM(input, userTimezone = 'UTC') {
  // Check if LLM is enabled
  if (!process.env.ANTHROPIC_API_KEY || process.env.ENABLE_LLM_PARSING === 'false') {
    console.log('[LLM Parser] LLM disabled, using fallback parser');
    return {
      ...parseTask(input),
      source: 'local_fallback',
      reason: 'llm_disabled'
    };
  }

  // Initialize Claude
  if (!initializeClaude()) {
    console.error('[LLM Parser] Failed to initialize Claude, using fallback');
    return {
      ...parseTask(input),
      source: 'local_fallback',
      reason: 'init_failed'
    };
  }

  try {
    // FIX 2026-01-06: Use user's timezone instead of server UTC
    // Build context using user's local time
    const now = new Date();

    // Convert to user's timezone
    const userLocalTime = new Date(now.toLocaleString('en-US', { timeZone: userTimezone }));

    const context = {
      today: userLocalTime.toISOString().split('T')[0],
      currentTime: userLocalTime.toTimeString().split(' ')[0].substring(0, 5),
      dayOfWeek: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][userLocalTime.getDay()],
      timezone: userTimezone
    };

    console.log(`[LLM Parser] Using user timezone: ${userTimezone}, local time: ${context.currentTime}`);

    // Build prompt
    const prompt = buildPrompt(input, context);

    // Call Claude with timeout
    const timeoutMs = 8000; // 8 second timeout (increased from 5s for reliability)
    const modelName = process.env.CLAUDE_MODEL || 'claude-3-haiku-20240307';

    const parsePromise = anthropic.messages.create({
      model: modelName,
      max_tokens: 1024,
      temperature: 0,
      messages: [{
        role: 'user',
        content: prompt
      }]
    });

    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error('LLM timeout')), timeoutMs)
    );

    const result = await Promise.race([parsePromise, timeoutPromise]);
    const responseText = result.content[0].text;

    // Parse JSON response
    let llmResponse;
    try {
      // Remove markdown code blocks if present
      let cleanJson = responseText.replace(/```json\n?|\n?```/g, '').trim();

      console.log('[LLM Parser] Response preview:', responseText.substring(0, 100));

      // Extract just the JSON object, ignoring any text before or after
      // This handles cases where LLM adds explanations after the JSON
      const jsonMatch = cleanJson.match(/\{[\s\S]*?\}(?=\s*(\n\n|$|[^{}\[\]]))/);
      if (jsonMatch) {
        // Find the complete JSON by tracking brace balance
        let braceCount = 0;
        let jsonEnd = -1;
        const startIdx = cleanJson.indexOf('{');
        for (let i = startIdx; i < cleanJson.length; i++) {
          if (cleanJson[i] === '{') braceCount++;
          if (cleanJson[i] === '}') braceCount--;
          if (braceCount === 0) {
            jsonEnd = i + 1;
            break;
          }
        }
        if (jsonEnd > startIdx) {
          cleanJson = cleanJson.substring(startIdx, jsonEnd);
          console.log('[LLM Parser] Extracted JSON:', cleanJson.substring(0, 100));
        }
      }

      llmResponse = JSON.parse(cleanJson);
    } catch (parseError) {
      console.error('[LLM Parser] JSON parse error:', parseError.message);
      console.error('[LLM Parser] Raw response:', responseText.substring(0, 300));
      throw new Error('Invalid LLM response format');
    }

    // Validate and clean response
    const validated = validateAndClean(llmResponse, input);

    // Add metadata
    return {
      title: validated.task,
      due_date: validated.dueDate,
      due_time: validated.dueTime,
      estimate_minutes: validated.estimateMinutes,
      priority: validated.priority,
      category: validated.category,
      energy_level: validated.energyLevel,
      context_tags: validated.contextTags,
      is_recurring: validated.isRecurring,
      recurring_pattern: validated.recurringPattern,
      confidence: validated.confidence || 0.85,
      source: 'llm',
      model: modelName
    };

  } catch (error) {
    console.error('[LLM Parser] Error:', error.message);

    // Graceful fallback to local parser
    return {
      ...parseTask(input),
      source: 'local_fallback',
      reason: error.message.includes('timeout') ? 'timeout' : 'api_error',
      originalError: error.message
    };
  }
}

/**
 * Test function for local development
 */
async function testParser() {
  const testInputs = [
    "Email manager by 5pm tomorrow",
    "Call mom in 10 minutes urgently",
    "Workout at gym high energy",
    "Read book for 30 minutes",
    "Meeting every Monday at 2pm"
  ];

  console.log('\n=== LLM Parser Test ===\n');

  for (const input of testInputs) {
    console.log(`Input: "${input}"`);
    const result = await parseLLM(input);
    console.log('Result:', JSON.stringify(result, null, 2));
    console.log('---');
  }
}

// Export functions
module.exports = {
  parseLLM,
  validateAndClean,
  buildPrompt,
  testParser
};

// Run tests if called directly
if (require.main === module) {
  testParser().catch(console.error);
}
