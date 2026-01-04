# 🔄 Gemini → Claude API Migration Guide

> **Date:** 2026-01-05
> **Status:** ✅ COMPLETE
> **Changes:** Replaced all Gemini API calls with Claude/Anthropic API

---

## 📋 Summary

All LLM features have been migrated from Google Gemini to Anthropic Claude API.

### **What Changed:**

| Component | Before | After |
|-----------|--------|-------|
| **SDK** | @google/generative-ai | @anthropic-ai/sdk |
| **API Key** | GEMINI_API_KEY | ANTHROPIC_API_KEY |
| **Model** | gemini-2.0-flash-lite | claude-3-haiku-20240307 |
| **Task Parser** | Gemini 1.5 Flash | Claude 3 Haiku |
| **Message Generator** | Gemini 1.5 Flash | Claude 3 Haiku |

---

## 🔧 Files Modified

### **1. Package Dependencies**
- ✅ `backend/package.json` - Added `@anthropic-ai/sdk`
- ❌ Can remove `@google/generative-ai` (optional)

### **2. Task Parser**
- ✅ `backend/utils/llm-task-parser.js`
  - Replaced `GoogleGenerativeAI` with `Anthropic`
  - Updated `initializeGemini()` → `initializeClaude()`
  - Updated API call format
  - Updated environment variable checks

### **3. Message Generator**
- ✅ `backend/services/message-generator-llm.js`
  - Replaced `GoogleGenerativeAI` with `Anthropic`
  - Updated `initializeGemini()` → `initializeClaude()`
  - Updated API call format
  - Updated environment variable checks

### **4. Environment Configuration**
- ✅ `backend/.env.example`
  - Changed `GEMINI_API_KEY` → `ANTHROPIC_API_KEY`
  - Changed `GEMINI_MODEL` → `CLAUDE_MODEL`
  - Updated model options and defaults

---

## ⚙️ Configuration Required

### **For Local Development:**

1. **Get Claude API Key:**
   - Visit: https://console.anthropic.com/
   - Create an account (if needed)
   - Generate an API key
   - Copy the key (starts with `sk-ant-...`)

2. **Update `.env` file:**
   ```bash
   # Old (remove these):
   # GEMINI_API_KEY=your-gemini-key
   # GEMINI_MODEL=gemini-2.0-flash-lite

   # New (add these):
   ANTHROPIC_API_KEY=sk-ant-your-api-key-here
   CLAUDE_MODEL=claude-3-haiku-20240307
   ENABLE_LLM_PARSING=true
   ENABLE_LLM_MESSAGES=true
   ```

### **For Vercel Production:**

Go to: https://vercel.com/pratapme1s-projects/cosmic-ocean-api/settings/environment-variables

**Remove:**
- `GEMINI_API_KEY`
- `GEMINI_MODEL`

**Add:**
```
ANTHROPIC_API_KEY=sk-ant-your-api-key-here
CLAUDE_MODEL=claude-3-haiku-20240307
ENABLE_LLM_PARSING=true
ENABLE_LLM_MESSAGES=true
```

**Then redeploy:**
```bash
cd /home/vi/supernova/backend
npx vercel --prod
```

---

## 🎯 Claude Model Options

### **Available Models (from cheapest to most capable):**

| Model | ID | Speed | Cost | Use Case |
|-------|---|-------|------|----------|
| **Haiku** | `claude-3-haiku-20240307` | Fastest | Lowest | Task parsing, quick responses |
| **Sonnet** | `claude-3-sonnet-20240229` | Fast | Medium | Balanced performance |
| **Sonnet 3.5** | `claude-3-5-sonnet-20241022` | Fast | Medium | Latest balanced model |
| **Opus** | `claude-3-opus-20240229` | Slowest | Highest | Complex reasoning (overkill for our use) |

### **Recommended for Our App:**

```bash
# Default (best for production):
CLAUDE_MODEL=claude-3-haiku-20240307

# Alternative (better quality, slightly slower):
CLAUDE_MODEL=claude-3-5-sonnet-20241022
```

**Why Haiku?**
- ⚡ Fastest response time (<1s)
- 💰 Lowest cost
- ✅ Perfect for task parsing and short messages
- 🎯 Handles our 8-word message limit well

---

## 📊 API Differences

### **Request Format:**

**Gemini:**
```javascript
const model = genAI.getGenerativeModel({
  model: 'gemini-2.0-flash-lite',
  generationConfig: {
    temperature: 0,
    responseMimeType: 'application/json'
  }
});
const result = await model.generateContent(prompt);
const text = result.response.text();
```

**Claude:**
```javascript
const result = await anthropic.messages.create({
  model: 'claude-3-haiku-20240307',
  max_tokens: 1024,
  temperature: 0,
  messages: [{
    role: 'user',
    content: prompt
  }]
});
const text = result.content[0].text;
```

### **Key Differences:**

