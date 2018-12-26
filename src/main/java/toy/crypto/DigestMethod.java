package toy.crypto;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A class to that implements the hashing method (using SHA-256).
 */
public class DigestMethod {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(DigestMethod.class);

    /**
     * Hash a given byte array.
     * @param toHash the byte array to hash
     * @return byte array of the hash result
     */
    static public byte[] hash(byte[] toHash) {
        MessageDigest digestMethod = null;
        try {
            digestMethod = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            logger.fatal("", e);
        }
        assert digestMethod != null;
        return digestMethod.digest(toHash);
    }

    /**
     * Validate that two hashes are equals.
     * @param d1 the first hash
     * @param d2 the second hash
     * @return true if equals, else false
     */
    static public boolean validate(byte[] d1, byte[] d2) {
        return Arrays.equals(d1, d2);
    }
}
