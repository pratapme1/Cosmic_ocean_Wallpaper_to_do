/**
 * Diagnose where time is actually rendering
 */

import { getLayoutConfig } from './services/layout-system.js';

const devices = [
  { name: 'Samsung S24', width: 1080, height: 2340 },
  { name: 'Your Device', width: 393, height: 876 },
];

function dp(value, density) {
  return Math.floor(value * density);
}

console.log('TIME POSITION DIAGNOSIS');
console.log('═'.repeat(70));

for (const device of devices) {
  const layout = getLayoutConfig(device.width, device.height);
  const taskZone = layout.layoutZones.task;
  const { typography, margins, density } = layout;

  // Current calculation (what we're doing now)
  const timeTopMargin = dp(12, density);

  // Estimate where time renders
  const messageHeight = dp(55, density);
  const headerHeight = typography.labelLarge + margins.vertical + dp(10, density);
  const taskHeight = dp(16, density) + typography.titleLarge + dp(4, density) +
                     typography.bodyMedium + dp(16, density);
  const tasksShown = 3;
  const remainingHeight = typography.bodyMedium + dp(8, density);

  const contentBeforeTime = messageHeight + headerHeight +
                           (tasksShown * taskHeight) + remainingHeight;

  const timeYPosition = taskZone.y + margins.vertical + contentBeforeTime + timeTopMargin;
  const timeBottomY = timeYPosition + typography.displayLarge;

  // Where time SHOULD be for visibility
  const bottomExtractStart = device.height - 600;
  const safeTimeY = bottomExtractStart + 100; // 100px from top of bottom extract

  console.log(`\n${device.name} (${device.width}x${device.height})`);
  console.log(`  Density: ${density}x`);
  console.log(`  Task zone: Y=${taskZone.y} to Y=${taskZone.y + taskZone.height} (height=${taskZone.height})`);
  console.log(`  Bottom 600px extract: Y=${bottomExtractStart} to Y=${device.height}`);
  console.log();
  console.log(`  Content breakdown:`);
  console.log(`    Message: ${messageHeight}px`);
  console.log(`    Header: ${headerHeight}px`);
  console.log(`    ${tasksShown} tasks: ${tasksShown * taskHeight}px (${taskHeight}px each)`);
  console.log(`    Remaining: ${remainingHeight}px`);
  console.log(`    Margin before time: ${timeTopMargin}px`);
  console.log(`    Total: ${contentBeforeTime + timeTopMargin}px`);
  console.log();
  console.log(`  Time renders at:`);
  console.log(`    Y position: ${timeYPosition}px`);
  console.log(`    Bottom Y: ${timeBottomY}px`);
  console.log(`    Status: ${timeYPosition >= bottomExtractStart ? '✅ IN bottom 600px' : '❌ ABOVE bottom 600px'}`);
  console.log();
  console.log(`  To be visible in extract, time needs:`);
  console.log(`    Y >= ${bottomExtractStart}px`);
  console.log(`    Currently: Y=${timeYPosition}px`);
  console.log(`    Gap: ${timeYPosition - bottomExtractStart}px (${timeYPosition < bottomExtractStart ? 'NEEDS TO MOVE DOWN' : 'OK'})`);
}

console.log('\n' + '═'.repeat(70));
console.log('SOLUTION: Time needs to render 200-400px LOWER on large screens');
console.log('═'.repeat(70));
