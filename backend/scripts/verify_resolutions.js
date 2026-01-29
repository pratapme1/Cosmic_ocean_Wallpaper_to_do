const fs = require('fs');
const path = require('path');
const { generateEnhancedWallpaper } = require('../services/wallpaper-generator-enhanced');

// Mock Data
const tasks = [
    { title: "Review Q1 Reports", priority: 1, estimate_minutes: 45, due_date: new Date().toISOString() },
    { title: "Team Sync", priority: 2, estimate_minutes: 30, category: 'work' },
    { title: "Buy Groceries", priority: 3, estimate_minutes: 60, category: 'personal' },
    { title: "Call Mom", priority: 2, estimate_minutes: 15, category: 'family' }, // 4th task to trigger "+1 more"
    { title: "Walk Dog", priority: 3, category: 'health' } // 5th task
];

const user = {
    id: "test-user",
    theme: "cosmic",
    hide_all_tasks_mode: false
};

const resolutions = [
    { name: 'Legacy_480x800', res: '480x800' },     // Very old Android
    { name: 'HD_720x1280', res: '720x1280' },       // Standard budget
    { name: 'Tall_1080x2400', res: '1080x2400' },   // Modern mid-range (20:9)
    { name: 'UltraTall_1080x2520', res: '1080x2520' }, // Sony Xperia (21:9)
    { name: 'QHD_1440x2560', res: '1440x2560' },    // Older Flagship
    { name: '4K_1644x3840', res: '1644x3840' }      // High-end (Sony)
];

async function generate() {
    console.log("🎨 Generating Verification Wallpapers...");

    for (const { name, res } of resolutions) {
        console.log(`Processing ${name} (${res})...`);
        user.resolution = res;

        try {
            const buffer = await generateEnhancedWallpaper(user, { tasks });
            const outputPath = path.join(__dirname, `../test_output_${name}.png`);
            fs.writeFileSync(outputPath, buffer);
            console.log(`✅ Saved: ${outputPath}`);
        } catch (e) {
            console.error(`❌ Failed ${name}:`, e);
        }
    }
}

generate();
