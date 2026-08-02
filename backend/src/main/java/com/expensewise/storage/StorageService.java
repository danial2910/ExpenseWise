package com.expensewise.storage;

import java.time.Duration;

/**
 * The seam between any module that stores a file (Profile's avatar today,
 * the Receipt module later) and whichever object store backs it —
 * currently Supabase Storage (SupabaseStorageService). Tests replace this
 * with a mock so no real network call is made. Mirrors the
 * AiChatClient/GroqChatClient seam used for the AI module.
 */
public interface StorageService {

    /**
     * Uploads (or overwrites) the object at {@code path} in the private
     * bucket.
     */
    void upload(String path, byte[] content, String contentType);

    /**
     * Generates a time-limited signed URL to read the object at
     * {@code path}. Callers should request a fresh URL on every read
     * rather than caching one, since it expires after {@code expiry}.
     */
    String generateSignedUrl(String path, Duration expiry);

    /**
     * Deletes the object at {@code path}. A no-op if it doesn't exist.
     */
    void delete(String path);
}
