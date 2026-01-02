/**
 * Jest Global Setup
 * Runs before all tests
 */

// Set test timeout to 10 seconds
jest.setTimeout(10000);

// Suppress console logs during tests (optional)
// global.console = {
//   ...console,
//   log: jest.fn(),
//   error: jest.fn(),
//   warn: jest.fn(),
//   info: jest.fn(),
//   debug: jest.fn(),
// };

// Global test utilities
global.testUtils = {
  /**
   * Wait for a condition to be true
   */
  waitFor: async (condition, timeout = 5000) => {
    const startTime = Date.now();
    while (!condition()) {
      if (Date.now() - startTime > timeout) {
        throw new Error('Timeout waiting for condition');
      }
      await new Promise(resolve => setTimeout(resolve, 100));
    }
  },

  /**
   * Generate random email for testing
   */
  randomEmail: () => {
    return `test_${Date.now()}_${Math.random().toString(36).substring(7)}@example.com`;
  },

  /**
   * Generate random string
   */
  randomString: (length = 10) => {
    return Math.random().toString(36).substring(2, length + 2);
  }
};
