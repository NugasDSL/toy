package blockchain;

public class BasicBlockchain extends Blockchain {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(BasicBlockchain.class);

    public BasicBlockchain(Blockchain orig, int start, int end) {
        super(orig, start, end);
    }
    public BasicBlockchain(int creatorID, int channel) {
        super(creatorID, channel);
    }

    @Override
    public Block createNewBLock() {
        return new BasicBlock();
    }

    @Override
    void createGenesis(int channel) {
        addBlock(new BasicBlock()
                .construct(-1, 0, -1, -1, channel,  null));
    }

}