1. **Response Format:**
   - Gemini: `result.response.text()`
   - Claude: `result.content[0].text`

2. **Config:**
   - Gemini: `responseMimeType: 'application/json'`
   - Claude: Must parse JSON from text response (same as before)

3. **Max Tokens:**
   - Gemini: `maxOutputTokens`
   - Claude: `max_tokens`

4. **Messages:**
   - Gemini: Direct prompt string
   - Claude: Array of message objects with roles

---

## 🧪 Testing

### **Test Task Parsing:**
```bash
# Create a task in the Android app:
"urgent meeting with manager tomorrow at 3pm"

# Check backend logs for:
[LLM Parser] Claude initialized successfully
[LLM Parser] Generated {...}
source: 'llm'
model: 'claude-3-haiku-20240307'
```

### **Test Message Generation:**
```bash
# Request wallpaper from API
curl "https://cosmic-ocean-api.vercel.app/api/wallpaper?theme=cosmic&resolution=1080x2246" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Check backend logs for:
[MessageGen] Claude initialized successfully
[MessageGen] Generating messages - Voice: WARM_FRIEND, Intent: CELEBRATE
[MessageGen] Generated 5 raw messages
```

### **Test Fallback:**
```bash
# Remove ANTHROPIC_API_KEY temporarily
# Verify app still works with template messages:
[MessageGen] ANTHROPIC_API_KEY not set - LLM messages disabled
[MessageGen] Using fallback templates
```

---

## 💰 Cost Comparison

### **Gemini Pricing (Free Tier):**
- Free: 15 requests/minute, 1500 requests/day
- After free tier: $0.075 per 1M input tokens

### **Claude Pricing:**
- **No free tier** (credit card required)
- Haiku: $0.25 per 1M input tokens, $1.25 per 1M output tokens
- Sonnet: $3 per 1M input tokens, $15 per 1M output tokens

### **Estimated Monthly Cost (100 users, 10 requests/day each):**

**Task Parsing (1000 requests/day):**
- Input: ~200 tokens/request × 1000 = 200K tokens/day × 30 = 6M tokens/month
- Output: ~100 tokens/request × 1000 = 100K tokens/day × 30 = 3M tokens/month
- **Cost:** (6M × $0.25) + (3M × $1.25) = **$5.25/month**

**Message Generation (1000 requests/day):**
- Input: ~150 tokens/request × 1000 = 150K tokens/day × 30 = 4.5M tokens/month
- Output: ~50 tokens/request × 1000 = 50K tokens/day × 30 = 1.5M tokens/month
- **Cost:** (4.5M × $0.25) + (1.5M × $1.25) = **$3.00/month**

**Total:** ~**$8-10/month** for 100 active users

**Recommendation:**
- Keep LLM features **disabled by default**
- Enable for power users or premium tier only
- Monitor costs via Anthropic console

---

## 🚨 Important Notes

### **1. API Key Format:**
- Gemini: Starts with `AI...`
- Claude: Starts with `sk-ant-...`

### **2. Rate Limits:**
- Haiku: 50 requests/minute (Tier 1)
- Can request higher limits if needed

### **3. Breaking Changes:**
- Old `GEMINI_API_KEY` will not work
- Must update environment variables in Vercel
- No code changes needed in Android app

### **4. Backward Compatibility:**
- ✅ Template fallback still works
- ✅ Local NLP parser still works
- ✅ No Android changes required

---

## ✅ Deployment Checklist

- [x] Install `@anthropic-ai/sdk`
- [x] Update `llm-task-parser.js`
- [x] Update `message-generator-llm.js`
- [x] Update `.env.example`
- [ ] Get Claude API key from console.anthropic.com
- [ ] Add `ANTHROPIC_API_KEY` to Vercel
- [ ] Add `CLAUDE_MODEL` to Vercel
- [ ] Commit and push to GitHub
- [ ] Deploy to Vercel
- [ ] Test task parsing
- [ ] Test message generation
- [ ] Monitor costs in Anthropic console

---

## 🔄 Rollback Plan

If needed, you can rollback by:

1. **Reinstall Gemini SDK:**
   ```bash
   npm install @google/generative-ai
   ```

2. **Revert files from git:**
   ```bash
   git checkout HEAD~1 backend/utils/llm-task-parser.js
   git checkout HEAD~1 backend/services/message-generator-llm.js
   git checkout HEAD~1 backend/.env.example
   ```

3. **Update Vercel env vars:**
   - Remove `ANTHROPIC_API_KEY`
   - Add back `GEMINI_API_KEY`

4. **Redeploy**

---

## 📚 Resources

- Claude API Docs: https://docs.anthropic.com/
- Claude Console: https://console.anthropic.com/
- Pricing: https://www.anthropic.com/pricing
- Model Comparison: https://docs.anthropic.com/en/docs/models-overview

---

**Migration Complete!** 🎉

All LLM features now use Claude API instead of Gemini.
