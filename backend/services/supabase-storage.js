/**
 * Supabase Storage Service
 * Handles file uploads to Supabase Storage buckets
 */

const { createClient } = require('@supabase/supabase-js');

// Initialize Supabase client
const supabaseUrl = process.env.SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseServiceKey = process.env.SUPABASE_SERVICE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;

let supabase = null;

if (supabaseUrl && supabaseServiceKey) {
  supabase = createClient(supabaseUrl, supabaseServiceKey);
  console.log('[Supabase Storage] Client initialized');
} else {
  console.warn('[Supabase Storage] Missing SUPABASE_URL or SUPABASE_SERVICE_KEY');
}

const BUCKET_NAME = 'wallpapers';

/**
 * Upload a file buffer to Supabase Storage
 * @param {Buffer} fileBuffer - The file data
 * @param {string} fileName - Name for the file (e.g., "user-123-timestamp.jpg")
 * @param {string} mimeType - MIME type of the file
 * @returns {Promise<{url: string, path: string}>} - Public URL and storage path
 */
async function uploadFile(fileBuffer, fileName, mimeType) {
  if (!supabase) {
    throw new Error('Supabase Storage not configured');
  }

  const filePath = `custom/${fileName}`;

  // Upload to Supabase Storage
  const { data, error } = await supabase.storage
    .from(BUCKET_NAME)
    .upload(filePath, fileBuffer, {
      contentType: mimeType,
      upsert: true // Overwrite if exists
    });

  if (error) {
    console.error('[Supabase Storage] Upload error:', error);
    throw new Error(`Storage upload failed: ${error.message}`);
  }

  // Get public URL
  const { data: urlData } = supabase.storage
    .from(BUCKET_NAME)
    .getPublicUrl(filePath);

  console.log('[Supabase Storage] File uploaded:', filePath);

  return {
    url: urlData.publicUrl,
    path: filePath
  };
}

/**
 * Delete a file from Supabase Storage
 * @param {string} filePath - The storage path to delete
 */
async function deleteFile(filePath) {
  if (!supabase) {
    throw new Error('Supabase Storage not configured');
  }

  const { error } = await supabase.storage
    .from(BUCKET_NAME)
    .remove([filePath]);

  if (error) {
    console.error('[Supabase Storage] Delete error:', error);
    throw new Error(`Storage delete failed: ${error.message}`);
  }

  console.log('[Supabase Storage] File deleted:', filePath);
}

/**
 * Get a signed URL for a private file (if bucket is private)
 * @param {string} filePath - The storage path
 * @param {number} expiresIn - Expiration in seconds (default 1 hour)
 */
async function getSignedUrl(filePath, expiresIn = 3600) {
  if (!supabase) {
    throw new Error('Supabase Storage not configured');
  }

  const { data, error } = await supabase.storage
    .from(BUCKET_NAME)
    .createSignedUrl(filePath, expiresIn);

  if (error) {
    console.error('[Supabase Storage] Signed URL error:', error);
    throw new Error(`Failed to create signed URL: ${error.message}`);
  }

  return data.signedUrl;
}

/**
 * Check if Supabase Storage is configured
 */
function isConfigured() {
  return supabase !== null;
}

module.exports = {
  uploadFile,
  deleteFile,
  getSignedUrl,
  isConfigured,
  BUCKET_NAME
};
