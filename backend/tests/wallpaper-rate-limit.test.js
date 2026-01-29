const request = require('supertest');
const express = require('express');
const { wallpaperLimiter } = require('../middleware/rate-limit');

describe('Wallpaper Rate Limit', () => {
    let app;

    beforeAll(() => {
        app = express();
        // Trust proxy for rate limiter to work correctly in test env if needed
        app.set('trust proxy', 1);
        app.use('/test-wallpaper', wallpaperLimiter, (req, res) => {
            res.status(200).send('OK');
        });
    });

    it('should allow more than 20 requests per hour (testing 50)', async () => {
        const requests = [];
        for (let i = 0; i < 50; i++) {
            requests.push(request(app).get('/test-wallpaper'));
        }

        const responses = await Promise.all(requests);

        // Check if all requests succeeded
        const failed = responses.filter(r => r.status === 429);

        if (failed.length > 0) {
            console.error(`Failed requests: ${failed.length}`);
        }

        expect(failed.length).toBe(0);
        responses.forEach(res => {
            expect(res.status).toBe(200);
        });
    });
});
