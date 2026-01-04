/**
 * Star Color Logic Test
 * 
 * Simulates Android Star.kt color logic to verify before deployment.
 * This tests the PROPOSED hybrid logic (priority + time based)
 */

// Simulate Android Temperature enum
const Temperature = {
  BLUE: 'BLUE',
  ORANGE: 'ORANGE', 
  RED: 'RED'
};

// CURRENT LOGIC (time-based only) - should FAIL for P1 without dueDate
function currentColorLogic(urgency, dueDate, dueIn) {
  if (dueDate === null) return Temperature.BLUE;
  if (dueIn < 0) return Temperature.RED;
  if (dueIn < 120) return Temperature.ORANGE;
  return Temperature.BLUE;
}

// PROPOSED LOGIC (hybrid: priority + time)
function proposedColorLogic(urgency, dueDate, dueIn) {
  // P1 = ALWAYS urgent (user explicitly said urgently/asap/critical)
  if (urgency === 1) return Temperature.RED;
  // No urgency keyword, no date = calm
  if (dueDate === null) return Temperature.BLUE;
  // Overdue
  if (dueIn < 0) return Temperature.RED;
  // Due within 2 hours
  if (dueIn < 120) return Temperature.ORANGE;
  // Future
  return Temperature.BLUE;
}

describe('Star Color Logic - Hybrid Approach', () => {
  
  describe('Current Logic (should show failures)', () => {
    
    test('"call mom urgently" - P1, no dueDate - CURRENT gives BLUE (wrong!)', () => {
      const color = currentColorLogic(1, null, 0);
      // This SHOULD be RED, but current logic gives BLUE
      expect(color).toBe(Temperature.BLUE); // Current behavior
    });
    
  });

  describe('Proposed Logic (should all pass)', () => {
    
    test('"call mom urgently" - P1, no dueDate - should be RED', () => {
      const color = proposedColorLogic(1, null, 0);
      expect(color).toBe(Temperature.RED);
    });

    test('"email boss asap" - P1, no dueDate - should be RED', () => {
      const color = proposedColorLogic(1, null, 0);
      expect(color).toBe(Temperature.RED);
    });

    test('"FIX BUG NOW" - P1, no dueDate - should be RED', () => {
      const color = proposedColorLogic(1, null, 0);
      expect(color).toBe(Temperature.RED);
    });

    test('"meeting in 10 minutes" - P1 (due soon), has dueDate - should be RED', () => {
      const color = proposedColorLogic(1, Date.now() + 600000, 10);
      expect(color).toBe(Temperature.RED);
    });

    test('"buy groceries tomorrow" - P2, has dueDate far - should be BLUE', () => {
      const color = proposedColorLogic(2, Date.now() + 86400000, 1440);
      expect(color).toBe(Temperature.BLUE);
    });

    test('"do laundry" - P2, no dueDate - should be BLUE', () => {
      const color = proposedColorLogic(2, null, 0);
      expect(color).toBe(Temperature.BLUE);
    });

    test('"low priority cleanup" - P3, no dueDate - should be BLUE', () => {
      const color = proposedColorLogic(3, null, 0);
      expect(color).toBe(Temperature.BLUE);
    });

    test('Overdue task - P2, dueIn negative - should be RED', () => {
      const color = proposedColorLogic(2, Date.now() - 3600000, -60);
      expect(color).toBe(Temperature.RED);
    });

    test('Due in 1 hour - P2, dueIn=60 - should be ORANGE', () => {
      const color = proposedColorLogic(2, Date.now() + 3600000, 60);
      expect(color).toBe(Temperature.ORANGE);
    });

    test('Due in 3 hours - P2, dueIn=180 - should be BLUE', () => {
      const color = proposedColorLogic(2, Date.now() + 10800000, 180);
      expect(color).toBe(Temperature.BLUE);
    });

  });

  describe('User Exact Inputs', () => {
    
    test('Input 1: "Email manager in 10 minutes" - should be RED (P1 due soon)', () => {
      // Backend: priority=1, dueDate=NOW+10min
      const color = proposedColorLogic(1, Date.now() + 600000, 10);
      expect(color).toBe(Temperature.RED);
    });

    test('Input 2: "call mom urgently" - should be RED (P1 explicit)', () => {
      // Backend: priority=1, dueDate=null
      const color = proposedColorLogic(1, null, 0);
      expect(color).toBe(Temperature.RED);
    });

    test('Input 3: "Complete email tasks in 10m" - should be RED (P1 due soon)', () => {
      // Backend: priority=1, dueDate=NOW+10min
      const color = proposedColorLogic(1, Date.now() + 600000, 10);
      expect(color).toBe(Temperature.RED);
    });

  });

});
