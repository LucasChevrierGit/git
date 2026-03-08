package mini_git.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ObjectStore {

    private static final Path OBJECTS_DIR = Path.of(".minigit", "objects");

    public static String writeObject(String content) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String sha = sha1Hex(bytes);
        Path objectPath = OBJECTS_DIR.resolve(sha);
        if (!Files.exists(objectPath)) {
            Files.write(objectPath, bytes);
        }
        return sha;
    }

    public static String storeBlob(byte[] content) throws IOException, NoSuchAlgorithmException {
        String sha = sha1Hex(content);
        Path objectPath = OBJECTS_DIR.resolve(sha);
        if (!Files.exists(objectPath)) {
            Files.write(objectPath, content);
        }
        return sha;
    }

    public static String computeHash(Path filePath) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(filePath));
            return toHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    public static String sha1Hex(byte[] data) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(data);
        return toHex(hash);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
