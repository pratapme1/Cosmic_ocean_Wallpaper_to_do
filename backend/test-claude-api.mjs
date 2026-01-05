import Anthropic from '@anthropic-ai/sdk';
import 'dotenv/config';

console.log('Testing Claude API connection...\n');

// Check env vars
console.log('ANTHROPIC_API_KEY:', process.env.ANTHROPIC_API_KEY ? `${process.env.ANTHROPIC_API_KEY.substring(0, 15)}...` : '❌ NOT SET');
console.log('CLAUDE_MODEL:', process.env.CLAUDE_MODEL || 'claude-3-haiku-20240307 (default)');
console.log('ENABLE_LLM_MESSAGES:', process.env.ENABLE_LLM_MESSAGES);
console.log('ENABLE_LLM_PARSING:', process.env.ENABLE_LLM_PARSING);
console.log('');

if (!process.env.ANTHROPIC_API_KEY) {
  console.error('❌ ANTHROPIC_API_KEY not set!');
  process.exit(1);
}

try {
  const anthropic = new Anthropic({
    apiKey: process.env.ANTHROPIC_API_KEY,
  });

  console.log('✅ Anthropic client initialized');
  console.log('Sending test request...\n');

  const message = await anthropic.messages.create({
    model: process.env.CLAUDE_MODEL || 'claude-3-haiku-20240307',
    max_tokens: 100,
    messages: [{
      role: 'user',
      content: 'Parse this task: "urgent call mom tomorrow 3pm". Return JSON with: task, priority (1-3), dueDate, dueTime.'
    }]
  });

  console.log('✅ SUCCESS! Claude API is working!\n');
  console.log('Response:');
  console.log(message.content[0].text);
  console.log('\nModel used:', message.model);
  console.log('Tokens used:', message.usage);

} catch (error) {
  console.error('❌ ERROR:', error.message);
  if (error.status) {
    console.error('Status:', error.status);
  }
  if (error.error) {
    console.error('Error details:', error.error);
  }
  process.exit(1);
}
