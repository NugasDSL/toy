package toy.blockchain;

import toy.crypto.DigestMethod;
import toy.proto.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * An abstract class that every implementation of a Blockchain must implement.
 */
public abstract class Blockchain {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Blockchain.class);
    private final List<Types.Block> blocks = new ArrayList<>();
    private final int creatorID;

    /**
     * Constructor
     * @param creatorID the creator server ID
     * @param channel the creator server channel (with a single Toy group this is always 0)
     */
    public Blockchain(int creatorID, int channel) {
        this.creatorID = creatorID;
        createGenesis(channel);
    }

    /**
     * Copy constructor.
     * @param orig the original Blockchain
     * @param start the index to start from
     * @param end the index to end with
     */
    public Blockchain(Blockchain orig, int start, int end) {
        this.creatorID = orig.creatorID;
        this.blocks.addAll(orig.getBlocks(start, end));
    }

    /**
     * Define the way to create a new block.
     * @return the new created block
     */
    public abstract Block createNewBLock();

    /**
     * Define the way to create a genesis block.
     * @param channel the channel of the creator server (with a single Toy group this is always 0)
     */
    abstract void createGenesis(int channel);

    /**
     * Check if <i>leader</i> is one of the last f+1 proposers.
     * @param leader the leader to be validated
     * @param f an upper bound on the faulty nodes number
     * @return true if the leader did not proposed one of the last f+1 blocks, false either
     */
    public boolean validateCurrentLeader(int leader, int f) {
        if (blocks.size() >= (f+1) && blocks.subList(blocks.size() - (f+1), blocks.size()).
                stream().
                map(bl -> bl.getHeader().getM().getSender()).
                collect(Collectors.toList()).
                contains(leader)) {
            logger.debug(format("[#%d] leader is in the last f proposers", creatorID));
            return false;
        }
        return true;
    }
    /**
     * Check if a block was proposed by a leader that proposed one of the last f+1 blocks.
     * @param b block to be validated
     * @param f an upper bound on the faulty nodes number
     * @return true if the block proposer did not proposed one of the last f+1 blocks, false either
     */
    public boolean validateBlockCreator(Types.Block b, int f) {
        if (blocks.size() >= (f+1) && blocks.subList(blocks.size() - (f + 1), blocks.size()).
        stream().
        map(bl -> bl.getHeader().getM().getSender()).
        collect(Collectors.toList()).
        contains(b.getHeader().getM().getSender())) {
            logger.debug(format("[#%d] invalid Block", creatorID));
            return false;
        }
        return true;
    }

    /**
     * Replace sub-chain with another sub-chain.
     * @param Nblocks the new sub-chain
     * @param start index from where to start replacing
     */
    public void setBlocks(List<Types.Block> Nblocks, int start) {
        for (int i = start ; i < start + Nblocks.size() ; i++) {
            if (blocks.size() <= i) {
                blocks.add(Nblocks.get(i - start));
            } else {
                blocks.set(i, Nblocks.get(i - start));
            }

        }
    }

    /**
     * Replace a single block with a new block.
     * @param index the index to be replaced
     * @param b the nre block
     */
    public void setBlock(int index, Types.Block b) {
        synchronized (blocks) {
            blocks.set(index, b);
        }
    }

    /**
     * Validate a block hash with respect to the latest block in the chain.
     * @param b the block to be validated
     * @return true if valid, false if not
     */
    public boolean validateBlockHash(Types.Block b) {
        byte[] d = DigestMethod.hash(blocks.get(b.getHeader().getHeight() - 1).getHeader().toByteArray());
        return DigestMethod.validate(b.getHeader().getPrev().toByteArray(),
                Objects.requireNonNull(d));
    }

    /**
     * Add block to the head of the chain.
     * @param b the block to add
     */
    public void addBlock(Types.Block b) {
        synchronized (blocks) {
            blocks.add(b);
        }
    }

    /**
     * Get the index_th block of the chain.
     * @param index the index of the requested block
     * @return the index_th block
     */
    public Types.Block getBlock(int index) {
        synchronized (blocks) {
            return blocks.get(index);
        }
    }

    /**
     * Get a sublist of blocks.
     * @param start the index to start with
     * @param end the index to end with
     * @return a list contains the blocks from <i>start</i> to <i>end</i>
     */
    public List<Types.Block> getBlocks(int start, int end) {
        synchronized (blocks) {
            return blocks.subList(start, end);
        }
    }

    /**
     * Get the current BLockchain height
     * @return the current BLockchain height
     */
    public int getHeight() {
        synchronized (blocks) {
            return blocks.size() - 1;
        }
    }

    /**
     * Remove the index_th block.
     * @param index the block to be removed
     */
    public void removeBlock(int index) {

        synchronized (blocks) {
            blocks.remove(index);
        }
    }

}
