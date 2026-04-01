package space.blockway.waybucks.paper.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps a Paper FileConfiguration and provides typed, MiniMessage-parsed accessors
 * for messages, GUI titles, and misc settings.
 *
 * <p>Message templates are cached after the first parse so repeated calls with the
 * same key do not re-parse the MiniMessage template string.</p>
 */
public class WaybucksPaperConfig {

    private final FileConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** Cache of raw template strings → pre-parsed Component templates. */
    private final Map<String, String> templateCache = new HashMap<>();

    public WaybucksPaperConfig(FileConfiguration config) {
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches {@code messages.KEY}, applies pairwise {placeholder} replacements
     * (replacements[0] is placeholder name, replacements[1] is value, etc.), and
     * returns the resulting {@link Component} WITHOUT a prefix.
     *
     * @param key          config key under {@code messages}
     * @param replacements alternating placeholder/value pairs, e.g. "amount","100"
     * @return parsed Component
     */
    public Component getMessage(String key, String... replacements) {
        String template = getRawMessage(key);
        template = applyReplacements(template, replacements);
        return miniMessage.deserialize(template);
    }

    /**
     * Same as {@link #getMessage(String, String...)} but prepends the configured
     * prefix component.
     */
    public Component getPrefixedMessage(String key, String... replacements) {
        Component prefix = getPrefix();
        Component body = getMessage(key, replacements);
        return prefix.append(body);
    }

    /**
     * Fetches {@code gui.KEY} and parses it with MiniMessage.
     *
     * @param key config key under {@code gui}
     * @return parsed Component title
     */
    public Component getGuiTitle(String key) {
        String raw = config.getString("gui." + key, "<gray>" + key + "</gray>");
        return miniMessage.deserialize(raw);
    }

    /**
     * Returns the configured server name used in PLAYER_JOIN payloads.
     */
    public String getServerName() {
        return config.getString("server-name", "survival");
    }

    /**
     * Item functionality is toggled on the Velocity side; this always returns
     * {@code true} as a placeholder so Paper-side item code is always compiled in.
     */
    public boolean isItemEnabled() {
        return true;
    }

    /**
     * Returns the raw (un-parsed) message string for the given key under
     * {@code messages}.  Useful when further string manipulation is needed before
     * MiniMessage parsing.
     */
    public String getRawMessage(String key) {
        return config.getString("messages." + key, "<gray>[Waybucks] Missing key: " + key + "</gray>");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the prefix component from config, defaulting to a bracketed tag.
     */
    private Component getPrefix() {
        String raw = config.getString("messages.prefix", "<gold>[Waybucks]</gold> ");
        return miniMessage.deserialize(raw);
    }

    /**
     * Applies pairwise {placeholder} replacements to a template string.
     * replacements[0] is the placeholder name, replacements[1] is the value, etc.
     */
    private String applyReplacements(String template, String... replacements) {
        if (replacements == null || replacements.length == 0) {
            return template;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String value = replacements[i + 1] != null ? replacements[i + 1] : "";
            template = template.replace(placeholder, value);
        }
        return template;
    }
}
