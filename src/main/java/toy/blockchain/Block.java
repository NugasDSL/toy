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

public abstract class Block {
    public Types.Block.Builder blockBuilder = Types.Block.newBuilder();

    abstract public boolean validateTransaction(Transaction t);


    public Transaction getTransaction(int index) {
        return blockBuilder.getData(index);
    }

    public ArrayList<Transaction> getAllTransactions() {
        ArrayList<Transaction> ret = new ArrayList<>();
        blockBuilder.addAllData(ret);
        return ret;
    }


    public void addTransaction(Transaction t) {
            blockBuilder.addData(t);
    }

    public void removeTransaction(int index) {
        blockBuilder.removeData(index);
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

    public int getTransactionCount() {
        return blockBuilder.getDataCount();
    }


}
