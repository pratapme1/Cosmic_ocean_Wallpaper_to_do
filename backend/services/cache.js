const { createClient } = require('redis');

/**
 * Redis Cache Service
 * Provides caching layer for wallpaper generation and other expensive operations
 */
class CacheService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.enabled = process.env.REDIS_URL || process.env.REDIS_ENABLED === 'true';
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 2000; // 2 seconds
    this.circuitBreakerOpen = false;
    this.circuitBreakerResetTime = null;
    this.circuitBreakerThreshold = 5; // Open circuit after 5 failures
    this.circuitBreakerTimeout = 30000; // 30 seconds
    this.failureCount = 0;
  }

  /**
   * Check if circuit breaker allows operations
   */
  isCircuitBreakerOpen() {
    if (!this.circuitBreakerOpen) {
      return false;
    }

    // Check if we should reset the circuit breaker
    if (Date.now() >= this.circuitBreakerResetTime) {
      console.log('🔄 Circuit breaker timeout reached, attempting to reconnect...');
      this.circuitBreakerOpen = false;
      this.failureCount = 0;
      return false;
    }

    return true;
  }

  /**
   * Record a failure and potentially open the circuit breaker
   */
  recordFailure() {
    this.failureCount++;

    if (this.failureCount >= this.circuitBreakerThreshold) {
      this.circuitBreakerOpen = true;
      this.circuitBreakerResetTime = Date.now() + this.circuitBreakerTimeout;
      console.warn(`⚠️  Redis circuit breaker OPEN (${this.failureCount} failures). Will retry after ${this.circuitBreakerTimeout / 1000}s`);
    }
  }

  /**
   * Record a success and reset failure counter
   */
  recordSuccess() {
    if (this.failureCount > 0) {
      console.log('✅ Redis operation successful, resetting failure counter');
    }
    this.failureCount = 0;
    this.circuitBreakerOpen = false;
  }

  /**
   * Initialize Redis connection
   */
  async connect() {
    if (!this.enabled) {
      console.log('ℹ️  Redis caching disabled (no REDIS_URL)');
      return;
    }

    try {
      this.client = createClient({
        url: process.env.REDIS_URL || 'redis://localhost:6379'
      });

      this.client.on('error', (err) => {
        console.error('Redis Client Error:', err);
        this.connected = false;
      });

      this.client.on('connect', () => {
        console.log('✅ Redis connected');
        this.connected = true;
      });

      await this.client.connect();
    } catch (err) {
      console.error('Failed to connect to Redis:', err);
      this.enabled = false;
      this.connected = false;
    }
  }

  /**
   * Get value from cache
   * @param {string} key - Cache key
   * @returns {Promise<any|null>} - Parsed JSON value or null
   */
  async get(key) {
    if (!this.enabled || !this.connected || this.isCircuitBreakerOpen()) {
      return null;
    }

    try {
      const value = await this.client.get(key);
      this.recordSuccess();
      return value ? JSON.parse(value) : null;
    } catch (err) {
      console.error('Cache get error:', err);
      this.recordFailure();
      return null;
    }
  }

  /**
   * Get binary value from cache (for images)
   * @param {string} key - Cache key
   * @returns {Promise<Buffer|null>} - Buffer or null
   */
  async getBuffer(key) {
    if (!this.enabled || !this.connected || this.isCircuitBreakerOpen()) {
      return null;
    }

    try {
      // Get base64-encoded string from Redis
      const value = await this.client.get(key);
      this.recordSuccess();

      if (!value) {
        return null;
      }

      // Decode from base64 to Buffer
      const buffer = Buffer.from(value, 'base64');
      return buffer;
    } catch (err) {
      console.error('Cache getBuffer error:', err);
      this.recordFailure();
      return null;
    }
  }

  /**
   * Set value in cache
   * @param {string} key - Cache key
   * @param {any} value - Value to cache (will be JSON stringified)
   * @param {number} ttl - Time to live in seconds (default: 3600 = 1 hour)
   */
  async set(key, value, ttl = 3600) {
    if (!this.enabled || !this.connected || this.isCircuitBreakerOpen()) {
      return false;
    }

    try {
      const stringValue = typeof value === 'string' ? value : JSON.stringify(value);
      await this.client.setEx(key, ttl, stringValue);
      this.recordSuccess();
      return true;
    } catch (err) {
      console.error('Cache set error:', err);
      this.recordFailure();
      return false;
    }
  }

  /**
   * Set binary value in cache (for images)
   * @param {string} key - Cache key
   * @param {Buffer} buffer - Buffer to cache
   * @param {number} ttl - Time to live in seconds (default: 3600 = 1 hour)
   */
  async setBuffer(key, buffer, ttl = 3600) {
    if (!this.enabled || !this.connected || this.isCircuitBreakerOpen()) {
      return false;
    }

    try {
      // Encode buffer to base64 string for Redis storage
      const base64String = buffer.toString('base64');
      await this.client.setEx(key, ttl, base64String);
      this.recordSuccess();
      return true;
    } catch (err) {
      console.error('Cache setBuffer error:', err);
      this.recordFailure();
      return false;
    }
  }

  /**
   * Delete key from cache
   * @param {string} key - Cache key
   */
  async del(key) {
    if (!this.enabled || !this.connected) {
      return false;
    }

    try {
      await this.client.del(key);
      return true;
    } catch (err) {
      console.error('Cache del error:', err);
      return false;
    }
  }

  /**
   * Delete multiple keys matching pattern
   * @param {string} pattern - Key pattern (e.g., "wp:user123:*")
   */
  async delPattern(pattern) {
    if (!this.enabled || !this.connected) {
      return false;
    }

    try {
      const keys = await this.client.keys(pattern);
      if (keys.length > 0) {
        await this.client.del(keys);
      }
      return true;
    } catch (err) {
      console.error('Cache delPattern error:', err);
      return false;
    }
  }

  /**
   * Check if key exists
   * @param {string} key - Cache key
   * @returns {Promise<boolean>}
   */
  async exists(key) {
    if (!this.enabled || !this.connected) {
      return false;
    }

    try {
      const result = await this.client.exists(key);
      return result === 1;
    } catch (err) {
      console.error('Cache exists error:', err);
      return false;
    }
  }

  /**
   * Generate wallpaper cache key
   * @param {string} userId
   * @param {string} theme
   * @param {string} resolution
   * @param {string} mode
   * @returns {string}
   */
  wallpaperKey(userId, theme = 'cosmic', resolution = '1080x1920', mode = 'one_thing') {
    return `wp:${userId}:${theme}:${resolution}:${mode}`;
  }

  /**
   * Invalidate all wallpaper cache for a user
   * @param {string} userId
   */
  async invalidateUserWallpapers(userId) {
    return this.delPattern(`wp:${userId}:*`);
  }

  /**
   * Close Redis connection
   */
  async disconnect() {
    if (this.client && this.connected) {
      await this.client.quit();
      this.connected = false;
    }
  }
}

// Singleton instance
const cacheService = new CacheService();

module.exports = cacheService;
