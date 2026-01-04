/**
 * Debug: Calculate actual layout positions for Samsung S24
 */

import { getLayoutConfig } from './services/layout-system.js';

const width = 1080;
const height = 2340;

const layout = getLayoutConfig(width, height);

console.log('Samsung S24 Layout Analysis:');
console.log('Screen:', width, 'x', height);
console.log('Density:', layout.density);
console.log('');
console.log('Task Zone:');
console.log('  Y start:', layout.layoutZones.task.y);
console.log('  Height:', layout.layoutZones.task.height);
console.log('  Y end:', layout.layoutZones.task.y + layout.layoutZones.task.height);
console.log('');
console.log('Interaction Zone:');
console.log('  Y start:', layout.layoutZones.interaction.y);
console.log('  Height:', layout.layoutZones.interaction.height);
console.log('');
console.log('Navigation Zone:');
console.log('  Y start:', layout.layoutZones.navigation.y);
console.log('  Height:', layout.layoutZones.navigation.height);
console.log('');
console.log('Typography (density ' + layout.density + 'x):');
console.log('  displayLarge:', layout.typography.displayLarge, 'px');
console.log('  bodyLarge:', layout.typography.bodyLarge, 'px');
console.log('');
console.log('SAFE ZONE for time:');
console.log('  Must be between Y=' + layout.layoutZones.task.y + ' and Y=' + (layout.layoutZones.task.y + layout.layoutZones.task.height - 100));
