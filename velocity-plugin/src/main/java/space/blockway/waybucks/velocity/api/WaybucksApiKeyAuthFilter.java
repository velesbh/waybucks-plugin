package space.blockway.waybucks.velocity.api;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import space.blockway.waybucks.velocity.database.WaybucksApiKeyRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Javalin before-handler that validates API key authentication via the
 * Authorization: Bearer <key> header.
 *
 * <p>If the supplied key matches the master key, {@code ctx.attribute("isMasterKey")} is set to
 * {@code true} and the request is allowed through. Otherwise the key hash is looked up in
 * {@code wb_api_keys}; on a match, {@code last_used} is updated. Invalid keys receive HTTP 401.</p>
 */
public class WaybucksApiKeyAuthFilter implements Handler {

    private final WaybucksApiKeyRepository apiKeyRepository;
    private final String masterKeyHash;

    public WaybucksApiKeyAuthFilter(WaybucksApiKeyRepository repo, String masterKey) {
        this.apiKeyRepository = repo;
        this.masterKeyHash = sha256(masterKey);
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.status(401).json(java.util.Map.of("error", "Unauthorized"));
            return;
        }

        String key = authHeader.substring(7).trim();

        if (key.isEmpty()) {
            ctx.status(401).json(java.util.Map.of("error", "Unauthorized"));
            return;
        }

        String keyHash = sha256(key);

        // Check master key first
        if (keyHash.equals(masterKeyHash)) {
            ctx.attribute("isMasterKey", true);
            return;
        }

        // Validate against stored API keys
        boolean valid = apiKeyRepository.validateAndTouch(keyHash);
        if (!valid) {
            ctx.status(401).json(java.util.Map.of("error", "Unauthorized"));
            return;
        }

        ctx.attribute("isMasterKey", false);
    }

    /**
     * Returns the lower-case hex SHA-256 digest of the given input string (UTF-8).
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm not available", ex);
        }
    }
}
