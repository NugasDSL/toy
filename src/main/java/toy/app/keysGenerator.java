package toy.app;
import toy.crypto.PkiUtils;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class keysGenerator {
    public static void main(String argv[]) {
        String serverID = argv[0];
        try {
            PkiUtils.generateKeyPair("../generatedKeys/" + serverID);
        } catch (NoSuchProviderException | NoSuchAlgorithmException |
                InvalidAlgorithmParameterException | IOException e) {
            e.printStackTrace();
        }
    }
}
