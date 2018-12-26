package toy.blockchain;

import com.google.protobuf.ByteString;
import toy.crypto.DigestMethod;
import toy.crypto.BlockDS;
import org.apache.commons.lang.ArrayUtils;
import toy.proto.Types;
import toy.proto.Types.BlockHeader;
import toy.proto.Types.Meta;
import toy.proto.Types.Transaction;

import java.util.ArrayList;

/**
 * An abstract class the every implementation of block should implement.
 */
public abstract class Block {
    public Types.Block.Builder blockBuilder = Types.Block.newBuilder();

    /**
     * Define the validity of a transaction with respect to the current Blockchain and the current block.
     * @param t the transaction to be validated
     * @return true if valid and false if not
     */
    abstract public boolean validateTransaction(Transaction t);


    /**
     * Get the i_th transaction of the block.
     * @param index the transaction index to return
     * @return the index_th transaction
     */
    public Transaction getTransaction(int index) {
        return blockBuilder.getData(index);
    }

    /**
     * Add transaction to the block.
     * @param t the transaciton to be added
     */
    public void addTransaction(Transaction t) {
            blockBuilder.addData(t);
    }

    public Types.Block construct(int creatorID, int height, int cidSeries, int cid, int channel, BlockHeader header) {
        long start = System.currentTimeMillis();
        byte[] tHash = new byte[0];
        for (Transaction t : blockBuilder.getDataList()) {
            tHash = DigestMethod.hash(ArrayUtils.addAll(tHash, t.toByteArray()));
        }
        byte[] headerArray = new byte[0];
        if (header != null) {
            headerArray = header.toByteArray();
        }
        blockBuilder
                .setHeader(BlockHeader.newBuilder()
                        .setM(Meta.newBuilder()
                                .setCid(cid)
                                .setCidSeries(cidSeries)
                                .setSender(creatorID)
                                .setChannel(channel)
                                .build())
                        .setHeight(height)
                        .setPrev(ByteString.copyFrom(DigestMethod.hash(headerArray)))
                        .setTransactionHash(ByteString.copyFrom(tHash))
                        .build());
        if (creatorID == -1) {
            return blockBuilder.build();
        }

        String signature = BlockDS.sign(blockBuilder.getHeader());
        return blockBuilder
                .setHeader(blockBuilder
                        .getHeader()
                        .toBuilder()
                .setProof(signature)
                .build())
                .setSt(blockBuilder.getSt().toBuilder().setSign(System.currentTimeMillis() - start))
                .build();
    }

    /**
     * Get the current number of transactions.
     * @return the current number of transactions
     */
    public int getTransactionCount() {
        return blockBuilder.getDataCount();
    }


}
