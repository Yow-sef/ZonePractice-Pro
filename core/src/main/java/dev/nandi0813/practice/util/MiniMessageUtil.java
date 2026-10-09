package dev.nandi0813.practice.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MiniMessageUtil {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private MiniMessageUtil() {}

    /**
     * Builds the global MiniMessage instance configured with standard Adventure tags
     * plus custom resolvers for {@code <head_texture:...>} and base64 head textures.
     */
    public static MiniMessage createMiniMessage() {
        return MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolver(createHeadTextureResolver())
                        .resolver(StandardTags.defaults())
                        .build())
                .build();
    }

    /**
     * Resolves {@code <head_texture:texture[:outer_layer]>} as well as {@code <headtexture:...>}
     * and intercepts {@code <head:...>} when given a base64 or URL skin texture.
     */
    public static TagResolver createHeadTextureResolver() {
        TagResolver dedicatedResolver = TagResolver.resolver(
                Set.of("head_texture", "headtexture"),
                (args, ctx) -> {
                    if (!args.hasNext()) {
                        throw ctx.newException("Missing texture argument for head_texture tag", args);
                    }
                    String texture = args.pop().value();
                    boolean hat = true;
                    if (args.hasNext()) {
                        Tag.Argument hatArg = args.pop();
                        if (hatArg.isFalse() || "false".equalsIgnoreCase(hatArg.value())) {
                            hat = false;
                        }
                    }
                    return createHeadTag(texture, hat);
                }
        );

        TagResolver fallbackHeadResolver = TagResolver.resolver(
                "head",
                (args, ctx) -> {
                    if (!args.hasNext()) {
                        return null;
                    }
                    String raw = args.peek().value();
                    if (isTextureString(raw)) {
                        args.pop();
                        boolean hat = true;
                        if (args.hasNext()) {
                            Tag.Argument hatArg = args.pop();
                            if (hatArg.isFalse() || "false".equalsIgnoreCase(hatArg.value())) {
                                hat = false;
                            }
                        }
                        return createHeadTag(raw, hat);
                    }
                    return null;
                }
        );

        return TagResolver.resolver(dedicatedResolver, fallbackHeadResolver);
    }

    private static Tag createHeadTag(String rawTexture, boolean hat) {
        String normalized = normalizeTexture(rawTexture);
        UUID headId = UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));

        PlayerHeadObjectContents contents = ObjectContents.playerHead()
                .id(headId)
                .profileProperty(PlayerHeadObjectContents.property("textures", normalized))
                .hat(hat)
                .build();

        return Tag.selfClosingInserting(Component.object(contents));
    }

    public static String normalizeTexture(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        raw = raw.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + raw + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        if (HEX_64.matcher(raw).matches()) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + raw + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        return raw;
    }

    private static boolean isTextureString(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true;
        if (HEX_64.matcher(trimmed).matches()) return true;
        if (trimmed.length() > 16 && !UUID_PATTERN.matcher(trimmed).matches() && !trimmed.contains(":")) {
            try {
                Base64.getDecoder().decode(trimmed);
                return true;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }
}
