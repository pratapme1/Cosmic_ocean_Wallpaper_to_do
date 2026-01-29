const { parseTask } = require('../utils/task-parser');

describe('Smarter Categorization Logic', () => {

    test('1. Context Tag Weight: "@home" should override "Meeting" keyword', () => {
        // "Meeting" is work (+1), "@home" is personal (+5)
        const result = parseTask('Meeting @home');
        expect(result.category).toBe('personal');
    });

    test('2. Synonym Match: "Training session" should be health', () => {
        // "Training" added to health
        const result = parseTask('Training session');
        expect(result.category).toBe('health');
    });

    test('3. Tie-Breaker: "Health report" should be health (Priority: Health > Work)', () => {
        // "Health" is health (+1), "Report" is work (+1)
        // Tie-breaker priority: health > work
        const result = parseTask('Health report');
        expect(result.category).toBe('health');
    });

    test('4. Multiple Keywords: "Client meeting report" should be work', () => {
        const result = parseTask('Client meeting report');
        expect(result.category).toBe('work');
    });

    test('5. Errands Match: "Grocery shopping"', () => {
        const result = parseTask('Grocery shopping');
        expect(result.category).toBe('errands');
    });

    test('6. Finance Match: "Pay electricity bill"', () => {
        const result = parseTask('Pay electricity bill');
        expect(result.category).toBe('finance');
    });

    test('7. Partial Match Prevention: "ear" should not trigger learning (from "learning")', () => {
        // Word boundary test
        const result = parseTask('Go near the park');
        expect(result.category).toBe('general');
    });

    test('8. Context Tag in Text: "Workout @gym"', () => {
        const result = parseTask('Workout @gym');
        expect(result.category).toBe('health');
    });
});
