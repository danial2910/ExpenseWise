package com.expensewise.storage;

import com.expensewise.config.SupabaseProperties;
import com.expensewise.exception.StorageUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls Supabase Storage's REST API directly via RestClient — no Supabase
 * SDK dependency, same "boring, explicit code, one RestClient call" choice
 * already made for Brevo (MailServiceImpl) and Groq (GroqChatClient). The
 * bucket is private; every read goes through a short-lived signed URL,
 * never a public URL. serviceKey is the Supabase service_role key and is
 * only ever used here, on the backend — it must never reach frontend code.
 */
@Component
public class SupabaseStorageService implements StorageService {

    private final RestClient restClient;
    private final String storageBaseUrl;
    private final String bucket;

    public SupabaseStorageService(SupabaseProperties supabaseProperties) {
        this.storageBaseUrl = supabaseProperties.url() + "/storage/v1";
        this.restClient = RestClient.builder()
                .baseUrl(storageBaseUrl)
                .defaultHeader("Authorization", "Bearer " + supabaseProperties.serviceKey())
                .defaultHeader("apikey", supabaseProperties.serviceKey())
                .build();
        this.bucket = supabaseProperties.bucket();
    }

    @Override
    public void upload(String path, byte[] content, String contentType) {
        try {
            restClient.post()
                    .uri(objectUri(path))
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new StorageUnavailableException("Could not upload the file. Please try again.");
        }
    }

    @Override
    public String generateSignedUrl(String path, Duration expiry) {
        try {
            SignedUrlResponse response = restClient.post()
                    .uri("/object/sign/" + bucket + "/" + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiry.toSeconds()))
                    .retrieve()
                    .body(SignedUrlResponse.class);

            if (response == null || response.signedURL() == null) {
                throw new StorageUnavailableException("Could not generate a link to the file.");
            }
            // Supabase returns a path relative to /storage/v1 (e.g.
            // "/object/sign/<bucket>/<path>?token=..."), not a full URL.
            return storageBaseUrl + response.signedURL();
        } catch (RestClientException ex) {
            throw new StorageUnavailableException("Could not generate a link to the file.");
        }
    }

    @Override
    public void delete(String path) {
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("/object/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DeleteRequest(List.of(path)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new StorageUnavailableException("Could not delete the file. Please try again.");
        }
    }

    /**
     * Builds the URI by string concatenation rather than a {path} template
     * variable — RestClient/UriComponentsBuilder percent-encodes "/" inside
     * a single template variable's value into "%2F", which Supabase's sign
     * endpoint then embeds verbatim into the signed token's "url" claim,
     * causing a later InvalidSignature on download (confirmed against a
     * real bucket). path is always our own generated value
     * (avatars/{userId}/{uuid}.ext) — never user input — so it's safe to
     * concatenate without its own escaping.
     */
    private String objectUri(String path) {
        return "/object/" + bucket + "/" + path;
    }

    private record SignedUrlResponse(String signedURL) {
    }

    private record DeleteRequest(List<String> prefixes) {
    }
}
