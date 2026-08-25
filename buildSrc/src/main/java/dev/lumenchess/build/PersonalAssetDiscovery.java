package dev.lumenchess.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Discovers a narrow, sanitized view of an external personal asset pack. */
public final class PersonalAssetDiscovery {
    public static final List<String> CANONICAL_PIECE_FILES = List.of(
        "bb.png", "bk.png", "bn.png", "bp.png", "bq.png", "br.png",
        "wb.png", "wk.png", "wn.png", "wp.png", "wq.png", "wr.png"
    );

    public static final List<String> REQUIRED_BOARD_FILES = List.of(
        "blue.png",
        "brown.png",
        "tournament.png",
        "walnut.png",
        "icy_sea.png",
        "dark_wood.png",
        "light.png",
        "tan.png"
    );

    private static final Pattern SAFE_DIRECTORY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private static final Map<String, String> CANONICAL_DISPLAY_NAMES = canonicalDisplayNames();

    private PersonalAssetDiscovery() {}

    public record PieceStyle(
        String sourceDirectory,
        String displayName,
        String stableId,
        Map<String, Path> files
    ) {}

    public record CopyEntry(Path source, String relativeDestination) {}

    public record Inventory(List<PieceStyle> styles, List<CopyEntry> copies, String fingerprint) {
        public String encodedStyles() {
            return styles.stream()
                .map(style -> style.stableId() + "|" + style.sourceDirectory() + "|" + style.displayName())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        }
    }

    public static Inventory discover(Path configuredRoot) throws IOException {
        Path root = configuredRoot.toRealPath();
        Path piecesRoot = containedDirectory(root, "pieces");
        Path boardsRoot = optionalContainedDirectory(root, "boards");
        List<PieceStyle> discovered = new ArrayList<>();

        try (Stream<Path> children = Files.list(piecesRoot)) {
            for (Path candidate : children.toList()) {
                String directoryName = candidate.getFileName().toString();
                if (!SAFE_DIRECTORY.matcher(directoryName).matches()) continue;
                if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                Path styleRoot = candidate.toRealPath();
                if (!styleRoot.startsWith(piecesRoot)) continue;

                Map<String, Path> pieceFiles = new LinkedHashMap<>();
                boolean complete = true;
                for (String canonicalName : CANONICAL_PIECE_FILES) {
                    Path file = styleRoot.resolve(canonicalName);
                    if (!isContainedRegularFile(styleRoot, file)) {
                        complete = false;
                        break;
                    }
                    pieceFiles.put(canonicalName, file.toRealPath());
                }
                if (!complete) continue;

                String displayName = CANONICAL_DISPLAY_NAMES.getOrDefault(directoryName, directoryName);
                discovered.add(new PieceStyle(
                    directoryName,
                    displayName,
                    "private.chesscom." + directoryName.toLowerCase(java.util.Locale.ROOT),
                    Map.copyOf(pieceFiles)
                ));
            }
        }

        Map<String, Integer> preferredOrder = new LinkedHashMap<>();
        int order = 0;
        for (String directory : CANONICAL_DISPLAY_NAMES.keySet()) preferredOrder.put(directory, order++);
        discovered.sort(
            Comparator.comparingInt((PieceStyle style) -> preferredOrder.getOrDefault(style.sourceDirectory(), Integer.MAX_VALUE))
                .thenComparing(PieceStyle::sourceDirectory)
        );

        List<CopyEntry> copies = new ArrayList<>();
        for (PieceStyle style : discovered) {
            for (String canonicalName : CANONICAL_PIECE_FILES) {
                copies.add(new CopyEntry(
                    style.files().get(canonicalName),
                    "pieces/" + style.sourceDirectory() + "/" + canonicalName
                ));
            }
        }
        if (boardsRoot != null) {
            for (String requiredBoard : REQUIRED_BOARD_FILES) {
                Path board = boardsRoot.resolve(requiredBoard);
                if (isContainedRegularFile(boardsRoot, board)) {
                    copies.add(new CopyEntry(board.toRealPath(), "boards/" + requiredBoard));
                }
            }
        }

        List<CopyEntry> immutableCopies = List.copyOf(copies);
        return new Inventory(List.copyOf(discovered), immutableCopies, fingerprint(immutableCopies));
    }

    public static Path safeRelative(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes configured personal asset root: " + relative);
        }
        return resolved;
    }

    private static Path containedDirectory(Path root, String child) throws IOException {
        Path directory = safeRelative(root, child);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("lumen.personalAssetsDir must contain " + child + "/");
        }
        Path real = directory.toRealPath();
        if (!real.startsWith(root)) {
            throw new IllegalArgumentException("Personal asset directory escapes configured root: " + child);
        }
        return real;
    }

    private static Path optionalContainedDirectory(Path root, String child) throws IOException {
        Path directory = safeRelative(root, child);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return null;
        return containedDirectory(root, child);
    }

    private static boolean isContainedRegularFile(Path root, Path candidate) throws IOException {
        if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return false;
        return candidate.toRealPath().startsWith(root);
    }

    private static String fingerprint(List<CopyEntry> entries) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        for (CopyEntry entry : entries.stream()
            .sorted(Comparator.comparing(CopyEntry::relativeDestination))
            .toList()) {
            digest.update(entry.relativeDestination().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(entry.source())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Map<String, String> canonicalDisplayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("ejgfv", "Neo");
        names.put("8qetl", "Neo Angle");
        names.put("game_room", "Game Room");
        names.put("wood", "Wood");
        names.put("glass", "Glass");
        names.put("gothic", "Gothic");
        names.put("classic", "Classic");
        names.put("metal", "Metal");
        names.put("bases", "Bases");
        names.put("neo_wood", "Neo-Wood");
        names.put("icy_sea", "Icy Sea");
        names.put("club", "Club");
        names.put("ocean", "Ocean");
        names.put("newspaper", "Newspaper");
        names.put("space", "Space");
        names.put("cases", "Cases");
        names.put("condal", "Condal");
        names.put("3d_chesskid", "3D ChessKid");
        names.put("8_bit", "8-Bit");
        names.put("marble", "Marble");
        names.put("book", "Book");
        names.put("alpha", "Alpha");
        names.put("bubblegum", "Bubblegum");
        names.put("dash", "Dash");
        names.put("graffiti", "Graffiti");
        names.put("light", "Light");
        names.put("lolz", "Lolz");
        names.put("luca", "Luca");
        names.put("maya", "Maya");
        names.put("modern", "Modern");
        names.put("nature", "Nature");
        names.put("neon", "Neon");
        names.put("sky", "Sky");
        names.put("tigers", "Tigers");
        names.put("tournament", "Tournament");
        names.put("vintage", "Vintage");
        names.put("3d_wood", "Real 3D");
        names.put("3d_staunton", "3D Staunton");
        names.put("3d_plastic", "3D Plastic");
        return Collections.unmodifiableMap(names);
    }
}
