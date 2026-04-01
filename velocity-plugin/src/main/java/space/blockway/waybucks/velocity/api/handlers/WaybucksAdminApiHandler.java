package space.blockway.waybucks.velocity.api.handlers;

import io.javalin.http.Context;
import space.blockway.waybucks.velocity.api.WaybucksApiKeyAuthFilter;
import space.blockway.waybucks.velocity.database.WaybucksApiKeyRepository;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * REST handler for API key management. All routes here require the master API key
 * (the {@code isMasterKey} context attribute must be {@code true}).
 */
public class WaybucksAdminApiHandler {

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_SUFFIX_LENGTH = 32;

    private final WaybucksApiKeyRepository repo;
    private final String masterKey;

    public WaybucksAdminApiHandler(WaybucksApiKeyRepository repo, String masterKey) {
        this.repo = repo;
        this.masterKey = masterKey;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/apikey
    // Body: { "label": "<string>" }
    // -------------------------------------------------------------------------

    /**
     * Generates a new API key with the given label, stores its SHA-256 hash, and returns the
     * plain-text key once — it is never stored in plain text and cannot be recovered.
     */
    public void generateApiKey(Context ctx) {
        if (!isMasterKey(ctx)) return;

        GenerateApiKeyBody body;
        try {
            body = ctx.bodyAsClass(GenerateApiKeyBody.class);
        } catch (Exception ex) {
            ctx.status(400).json(Map.of("error", "Invalid payload: " + ex.getMessage()));
            return;
        }

        if (body == null || body.label == null || body.label.isBlank()) {
            ctx.status(400).json(Map.of("error", "label is required"));
            return;
        }

        String rawKey = "wb_" + generateRandomSuffix();
        String keyHash = WaybucksApiKeyAuthFilter.sha256(rawKey);

        repo.insertApiKey(body.label.trim(), keyHash);

        ctx.status(201).json(Map.of(
                "label", body.label.trim(),
                "key", rawKey
        ));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/apikey/{label}
    // -------------------------------------------------------------------------

    /**
     * Revokes (deletes) the API key with the supplied label.
     */
    public void revokeApiKey(Context ctx) {
        if (!isMasterKey(ctx)) return;

        String label = ctx.pathParam("label");
        boolean deleted = repo.deleteApiKey(label);

        if (deleted) {
            ctx.json(Map.of("result", "REVOKED", "label", label));
        } else {
            ctx.status(404).json(Map.of("error", "API key not found", "label", label));
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/apikey
    // -------------------------------------------------------------------------

    /**
     * Lists all API key metadata (label, created_at, last_used). The key hash is never returned.
     */
    public void listApiKeys(Context ctx) {
        if (!isMasterKey(ctx)) return;

        List<WaybucksApiKeyRepository.ApiKeyRecord> keys = repo.listApiKeys();
        ctx.json(keys);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isMasterKey(Context ctx) {
        Boolean master = ctx.attribute("isMasterKey");
        if (master == null || !master) {
            ctx.status(403).json(Map.of("error", "Forbidden — master key required"));
            return false;
        }
        return true;
    }

    private static String generateRandomSuffix() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_SUFFIX_LENGTH);
        for (int i = 0; i < KEY_SUFFIX_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Request body POJOs
    // -------------------------------------------------------------------------

    public static class GenerateApiKeyBody {
        public String label;
    }
}
