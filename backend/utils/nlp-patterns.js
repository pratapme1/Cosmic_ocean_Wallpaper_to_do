/**
 * NLP Pattern Definitions for Task Parser
 *
 * Defines patterns for extracting metadata from natural language task descriptions.
 * Based on cosmic-ocean-intelligence-spec requirements.
 */

// Context tags that can be extracted from task descriptions
const CONTEXT_TAGS = [
  '@home',
  '@work',
  '@gym',
  '@errands',
  '@computer',
  '@phone',
  '@office',
  '@outside',
  '@online',
  '@waiting'
];

// Keywords that indicate high energy tasks
const HIGH_ENERGY_KEYWORDS = [
  'focus',
  'deep work',
  'concentrate',
  'intensive',
  'challenging',
  'complex',
  'difficult',
  'hard',
  'brainstorm',
  'creative',
  'strategic',
  'analyze',
  'research'
];

// Keywords that indicate low energy tasks
const LOW_ENERGY_KEYWORDS = [
  'quick',
  'easy',
  'simple',
  'routine',
  'mindless',
  'relax',
  'casual',
  'light',
  'brief',
  'short'
];

// Keywords that indicate high priority (P1)
const HIGH_PRIORITY_KEYWORDS = [
  'urgent',
  'asap',
  'important',
  'critical',
  'priority',
  'deadline',
  'must',
  'need to',
  'have to',
  'immediately',
  'now',
  'today',
  'essential',
  'crucial',
  'vital'
];

// Keywords that indicate low priority (P3)
const LOW_PRIORITY_KEYWORDS = [
  'someday',
  'maybe',
  'when possible',
  'if time',
  'eventually',
  'later',
  'whenever',
  'could',
  'might',
  'optional',
  'nice to have',
  'low priority'
];

// Category detection patterns
// NOTE: Order matters for tiebreakers - more specific categories should have higher-scoring keywords
const CATEGORY_PATTERNS = {
  work: [
    'meeting', 'email', 'report', 'presentation', 'project',
    'deadline', 'client', 'team', 'review', 'submit',
    'proposal', 'budget', 'invoice', 'contract', 'colleague',
    'standup', 'scrum', 'sprint', 'deploy', 'code',
    '@work', '@office'
  ],
  personal: [
    'family', 'home', 'house', 'clean', 'organize',
    'pet', 'garden', 'laundry', 'cook',
    '@home'
    // Note: 'friend' moved to social to avoid conflict with "dinner with friends"
  ],
  health: [
    'workout', 'exercise', 'gym', 'run', 'yoga', 'meditate',
    'doctor', 'dentist', 'medicine', 'vitamin', 'sleep',
    'diet', 'nutrition', 'weight', 'health', 'fitness',
    '@gym'
  ],
  finance: [
    'pay', 'bill', 'bank', 'budget', 'invest', 'tax',
    'insurance', 'credit', 'loan', 'save', 'money',
    'expense', 'receipt', 'refund'
  ],
  learning: [
    'learn', 'study', 'read', 'book', 'course', 'class',
    'practice', 'tutorial', 'lesson', 'exam', 'test',
    'research', 'skill', 'education'
  ],
  social: [
    'call', 'meet', 'dinner', 'lunch', 'coffee', 'party',
    'event', 'birthday', 'anniversary', 'gift', 'visit',
    'hangout', 'catch up', 'friend'
  ],
  errands: [
    'buy', 'shop', 'store', 'pick up', 'drop off', 'return',
    'grocery', 'pharmacy', 'post office', 'dry clean',
    '@errands'
  ]
};

// Recurring pattern definitions
const RECURRING_PATTERNS = [
  { pattern: /every\s*day|daily/i, interval: 'daily', dayOfWeek: null, dayOfMonth: null },
  { pattern: /every\s*week|weekly/i, interval: 'weekly', dayOfWeek: null, dayOfMonth: null },
  { pattern: /every\s*month|monthly/i, interval: 'monthly', dayOfWeek: null, dayOfMonth: null },
  { pattern: /every\s*monday/i, interval: 'weekly', dayOfWeek: 1, dayOfMonth: null },
  { pattern: /every\s*tuesday/i, interval: 'weekly', dayOfWeek: 2, dayOfMonth: null },
  { pattern: /every\s*wednesday/i, interval: 'weekly', dayOfWeek: 3, dayOfMonth: null },
  { pattern: /every\s*thursday/i, interval: 'weekly', dayOfWeek: 4, dayOfMonth: null },
  { pattern: /every\s*friday/i, interval: 'weekly', dayOfWeek: 5, dayOfMonth: null },
  { pattern: /every\s*saturday/i, interval: 'weekly', dayOfWeek: 6, dayOfMonth: null },
  { pattern: /every\s*sunday/i, interval: 'weekly', dayOfWeek: 0, dayOfMonth: null },
  { pattern: /bi-?weekly|every\s*two\s*weeks|every\s*other\s*week/i, interval: 'biweekly', dayOfWeek: null, dayOfMonth: null },
  { pattern: /every\s*(\d+)(?:st|nd|rd|th)?(?:\s*of\s*(?:the\s*)?month)?/i, interval: 'monthly', dayOfWeek: null, dayOfMonth: '$1' }
];

// Time context patterns (morning, afternoon, evening, etc.)
const TIME_CONTEXT_PATTERNS = [
  { pattern: /\b(this\s+)?morning\b/i, timeContext: 'morning', suggestedHour: 9 },
  { pattern: /\bafter\s*lunch\b/i, timeContext: 'afternoon', suggestedHour: 14 },
  { pattern: /\b(this\s+)?afternoon\b/i, timeContext: 'afternoon', suggestedHour: 14 },
  { pattern: /\b(this\s+)?evening\b/i, timeContext: 'evening', suggestedHour: 18 },
  { pattern: /\btonight\b/i, timeContext: 'evening', suggestedHour: 20 },
  { pattern: /\bbefore\s*bed\b/i, timeContext: 'night', suggestedHour: 22 },
  { pattern: /\bfirst\s*thing\b/i, timeContext: 'morning', suggestedHour: 8 },
  { pattern: /\bend\s*of\s*day\b|\beod\b/i, timeContext: 'evening', suggestedHour: 17 }
];

// Duration keyword mappings (for natural language durations)
const DURATION_KEYWORDS = {
  'quick': 15,
  'brief': 15,
  'short': 30,
  'long': 120,
  'all day': 480,
  'half day': 240,
  'full day': 480,
  'pomodoro': 25
};

module.exports = {
  CONTEXT_TAGS,
  HIGH_ENERGY_KEYWORDS,
  LOW_ENERGY_KEYWORDS,
  HIGH_PRIORITY_KEYWORDS,
  LOW_PRIORITY_KEYWORDS,
  CATEGORY_PATTERNS,
  RECURRING_PATTERNS,
  TIME_CONTEXT_PATTERNS,
  DURATION_KEYWORDS
};
