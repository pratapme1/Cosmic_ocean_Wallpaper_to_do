
const axios = require('axios');
const fs = require('fs');
const FormData = require('form-data');
const path = require('path');

// BASE URL (Local)
const API_URL = 'http://localhost:3001';

// Test Data
const TEST_EMAIL = "test_user_" + Date.now() + "@example.com";
const TEST_PASSWORD = "password123";
let AUTH_TOKEN = "";

async function runTest() {
    console.log(">>> STARTING E2E TEST FOR CUSTOM WALLPAPER <<<");

    try {
        // 1. Register/Login
        console.log("\n1. Authentication...");
        try {
            const regRes = await axios.post(`${API_URL}/api/auth/register`, {
                email: TEST_EMAIL,
                password: TEST_PASSWORD,
                name: "Test User"
            });
            console.log("   Register Response: ", JSON.stringify(regRes.data));
            AUTH_TOKEN = regRes.data.accessToken;
            console.log("   Registered new user. Token obtained.");
        } catch (e) {
            console.log("   Register failed: " + (e.response ? JSON.stringify(e.response.data) : e.message));
            // Login if exists
            const loginRes = await axios.post(`${API_URL}/api/auth/login`, {
                email: TEST_EMAIL,
                password: TEST_PASSWORD
            });
            AUTH_TOKEN = loginRes.data.accessToken;
            console.log("   Logged in existing user. Token obtained.");
        }

        // 2. Upload Wallpaper
        console.log("\n2. Uploading Custom Wallpaper...");

        // Create a dummy image file
        const imagePath = path.join(__dirname, 'test_image.png');
        // Simple 1x1 pixel PNG
        const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==', 'base64');
        fs.writeFileSync(imagePath, pixel);

        const form = new FormData();
        form.append('image', fs.createReadStream(imagePath));

        const uploadRes = await axios.post(`${API_URL}/api/user/wallpaper`, form, {
            headers: {
                ...form.getHeaders(),
                'Authorization': `Bearer ${AUTH_TOKEN}`
            }
        });

        if (uploadRes.status === 200) {
            console.log("   Upload Successful: " + JSON.stringify(uploadRes.data));
            // Expect { message: 'Wallpaper processed and updated', ... }
        } else {
            throw new Error("Upload failed with status " + uploadRes.status);
        }

        // 3. Verify User Preferences
        console.log("\n3. Verifying User Preferences...");
        const userRes = await axios.get(`${API_URL}/api/user`, {
            headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
        });

        const prefs = userRes.data;
        console.log("   User Data: ", prefs);

        if (prefs.wallpaper_mode === 'custom') {
            console.log("   SUCCESS: wallpaper_mode is 'custom'");
        } else {
            console.error("   FAILURE: wallpaper_mode is " + prefs.wallpaper_mode);
        }

        if (prefs.custom_wallpaper_path) {
            console.log("   SUCCESS: custom_wallpaper_path is set to " + prefs.custom_wallpaper_path);
        } else {
            console.error("   FAILURE: custom_wallpaper_path is missing");
        }

        console.log("\n>>> E2E TEST COMPLETED SUCCESSFULLY <<<");

    } catch (error) {
        console.error("\n!!! TEST FAILED !!!");
        if (error.response) {
            console.error("Status:", error.response.status);
            console.error("Data:", error.response.data);
        } else {
            console.error(error.message);
        }
    }
}

runTest();
