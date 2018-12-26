package toy.blockchain;

/**
 * This class implement a simple Blockchain that holds a list of blocks.
 */
public class BasicBlockchain extends Blockchain {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(BasicBlockchain.class);

    /**
     * Copy constructor.
     * @param orig the original Blockchain
     * @param start the index to start from
     * @param end the index to end with
     */
    public BasicBlockchain(Blockchain orig, int start, int end) {
        super(orig, start, end);
    }

    /**
     * Constructor.
     * @param creatorID the creating server ID
     * @param channel the creating channel number (with a single Toy group this is always 0)
     */
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
