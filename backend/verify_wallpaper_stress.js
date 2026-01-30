const http = require('http');
const querystring = require('querystring');

// Config
const PORT = process.env.PORT || 3000;
const HOST = 'localhost';
const CONCURRENCY = 10; // "Zero Failure" standard

function request(options, postData = null) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', (chunk) => body += chunk);
            res.on('end', () => resolve({ statusCode: res.statusCode, body }));
        });
        req.on('error', reject);
        if (postData) req.write(postData);
        req.end();
    });
}

async function run() {
    console.log('🚀 Starting Zero-Failure Stress Test...');
    try {
        // 1. Register User
        console.log('👤 Creating Test User...');
        const email = `stress${Date.now()}@test.com`;
        const regData = JSON.stringify({ email, password: 'password123', name: 'Stress User' });

        const regRes = await request({
            hostname: HOST, port: PORT, path: '/api/auth/register', method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Content-Length': regData.length }
        }, regData);

        const token = JSON.parse(regRes.body).token;
        if (!token) throw new Error('Failed to get token: ' + regRes.body);
        console.log('✅ User registered');

        // 2. Hammer Wallpaper Endpoint
        console.log(`🔨 Launching ${CONCURRENCY} concurrent wallpaper requests...`);
        const promises = [];
        const start = Date.now();

        for (let i = 0; i < CONCURRENCY; i++) {
            promises.push(request({
                hostname: HOST, port: PORT, path: '/api/wallpaper?resolution=1080x1920', method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` }
            }).then(res => {
                const time = Date.now() - start;
                console.log(`  ➡ Request ${i + 1}: Status ${res.statusCode} (${time}ms)`);
                return res;
            }));
        }

        const results = await Promise.all(promises);
        const successCount = results.filter(r => r.statusCode === 200).length;
        const failCount = results.length - successCount;

        console.log('\n📊 Results:');
        console.log(`  Total: ${results.length}`);
        console.log(`  Success: ${successCount}`);
        console.log(`  Failures: ${failCount}`);

        if (failCount === 0) {
            console.log('✅ PASS: Zero failures under load.');
        } else {
            console.error('❌ FAIL: Some requests failed.');
        }

    } catch (err) {
        console.error('❌ CRITICAL FAIL:', err.message);
    }
}

run();
