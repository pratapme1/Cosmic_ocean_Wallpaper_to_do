const http = require('http');
const https = require('https'); // For uploading to external URL if needed, but here we mock or use localhost
const fs = require('fs');
const path = require('path');
const FormData = require('form-data');

// Config
const PORT = process.env.PORT || 3000;
const HOST = 'localhost';

// Helper: HTTP Request Wrapper
function request(method, path, headers = {}, body = null, isMultipart = false) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: HOST,
            port: PORT,
            path: path,
            method: method,
            headers: headers
        };

        const req = http.request(options, (res) => {
            let responseBody = '';
            res.on('data', (chunk) => responseBody += chunk);
            res.on('end', () => {
                try {
                    const parsed = responseBody ? JSON.parse(responseBody) : {};
                    resolve({ statusCode: res.statusCode, body: parsed });
                } catch (e) {
                    resolve({ statusCode: res.statusCode, body: responseBody });
                }
            });
        });

        req.on('error', reject);

        if (body) {
            if (isMultipart) {
                body.pipe(req); // Pipe form data
            } else {
                req.write(typeof body === 'object' ? JSON.stringify(body) : body);
            }
        }
        if (!isMultipart) req.end();
    });
}

async function run() {
    console.log('🚀 Starting Rapid User Flow Test...');
    const startTotal = Date.now();

    try {
        // 0. Setup: Register User
        console.log('\n👤 0. Registering Test User...');
        const email = `rapid${Date.now()}@test.com`;
        const regRes = await request('POST', '/api/auth/register', { 'Content-Type': 'application/json' }, {
            email, password: 'password123', name: 'Rapid User'
        });
        const token = regRes.body.accessToken;
        if (!token) {
            console.error('Registration Failed:', regRes.statusCode, JSON.stringify(regRes.body));
            throw new Error('Failed to get token');
        }
        const authHeaders = { 'Authorization': `Bearer ${token}` };
        console.log('✅ User registered');

        // 1. Upload Wallpaper
        console.log('\n🎨 1. Uploading Wallpaper 1...');
        const form1 = new FormData();
        // Assuming test_image.png exists in current dir or known location
        const imagePath = path.join(__dirname, 'test_image.png');
        if (!fs.existsSync(imagePath)) {
            // Create dummy image if valid png not found
            fs.writeFileSync(imagePath, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64'));
        }
        form1.append('image', fs.createReadStream(imagePath));

        const up1Start = Date.now();
        const up1Res = await request('POST', '/api/user/wallpaper', { ...authHeaders, ...form1.getHeaders() }, form1, true);
        console.log(`✅ Upload 1 Status: ${up1Res.statusCode} (${Date.now() - up1Start}ms)`);


        // 2. Create Task 1
        console.log('\n📝 2. Creating Task 1...');
        const t1Start = Date.now();
        const t1Res = await request('POST', '/api/tasks', { ...authHeaders, 'Content-Type': 'application/json' }, {
            title: 'Task 1 Immediate Change',
            priority: 3
        });
        const taskId1 = t1Res.body.id;
        console.log(`✅ Create Task 1 Status: ${t1Res.statusCode} (${Date.now() - t1Start}ms)`);

        // 3. Upload Another Wallpaper
        console.log('\n🎨 3. Uploading Wallpaper 2...');
        const form2 = new FormData();
        form2.append('image', fs.createReadStream(imagePath));
        const up2Start = Date.now();
        const up2Res = await request('POST', '/api/user/wallpaper', { ...authHeaders, ...form2.getHeaders() }, form2, true);
        console.log(`✅ Upload 2 Status: ${up2Res.statusCode} (${Date.now() - up2Start}ms)`);

        // 4. Complete Task 1
        console.log('\n✅ 4. Completing Task 1...');
        const c1Start = Date.now();
        const c1Res = await request('PATCH', `/api/tasks/${taskId1}`, { ...authHeaders, 'Content-Type': 'application/json' }, {
            completed: true
        });
        console.log(`✅ Complete Task 1 Status: ${c1Res.statusCode} (${Date.now() - c1Start}ms)`);

        // 5. Create Task 2
        console.log('\n📝 5. Creating Task 2...');
        const t2Start = Date.now();
        const t2Res = await request('POST', '/api/tasks', { ...authHeaders, 'Content-Type': 'application/json' }, {
            title: 'Task 2 New Item',
            priority: 2
        });
        const taskId2 = t2Res.body.id;
        console.log(`✅ Create Task 2 Status: ${t2Res.statusCode} (${Date.now() - t2Start}ms)`);

        // 6. Update Task 2
        console.log('\n✏️  6. Updating Task 2...');
        const u2Start = Date.now();
        const u2Res = await request('PATCH', `/api/tasks/${taskId2}`, { ...authHeaders, 'Content-Type': 'application/json' }, {
            priority: 1
        });
        console.log(`✅ Update Task 2 Status: ${u2Res.statusCode} (${Date.now() - u2Start}ms)`);

        // 7. Upload Wallpaper 3
        console.log('\n🎨 7. Uploading Wallpaper 3...');
        const form3 = new FormData();
        form3.append('image', fs.createReadStream(imagePath));
        const up3Start = Date.now();
        const up3Res = await request('POST', '/api/user/wallpaper', { ...authHeaders, ...form3.getHeaders() }, form3, true);
        console.log(`✅ Upload 3 Status: ${up3Res.statusCode} (${Date.now() - up3Start}ms)`);

        const totalTime = (Date.now() - startTotal) / 1000;
        console.log(`\n🏁 Total Time: ${totalTime.toFixed(2)}s`);

        if (totalTime < 60) {
            console.log('✅ SUCCESS: Completed under 60 seconds.');
        } else {
            console.log('⚠️  WARNING: Took longer than 60 seconds.');
        }

    } catch (err) {
        console.error('❌ FAIL:', err);
    }
}

run();
