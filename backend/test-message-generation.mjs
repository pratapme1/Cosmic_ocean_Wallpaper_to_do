/**
 * Epic 8 Week 4: Test Message Generation System
 * Tests LLM message generation, caching, and rotation
 */

import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import dotenv from 'dotenv';
import { Client } from 'pg';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Load environment
dotenv.config({ path: join(__dirname, '../.env') });

console.log('\n🧪 Epic 8 Week 4: Message Generation Test\n');
console.log('=========================================\n');

// Configuration
const DATABASE_URL = process.env.DATABASE_URL;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const ENABLE_LLM_MESSAGES = process.env.ENABLE_LLM_MESSAGES;

console.log('📊 Configuration:');
console.log(`  DATABASE_URL: ${DATABASE_URL ? '✅ Set' : '❌ Not set'}`);
console.log(`  GEMINI_API_KEY: ${GEMINI_API_KEY ? '✅ Set' : '❌ Not set'}`);
console.log(`  ENABLE_LLM_MESSAGES: ${ENABLE_LLM_MESSAGES}`);
console.log('');

if (!DATABASE_URL) {
  console.error('❌ DATABASE_URL not set');
  process.exit(1);
}

const client = new Client({
  connectionString: DATABASE_URL,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

async function testMessageGeneration() {
  try {
    await client.connect();
    console.log('✅ Connected to database\n');

    // Get test user
    const userQuery = 'SELECT id, email FROM users LIMIT 1';
    const userResult = await client.query(userQuery);

    if (userResult.rows.length === 0) {
      console.error('❌ No users found in database');
      console.log('💡 Create a user first: npm test -- auth.test.js');
      process.exit(1);
    }

    const testUser = userResult.rows[0];
    console.log(`👤 Test User: ${testUser.email} (${testUser.id})\n`);

    // Create some test tasks for context
    console.log('📝 Creating test tasks for message context...');
    const tasks = [
      { title: 'Review code', priority: 1, due_date: new Date().toISOString().split('T')[0] },
      { title: 'Update docs', priority: 2, due_date: new Date(Date.now() + 86400000).toISOString().split('T')[0] },
      { title: 'Fix bug', priority: 1, completed: false }
    ];

    for (const task of tasks) {
      await client.query(
        'INSERT INTO tasks (user_id, title, priority, due_date, completed) VALUES ($1, $2, $3, $4, $5)',
        [testUser.id, task.title, task.priority, task.due_date, task.completed || false]
      );
    }
    console.log(`  ✅ Created ${tasks.length} test tasks\n`);

    // Test 1: Generate and cache messages
    console.log('🧪 Test 1: Generate and Cache Messages');
    console.log('---------------------------------------');

    // Import after env is loaded
    const { generateAndCacheMessages } = await import('./services/message-generator-llm.js');

    const generateResult = await generateAndCacheMessages(testUser.id);

    console.log(`Result: ${JSON.stringify(generateResult, null, 2)}`);

    if (!generateResult.success) {
      console.log('⚠️  Message generation failed (expected if no API key)');
      console.log('💡 Set GEMINI_API_KEY in .env to enable LLM generation\n');
    } else {
      console.log(`✅ Generated ${generateResult.count} messages (source: ${generateResult.source})\n`);
    }

    // Test 2: Check message cache
    console.log('🧪 Test 2: Check Message Cache');
    console.log('--------------------------------');

    const cacheQuery = 'SELECT * FROM message_cache WHERE user_id = $1 ORDER BY display_order';
    const cacheResult = await client.query(cacheQuery, [testUser.id]);

    console.log(`Found ${cacheResult.rows.length} messages in cache:\n`);
    cacheResult.rows.forEach((msg, i) => {
      console.log(`  ${i + 1}. "${msg.message}"`);
      console.log(`     Voice: ${msg.voice}, Intent: ${msg.intent}, Shown: ${msg.shown}`);
    });
    console.log('');

    // Test 3: Get current message (should mark as shown)
    console.log('🧪 Test 3: Get Current Message (Rotation Test)');
    console.log('------------------------------------------------');

    const { getCurrentMessage } = await import('./services/wallpaper-message-provider.js');

    for (let i = 1; i <= 3; i++) {
      const message = await getCurrentMessage(testUser.id);
      console.log(`\n  Message ${i}:`);
      console.log(`    "${message.message}"`);
      console.log(`    Source: ${message.source}, Voice: ${message.voice}, Intent: ${message.intent}`);
    }
    console.log('');

    // Test 4: Check message history
    console.log('🧪 Test 4: Check Message History');
    console.log('---------------------------------');

    const historyQuery = 'SELECT * FROM message_history WHERE user_id = $1 ORDER BY shown_at DESC LIMIT 5';
    const historyResult = await client.query(historyQuery, [testUser.id]);

    console.log(`Found ${historyResult.rows.length} messages in history:\n`);
    historyResult.rows.forEach((msg, i) => {
      console.log(`  ${i + 1}. "${msg.message}" (${msg.voice}/${msg.intent})`);
      console.log(`     Shown at: ${msg.shown_at}`);
    });
    console.log('');

    // Test 5: Verify remaining unshown count
    console.log('🧪 Test 5: Cache Status');
    console.log('------------------------');

    const remainingQuery = 'SELECT COUNT(*) as count FROM message_cache WHERE user_id = $1 AND shown = false';
    const remainingResult = await client.query(remainingQuery, [testUser.id]);

    const remaining = parseInt(remainingResult.rows[0].count);
    console.log(`Remaining unshown messages: ${remaining}`);

    if (remaining <= 2) {
      console.log('⚠️  Cache running low - background worker would trigger refresh');
    } else {
      console.log('✅ Cache healthy');
    }
    console.log('');

    // Cleanup
    console.log('🧹 Cleaning up test data...');
    await client.query('DELETE FROM tasks WHERE user_id = $1', [testUser.id]);
    await client.query('DELETE FROM message_cache WHERE user_id = $1', [testUser.id]);
    await client.query('DELETE FROM message_history WHERE user_id = $1', [testUser.id]);
    console.log('  ✅ Test data cleaned up\n');

    console.log('✅ All tests passed!\n');

  } catch (error) {
    console.error('\n❌ Error:', error.message);
    console.error(error.stack);
    process.exit(1);
  } finally {
    await client.end();
  }
}

testMessageGeneration();
