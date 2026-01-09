/**
 * Test Privacy Settings reflected in Wallpaper Generation
 */
import fs from 'fs';

// Node.js 20 has built-in fetch

const API = 'https://cosmic-ocean-api.vercel.app';

async function testWallpaperPrivacy() {
  // Login first
  console.log('🔐 Logging in...');
  const loginRes = await fetch(API + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'test-e2e@cosmic.com',
      password: 'testpass123'
    })
  });

  if (!loginRes.ok) {
    throw new Error('Login failed: ' + await loginRes.text());
  }

  const { accessToken } = await loginRes.json();
  console.log('✅ Logged in');

  // Check current privacy settings
  console.log('\n📋 Current Privacy Settings:');
  const prefsRes = await fetch(API + '/api/user/preferences', {
    headers: { 'Authorization': 'Bearer ' + accessToken }
  });
  const prefs = await prefsRes.json();
  console.log(JSON.stringify(prefs, null, 2));

  // Set hide_all_tasks_mode to true
  console.log('\n🔒 Setting hide_all_tasks_mode = true...');
  const updateRes = await fetch(API + '/api/user/preferences', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + accessToken
    },
    body: JSON.stringify({ hide_all_tasks_mode: true })
  });

  if (!updateRes.ok) {
    console.log('❌ Update failed:', await updateRes.text());
  } else {
    console.log('✅ Privacy setting updated');
  }

  // Verify the setting was saved
  console.log('\n📋 Verified Settings:');
  const verifyRes = await fetch(API + '/api/user/preferences', {
    headers: { 'Authorization': 'Bearer ' + accessToken }
  });
  const verifiedPrefs = await verifyRes.json();
  console.log('hide_all_tasks_mode:', verifiedPrefs.hide_all_tasks_mode);

  // Generate wallpaper with hide_all=true
  console.log('\n🖼️ Generating wallpaper (hide_all=true)...');
  const wallpaperRes = await fetch(API + '/api/wallpaper?resolution=720x1280&theme=cosmic&timezone=Asia/Kolkata', {
    headers: { 'Authorization': 'Bearer ' + accessToken }
  });

  if (wallpaperRes.ok) {
    const buffer = Buffer.from(await wallpaperRes.arrayBuffer());
    console.log('✅ Wallpaper generated: ' + buffer.length + ' bytes');
    fs.writeFileSync('/tmp/privacy-wallpaper-hide-all.png', buffer);
    console.log('   Saved to /tmp/privacy-wallpaper-hide-all.png');
  } else {
    console.log('❌ Wallpaper failed:', await wallpaperRes.text());
  }

  // Reset hide_all_tasks_mode to false
  console.log('\n🔓 Resetting hide_all_tasks_mode = false...');
  await fetch(API + '/api/user/preferences', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + accessToken
    },
    body: JSON.stringify({ hide_all_tasks_mode: false })
  });
  console.log('✅ Reset complete');

  // Generate wallpaper again
  console.log('\n🖼️ Generating wallpaper (hide_all=false)...');
  const wallpaper2Res = await fetch(API + '/api/wallpaper?resolution=720x1280&theme=cosmic&timezone=Asia/Kolkata', {
    headers: { 'Authorization': 'Bearer ' + accessToken }
  });

  if (wallpaper2Res.ok) {
    const buffer = Buffer.from(await wallpaper2Res.arrayBuffer());
    console.log('✅ Wallpaper generated: ' + buffer.length + ' bytes');
    fs.writeFileSync('/tmp/privacy-wallpaper-normal.png', buffer);
    console.log('   Saved to /tmp/privacy-wallpaper-normal.png');
  }

  console.log('\n✅ TEST COMPLETE');
  console.log('Compare wallpapers:');
  console.log('  /tmp/privacy-wallpaper-hide-all.png (should show NO tasks)');
  console.log('  /tmp/privacy-wallpaper-normal.png (should show tasks)');
}

testWallpaperPrivacy().catch(console.error);
