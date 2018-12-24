package toy.crypto;

public interface DigitalSignature {

    String signMessage(Object toSign);

    boolean verifyMessage(int id, Object toVer);
}
