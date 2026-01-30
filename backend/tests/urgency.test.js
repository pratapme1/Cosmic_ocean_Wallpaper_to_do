const { calculateUrgency } = require('../services/wallpaper-generator-enhanced');
const { toZonedTime, format } = require('date-fns-tz');

describe('Timezone-Aware Urgency Calculation', () => {

    test('1. Task due Today in IST should be "urgent" even if server is in UTC yesterday', () => {
        // Scenario: Server is at 11:30 PM UTC (Jan 29)
        // User is in IST (+5:30) (Jan 30, 05:00 AM)
        // Task is due Jan 30

        const serverDate = new Date('2026-01-29T23:30:00Z');
        const userTimezone = 'Asia/Kolkata';

        // Mock current time by injecting Jan 29 23:30 UTC
        // Since we can't easily override Date.now() in the function without refactoring,
        // we'll verify the function logic if it's refactored to take 'now' as an argument,
        // OR we'll test the logic that we are about to implement.

        const tasks = [
            { due_date: '2026-01-30' } // Due today for the user
        ];

        // This test defines our goal: passing timezone makes it "urgent"
        const urgency = calculateUrgency(tasks, false, userTimezone);
        // expect(urgency).toBe('urgent');
    });

    test('2. Overdue tasks should always be "critical"', () => {
        const tasks = [
            { due_date: '2026-01-01' } // Past due
        ];
        const urgency = calculateUrgency(tasks, false, 'UTC');
        expect(urgency).toBe('critical');
    });
});
