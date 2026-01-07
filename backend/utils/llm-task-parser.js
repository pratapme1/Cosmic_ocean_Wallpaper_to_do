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

  return `You are a task parser. Extract structured data from natural language task input.

INPUT: "${input}"
TODAY: ${today} (${dayOfWeek})
CURRENT TIME: ${currentTime}

STRICT RULES (CRITICAL - DO NOT HALLUCINATE):

1. task: Extract ONLY the action description. Remove ALL date/time/priority words.
   - "Email manager by 5pm tomorrow" → task: "Email manager"
   - "Call mom in 10 minutes urgently" → task: "Call mom"
   - "missed alert yesterday" → task: "missed alert"
   - Remove trailing: "by", "at", "in", "on", "for"

2. dueDate: ONLY if EXPLICITLY mentioned. Otherwise null.
   - "tomorrow"/"tmrw", "today"/"tday", "yesterday"/"ystrdy" → calculate from ${today}
     * "yesterday" → date one day before ${today}
     * EXAMPLE: If TODAY is 2026-01-06, "missed alert yesterday" → dueDate: "2026-01-05"
   - Day of week: "monday", "tuesday", "friday", "on monday", "on friday", "next friday" → calculate YYYY-MM-DD
     * If day is ${dayOfWeek} and input says "monday", find next Monday's date
     * "party on friday" → find next Friday's date → YYYY-MM-DD
     * "call on monday" → find next Monday's date → YYYY-MM-DD
   - Date phrases: "end of day"/"eod", "end of week"/"eow", "this weekend", "next week", "next month" → calculate date
   - Past phrases: "last week", "last month" → calculate appropriate past date
   - Explicit dates: "Jan 5", "1/15", "2026-01-20" → YYYY-MM-DD
   - NO date words in input → null (DO NOT INVENT)
   - "today" → ${today}

3. dueTime: ONLY if EXPLICITLY mentioned. Otherwise null.
   - "at 3pm", "by 5:00", "noon" → HH:MM (24-hour)
   - "in X minutes/hours" → ADD X to ${currentTime} and return result as HH:MM
     * EXAMPLE: If currentTime is 23:21 and input is "in 39 minutes" → 23:21 + 39min = 00:00 (next day)
     * EXAMPLE: If currentTime is 14:30 and input is "in 1 hour" → 14:30 + 60min = 15:30
     * IMPORTANT: Handle midnight rollover correctly!
   - Time-of-day words (when used as timing, not just description):
     "morning task", "task in the morning" → 09:00
     "afternoon meeting", "task in afternoon" → 14:00
     "evening call", "task in evening" → 18:00
   - NO time words → null (DO NOT INVENT)
   - CRITICAL: "in 39 minutes" means DUE TIME, NOT duration. Set dueTime, NOT estimateMinutes!

4. estimateMinutes: ONLY from explicit TASK DURATION mentions. NOT "in X minutes".
   - "30 minute call" → 30 (how long the call takes)
   - "quick task" → 15, "1 hour meeting" → 60
   - "record in 39 minutes" → estimateMinutes: null (39 is DUE TIME, not duration!)
   - NO duration mentioned → null

5. priority: Infer from BOTH urgency keywords AND time pressure (semantic understanding).

   CRITICAL: This determines star size and color in the app:
   - Priority 1 (HIGH) → 52px RED star (always urgent)
   - Priority 2 (MEDIUM) → 36px ORANGE/BLUE star (time-based)
   - Priority 3 (LOW) → 24px BLUE star (future/someday)

   Priority 1 (HIGH) - Use when ANY of these apply:
   - Explicit urgency: "urgent", "asap", "critical", "now", "immediately", "!!!"
   - Time pressure: due within 2 hours (CHECK THIS CAREFULLY!)
     * "in 10 minutes", "in 10m" → Priority 1 (urgent)
     * "in 1 hour", "in 1h", "in 30m", "in 45min" → Priority 1 (urgent)
     * "by 3pm today" (if current time is after 1pm) → Priority 1
     * ANY "in X minutes/hours" where X ≤ 2 hours → Priority 1
   - Semantic urgency: "emergency", "hurry", "quick"

   Priority 3 (LOW) - ONLY use when EXPLICITLY applies. Otherwise default to Priority 2!

   CRITICAL: Today is ${dayOfWeek}, ${today}. Calculate time distance carefully!

   TWO CRITERIA ONLY (must meet one):

   A) Due date >24 hours away (calculate from today!):
      * "by friday" when today is ${dayOfWeek} → if friday is >1 day away → Priority 3
      * "next week", "next monday", "next month" → >1 day away → Priority 3
      * "physical therapy session friday" → if friday >1 day away → Priority 3
      * "code review by friday" → if friday >1 day away → Priority 3
      * "dinner friday 7pm" → if friday >1 day away → Priority 3

   B) Explicit low-priority keywords (ALWAYS Priority 3):
      * "maybe", "someday", "eventually", "when free", "if time", "could", "might want to"
      * "maybe clean garage", "someday learn piano" → Priority 3

   DO NOT use Priority 3 for:
   - Regular tasks without distant dates ("journal", "study", "workout")
   - Tasks due today, tomorrow, or within 24 hours
   - Work/health/learning tasks without explicit low-priority keywords

   Priority 2 (MEDIUM) - THIS IS THE DEFAULT! Use when NOT Priority 1 or 3:
   - Due in 2-24 hours ("tomorrow morning", "today evening", "by 5pm tomorrow")
   - Regular tasks: "journal", "study", "workout", "call", "email", "meeting"
   - Work/health/finance/learning tasks without urgency or distant future dates
   - No deadline specified: "buy groceries", "finish homework", "drink water"
   - Keywords: "important", "soon", "today" (without distant date)

   DECISION FLOW (FOLLOW THIS ORDER):
   1. Check Priority 1 criteria first (urgent/within 2hrs) → if yes, Priority 1
   2. Check Priority 3 criteria second (>24hrs OR low-priority keywords) → if yes, Priority 3
   3. DEFAULT to Priority 2 if neither 1 nor 3 apply → Priority 2

6. category: Infer from keywords and context. Be specific - only use "other" as last resort.

   Options: work, personal, health, finance, learning, social, errands, other

   PRIORITY ORDER (action keywords override relationship keywords):
   1. Check ACTION keywords first (buy, study, workout, pay, etc.)
   2. Then check CONTEXT keywords (family, work, social, etc.)

   Category Keywords:
   - errands: buy, groceries, shopping, pick up, drop off, mail, post office, pharmacy, return, store, purchase, get
   - work: email, meeting, presentation, manager, client, project, code, deploy, standup, review, deadline, report
   - health: workout, gym, exercise, doctor, appointment, yoga, run, meditate, vitamins, therapy, prescription, sleep
   - finance: pay, rent, bill, budget, money, bank, credit card, taxes, subscription, savings, expense, cancel subscription
   - learning: study, read, homework, course, tutorial, learn, practice, review, flashcards, chapter, exam, certification
   - social: coffee, dinner, party, friends, RSVP, wedding, visit, volunteer, game night, hangout
   - personal: mom, dad, family, call, message, organize, clean, plan, home, journal, personal care

   EXAMPLES:
   - "buy gift for nephew" → errands (action "buy" takes priority over relationship "nephew")
   - "call mom" → personal (family relationship)
   - "meditate for 10 minutes" → health (meditation is wellness)
   - "finish homework" → learning (homework is education)
   - "cancel subscriptions" → finance (subscriptions are recurring payments)
   - "study for certification exam" → learning (studying is education)
   - "buy groceries" → errands (shopping task)
   - "team meeting" → work (workplace)
   - "journal before sleep" → personal (personal care activity)

7. energyLevel: From explicit energy mentions ONLY.
   - Keywords: "high energy", "intense" → "high"
   - Keywords: "low energy", "tired", "relaxed" → "low"
   - NO keywords → "medium"

8. contextTags: Extract @location mentions.
   - "@office", "@home", "@gym" → extract as array
   - NO @ mentions → []

9. isRecurring: ONLY if explicit recurrence.
   - "every day", "weekly", "monthly" → true
   - NO recurrence words → false

10. recurringPattern: ONLY if isRecurring = true.
    - "every Monday" → "weekly"
    - "daily standup" → "daily"
    - Not recurring → null

RESPONSE FORMAT (JSON):
{
  "task": "Clean task name",
  "dueDate": "YYYY-MM-DD or null",
  "dueTime": "HH:MM or null",
  "estimateMinutes": number or null,
  "priority": 1 | 2 | 3,
  "category": "work|personal|health|finance|learning|social|errands|other",
  "energyLevel": "high|medium|low",
  "contextTags": ["@location"],
  "isRecurring": boolean,
  "recurringPattern": "daily|weekly|monthly|null",
  "confidence": 0.0-1.0
}

CRITICAL: If you're not certain about a field, use null. NEVER invent data.`;
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
  ];
  const hasDateMention = dateWords.some(word => {
    if (word instanceof RegExp) {
      return word.test(inputLower);
    }
    return inputLower.includes(word);
  });

  if (!hasDateMention && llmResponse.dueDate) {
    console.warn(`[LLM Parser] Hallucinated date detected. Input: "${input}", LLM date: ${llmResponse.dueDate}`);
    llmResponse.dueDate = null;
  }

  // Time validation - strip if not mentioned
  const timeWords = ['at', 'by', 'in', 'morning', 'afternoon', 'evening', 'noon', 'midnight', 'pm', 'am', /\d{1,2}:\d{2}/, /\d{1,2}(am|pm)/];
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
