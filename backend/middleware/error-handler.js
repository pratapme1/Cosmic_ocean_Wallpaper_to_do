/**
 * Centralized Error Handler Middleware
 * Provides consistent error responses across all endpoints
 */

/**
 * Standard error codes
 */
const ErrorCodes = {
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  AUTHENTICATION_ERROR: 'AUTHENTICATION_ERROR',
  AUTHORIZATION_ERROR: 'AUTHORIZATION_ERROR',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  RATE_LIMIT: 'RATE_LIMIT',
  DATABASE_ERROR: 'DATABASE_ERROR',
  CACHE_ERROR: 'CACHE_ERROR',
  EXTERNAL_SERVICE_ERROR: 'EXTERNAL_SERVICE_ERROR',
  INTERNAL_ERROR: 'INTERNAL_ERROR'
};

/**
 * Custom API Error class
 */
class APIError extends Error {
  constructor(message, statusCode, errorCode, details = null) {
    super(message);
    this.name = 'APIError';
    this.statusCode = statusCode;
    this.errorCode = errorCode;
    this.details = details;
    this.timestamp = new Date().toISOString();
  }
}

/**
 * Create standardized error response
 */
function createErrorResponse(err, req) {
  const isProduction = process.env.NODE_ENV === 'production';

  const errorResponse = {
    error: {
      message: err.message || 'An unexpected error occurred',
      code: err.errorCode || ErrorCodes.INTERNAL_ERROR,
      timestamp: err.timestamp || new Date().toISOString(),
      path: req.path,
      method: req.method
    }
  };

  // Add details in development mode or if explicitly provided
  if (err.details) {
    errorResponse.error.details = err.details;
  }

  // Add stack trace in development
  if (!isProduction && err.stack) {
    errorResponse.error.stack = err.stack;
  }

  // Add request ID if available (for tracing)
  if (req.id) {
    errorResponse.error.requestId = req.id;
  }

  return errorResponse;
}

/**
 * Error handler middleware
 * Must be registered last after all routes
 */
function errorHandler(err, req, res, next) {
  // Log the error
  console.error(`[${new Date().toISOString()}] Error:`, {
    path: req.path,
    method: req.method,
    error: err.message,
    stack: err.stack
  });

  // Determine status code
  let statusCode = err.statusCode || 500;

  // Handle specific error types
  if (err.name === 'ValidationError') {
    statusCode = 400;
    err.errorCode = ErrorCodes.VALIDATION_ERROR;
  } else if (err.name === 'UnauthorizedError' || err.message.includes('token')) {
    statusCode = 401;
    err.errorCode = ErrorCodes.AUTHENTICATION_ERROR;
  } else if (err.code === 'ECONNREFUSED' || err.code === 'ENOTFOUND') {
    statusCode = 503;
    err.errorCode = ErrorCodes.EXTERNAL_SERVICE_ERROR;
    err.message = 'External service temporarily unavailable';
  } else if (err.code === '23505') { // PostgreSQL unique violation
    statusCode = 409;
    err.errorCode = ErrorCodes.CONFLICT;
    err.message = 'Resource already exists';
  } else if (err.code === '23503') { // PostgreSQL foreign key violation
    statusCode = 400;
    err.errorCode = ErrorCodes.VALIDATION_ERROR;
    err.message = 'Invalid reference to related resource';
  }

  // Create standardized response
  const errorResponse = createErrorResponse(err, req);

  // Send response
  res.status(statusCode).json(errorResponse);
}

/**
 * 404 handler for undefined routes
 */
function notFoundHandler(req, res) {
  const err = new APIError(
    `Route ${req.method} ${req.path} not found`,
    404,
    ErrorCodes.NOT_FOUND
  );

  res.status(404).json(createErrorResponse(err, req));
}

/**
 * Async route wrapper to catch errors
 * Usage: app.get('/api/route', asyncHandler(async (req, res) => { ... }))
 */
function asyncHandler(fn) {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = {
  ErrorCodes,
  APIError,
  errorHandler,
  notFoundHandler,
  asyncHandler,
  createErrorResponse
};
