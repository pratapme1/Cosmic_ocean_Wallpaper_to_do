/**
 * Calculate exact Y positions for time on each device
 */

import { getLayoutConfig } from './services/layout-system.js';

const dp = (value, density) => Math.floor(value * density);

const devices = [
  { name: 'Your Device', width: 393, height: 876 },
  { name: 'Samsung S24', width: 1080, height: 2340 },
  { name: 'Nothing Phone 1', width: 1080, height: 2400 },
];

console.log('═'.repeat(70));
console.log('TIME POSITION ANALYSIS');
console.log('═'.repeat(70));
console.log();

for (const device of devices) {
  const layout = getLayoutConfig(device.width, device.height);
  const taskZone = layout.layoutZones.task;
  const { typography, density } = layout;

  // Current calculation (iteration 3)
  const safetyMargin = dp(80, density);
  const timeYCurrent = taskZone.y + taskZone.height - typography.displayLarge - safetyMargin;

  // What it SHOULD be (visible in bottom 600px)
  const bottomExtractStart = device.height - 600;
  const targetTimeY = bottomExtractStart + 100;  // 100px from top of bottom 600px extract

  console.log(`${device.name} (${device.width}x${device.height})`);
  console.log(`  Density: ${density}x`);
  console.log(`  Task zone: Y=${taskZone.y} to Y=${taskZone.y + taskZone.height}`);
  console.log(`  Bottom 600px: Y=${bottomExtractStart} to Y=${device.height}`);
  console.log(`  Current time Y: ${timeYCurrent} ${timeYCurrent < bottomExtractStart ? '❌ ABOVE extract' : '✅ IN extract'}`);
  console.log(`  Target time Y: ${targetTimeY} (for visibility)`);
  console.log(`  Margin needed: ${taskZone.y + taskZone.height - typography.displayLarge - targetTimeY}px = ${(taskZone.y + taskZone.height - typography.displayLarge - targetTimeY) / density}dp`);
  console.log();
}

console.log('═'.repeat(70));
console.log('RECOMMENDATION:');
console.log('Use adaptive safety margin based on screen size:');
console.log('  Small screens (< 1000px): 40-60dp');
console.log('  Large screens (>= 1000px): 20-30dp');
console.log('═'.repeat(70));
