/**
 * Verify time is actually rendered in the images
 * Extract bottom portion of each image to check
 */

import { createCanvas, loadImage } from 'canvas';
import fs from 'fs';

const devices = [
  { file: '/tmp/FINAL-your-device.png', name: 'Your Device', height: 876 },
  { file: '/tmp/FINAL-samsung-s24.png', name: 'Samsung S24', height: 2340 },
  { file: '/tmp/FINAL-nothing-phone-1.png', name: 'Nothing Phone 1', height: 2400 },
];

console.log('═'.repeat(70));
console.log('VERIFYING TIME IS VISIBLE IN GENERATED WALLPAPERS');
console.log('═'.repeat(70));
console.log();

for (const device of devices) {
  console.log(`📱 ${device.name}`);

  const img = await loadImage(device.file);

  // Extract bottom 600px to see time zone
  const extractHeight = Math.min(600, device.height);
  const startY = device.height - extractHeight;

  const canvas = createCanvas(img.width, extractHeight);
  const ctx = canvas.getContext('2d');

  // Draw bottom portion
  ctx.drawImage(
    img,
    0, startY,           // Source X, Y
    img.width, extractHeight,  // Source width, height
    0, 0,                // Dest X, Y
    img.width, extractHeight   // Dest width, height
  );

  const outputFile = `/tmp/VERIFY-BOTTOM-${device.name.toLowerCase().replace(/\s+/g, '-')}.png`;
  fs.writeFileSync(outputFile, canvas.toBuffer('image/png'));

  console.log(`   ✅ Extracted bottom ${extractHeight}px → ${outputFile}`);
  console.log();
}

console.log('═'.repeat(70));
console.log('NOW VISUALLY CHECK:');
console.log('  /tmp/VERIFY-BOTTOM-your-device.png');
console.log('  /tmp/VERIFY-BOTTOM-samsung-s24.png');
console.log('  /tmp/VERIFY-BOTTOM-nothing-phone-1.png');
console.log('');
console.log('Should see time (e.g., "11:15 PM") at bottom of each image');
console.log('═'.repeat(70));
