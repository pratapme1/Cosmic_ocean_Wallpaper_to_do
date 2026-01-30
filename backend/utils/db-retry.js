/**
 * Database Retry Utility
 * Provides exponential backoff retry logic for database operations
 */

/**
 * Sleep utility for delays
 * @param {number} ms - Milliseconds to sleep
 */
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Retry a database operation with exponential backoff
 * @param {Function} operation - Async function to execute
 * @param {Object} options - Retry configuration
 * @param {number} options.maxRetries - Maximum number of retry attempts (default: 3)
 * @param {number} options.initialDelay - Initial delay in ms (default: 100)
 * @param {number} options.maxDelay - Maximum delay in ms (default: 5000)
 * @param {number} options.backoffMultiplier - Multiplier for exponential backoff (default: 2)
 * @param {Function} options.shouldRetry - Function to determine if error should be retried
 * @returns {Promise<any>} - Result of the operation
 */
async function retryOperation(operation, options = {}) {
  const {
    maxRetries = 3,
    initialDelay = 500, // Wait longer before first retry during a storm
    maxDelay = 10000,
    backoffMultiplier = 2.5,
    shouldRetry = (err) => {
      // Retry on connection errors, timeouts, deadlocks
      const retryableErrors = [
        'ECONNREFUSED',
        'ECONNRESET',
        'ETIMEDOUT',
        'ENOTFOUND',
        'Connection terminated',
        'deadlock detected',
        'could not serialize access',
        'timeout exceeded'  // pg-pool connection timeout
      ];

      return retryableErrors.some(msg =>
        err.message?.includes(msg) || err.code?.includes(msg)
      );
    }
  } = options;

  let lastError;
  let delay = initialDelay;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      // Execute the operation
      const result = await operation();

      // Log success if this was a retry
      if (attempt > 0) {
        console.log(`✅ Database operation succeeded on attempt ${attempt + 1}`);
      }

      return result;
    } catch (err) {
      lastError = err;

      // Check if we should retry
      if (attempt < maxRetries && shouldRetry(err)) {
        const isTimeout = err.message?.includes('timeout');
        console.warn(`⚠️  Database ${isTimeout ? 'TIMEOUT' : 'FAIL'} (attempt ${attempt + 1}/${maxRetries + 1}), retrying in ${delay}ms:`, err.message);

        // Wait before retrying
        await sleep(delay);

        // Exponential backoff with max delay cap
        delay = Math.min(delay * backoffMultiplier, maxDelay);
      } else {
        // Don't retry - either max retries reached or non-retryable error
        if (attempt >= maxRetries) {
          console.error(`❌ Database operation failed after ${maxRetries + 1} attempts:`, err.message);
        }
        throw err;
      }
    }
  }

  throw lastError;
}

/**
 * Wrapper to make query operations retryable
 * @param {Object} client - Database client (Pool or Client)
 * @param {string} query - SQL query
 * @param {Array} params - Query parameters
 * @param {Object} retryOptions - Retry configuration
 */
async function queryWithRetry(client, query, params = [], retryOptions = {}) {
  return retryOperation(
    () => client.query(query, params),
    retryOptions
  );
}

module.exports = {
  retryOperation,
  queryWithRetry,
  sleep
};
