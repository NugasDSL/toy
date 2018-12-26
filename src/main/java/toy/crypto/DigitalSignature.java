package toy.crypto;

/**
 * An interface that any signature should implement.
 */
public interface DigitalSignature {

    /**
     * Sign on a given message.
     * @param toSign the object to sign on
     * @return a string that encodes the signature
     */
    String signMessage(Object toSign);

    /**
     * Verify a signature of a given signer on a given object
     * @param id the signer's ID
     * @param toVer the object to be validated
     * @return true if valid, false if not
     */
    boolean verifyMessage(int id, Object toVer);
}
