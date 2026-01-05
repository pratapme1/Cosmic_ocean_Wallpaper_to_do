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
   - Remove trailing: "by", "at", "in", "on", "for"

2. dueDate: ONLY if EXPLICITLY mentioned. Otherwise null.
   - "tomorrow", "Monday", "Jan 5", "next Friday" → YYYY-MM-DD
   - NO date words in input → null (DO NOT INVENT)
   - "today" → ${today}
   - "tomorrow" → calculate from ${today}

3. dueTime: ONLY if EXPLICITLY mentioned. Otherwise null.
   - "at 3pm", "by 5:00", "noon" → HH:MM (24-hour)
   - "in X minutes/hours" → calculate from ${currentTime}
   - "morning" → 09:00, "afternoon" → 14:00, "evening" → 18:00
   - NO time words → null (DO NOT INVENT)

4. estimateMinutes: ONLY from explicit duration mentions.
   - "30 minute call" → 30
   - "quick task" → 15, "1 hour meeting" → 60
   - NO duration mentioned → null

5. priority: Based on EXPLICIT urgency keywords ONLY.
   - Keywords: "urgent", "asap", "critical", "important", "!!!" → 1 (high)
   - Keywords: "important" → 2 (medium)
   - Keywords: "someday", "maybe", "eventually" → 3 (low)
   - NO keywords → 2 (default)

6. category: Infer from keywords, use "other" if uncertain.
   - Options: work, personal, health, finance, learning, social, errands, other
   - "email manager" → work, "call mom" → personal, "gym" → health

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

OUTPUT RULES (CRITICAL):
- Respond with ONLY a valid JSON object.
- No preamble, no explanations, no markdown, no code fences.
- The first character must be "{" and the last character must be "}".

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
  const dateWords = ['tomorrow', 'today', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday', 'next week', 'next month', 'jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec', /\d{1,2}\/\d{1,2}/, /\d{4}-\d{2}-\d{2}/];
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
 * @returns {Promise<object>} - Parsed task data
 */
async function parseLLM(input) {
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
    // Build context
    const now = new Date();
    const context = {
      today: now.toISOString().split('T')[0],
      currentTime: now.toTimeString().split(' ')[0].substring(0, 5),
      dayOfWeek: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][now.getDay()]
    };

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
      const cleanJson = responseText.replace(/```json\n?|\n?```/g, '').trim();
      let jsonText = cleanJson;
      if (!jsonText.startsWith('{')) {
        const firstBrace = jsonText.indexOf('{');
        const lastBrace = jsonText.lastIndexOf('}');
        if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
          jsonText = jsonText.slice(firstBrace, lastBrace + 1).trim();
        }
      }
      llmResponse = JSON.parse(jsonText);
    } catch (parseError) {
      console.error('[LLM Parser] JSON parse error:', parseError.message);
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
