/**
 * Test /api/tasks/all endpoint
 */

import fetch from 'node-fetch';

const API_URL = process.env.API_URL || 'http://localhost:3000';

async function testTasksAllEndpoint() {
  console.log('═'.repeat(70));
  console.log('Testing /api/tasks/all Endpoint');
  console.log('═'.repeat(70));
  console.log();

  // First, login to get a token
  console.log('1. Logging in...');
  const loginResponse = await fetch(`${API_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'test@example.com',
      password: 'password123'
    })
  });

  if (!loginResponse.ok) {
    console.log('❌ Login failed. Make sure you have a test user or update credentials.');
    return;
  }

  const { token } = await loginResponse.json();
  console.log('✅ Logged in successfully');
  console.log();

  // Get all tasks
  console.log('2. Fetching all tasks...');
  const tasksResponse = await fetch(`${API_URL}/api/tasks/all`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });

  if (!tasksResponse.ok) {
    console.log(`❌ Failed to fetch tasks: ${tasksResponse.status}`);
    return;
  }

  const data = await tasksResponse.json();
  console.log('✅ Successfully fetched all tasks');
  console.log();

  // Display summary
  console.log('═'.repeat(70));
  console.log('TASK SUMMARY');
  console.log('═'.repeat(70));
  console.log(`Total tasks:      ${data.total}`);
  console.log(`Active:           ${data.active}`);
  console.log(`Completed:        ${data.completed}`);
  console.log(`Archived:         ${data.archived}`);
  console.log(`Snoozed:          ${data.snoozed}`);
  console.log();

  // Display tasks
  if (data.tasks.length > 0) {
    console.log('═'.repeat(70));
    console.log('ALL TASKS');
    console.log('═'.repeat(70));

    data.tasks.forEach((task, i) => {
      console.log(`\n${i + 1}. ${task.title}`);
      console.log(`   Status:    ${task.status.toUpperCase()}`);
      console.log(`   Priority:  ${task.priority}`);
      console.log(`   Category:  ${task.category || 'none'}`);
      if (task.due_date) {
        console.log(`   Due:       ${task.due_date}${task.due_time ? ' ' + task.due_time : ''}`);
      }
      console.log(`   Created:   ${new Date(task.created_at).toLocaleString()}`);
    });
  } else {
    console.log('No tasks found.');
  }

  console.log();
  console.log('═'.repeat(70));
  console.log('✅ TEST COMPLETE');
  console.log('═'.repeat(70));
}

testTasksAllEndpoint().catch(err => {
  console.error('❌ Test failed:', err.message);
  process.exit(1);
});
