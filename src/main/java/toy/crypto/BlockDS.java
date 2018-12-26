package toy.crypto;

import org.apache.commons.lang.ArrayUtils;
import toy.proto.Types;

import java.util.Arrays;

/**
 * A class that implements the digital signature of blocks.
 */
public class BlockDS implements DigitalSignature {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(BlockDS.class);
    @Override
    public String signMessage(Object toSign) {
        Types.BlockHeader header = (Types.BlockHeader) toSign;
//        Block.Builder b =(Block.Builder) toSign;
        return PkiUtils.sign(new String(header.getM().toByteArray()) +
                String.valueOf(header.getHeight()) +
                new String(header.getTransactionHash().toByteArray()));
    }

    @Override
    public boolean verifyMessage(int id, Object toVer) {
        Types.Block b = (Types.Block) toVer;
        byte[] tHash = new byte[0];
        for (Types.Transaction t : b.getDataList()) {
            tHash = DigestMethod.hash(ArrayUtils.addAll(tHash, t.toByteArray()));
        }
        if (!Arrays.equals(tHash, b.getHeader().getTransactionHash().toByteArray())) return false;
        Types.BlockHeader header = b.getHeader();
        return PkiUtils.verify(id,
                new String(header.getM().toByteArray()) +
                        String.valueOf(header.getHeight()) +
                        new String(header.getTransactionHash().toByteArray()), header.getProof());
    }

    /**
     * Verify the signature of a given signer on a given block.
     * @param id the signer node ID
     * @param b the block to be verified
     * @return true if valid, false if not
     */
    public static boolean verify(int id, Types.Block b) {
        return new BlockDS().verifyMessage(id, b);
    }

    /**
     * Sign on a given block.
     * @param b the block to sign on
     * @return a string that represents the signature
     */
    public static String sign(Types.BlockHeader b) {
        return new BlockDS().signMessage(b);
    }
}
