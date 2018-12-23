package blockchain;

import crypto.DigestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

public abstract class Blockchain {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Blockchain.class);
    private final List<proto.Types.Block> blocks = new ArrayList<>();
    private final int creatorID;

    public Blockchain(int creatorID, int channel) {
        this.creatorID = creatorID;
        createGenesis(channel);
    }

    public Blockchain(Blockchain orig, int start, int end) {
        this.creatorID = orig.creatorID;
        this.blocks.addAll(orig.getBlocks(start, end));
    }

    public abstract Block createNewBLock();

    abstract void createGenesis(int channel);

    public boolean validateCurrentLeader(int leader, int f) {
        if (blocks.size() >= f && blocks.subList(blocks.size() - f, blocks.size()).
                stream().
                map(bl -> bl.getHeader().getM().getSender()).
                collect(Collectors.toList()).
                contains(leader)) {
            logger.debug(format("[#%d] leader is in the last f proposers", creatorID));
            return false;
        }
        return true;
    }
    public boolean validateBlockCreator(proto.Types.Block b, int f) {
        if (blocks.size() >= f && blocks.subList(blocks.size() - f, blocks.size()).
        stream().
        map(bl -> bl.getHeader().getM().getSender()).
        collect(Collectors.toList()).
        contains(b.getHeader().getM().getSender())) {
            logger.debug(format("[#%d] invalid Block", creatorID));
            return false;
        }
        return true;
    }

//    public abstract boolean validateBlockData(Block b);

    public void setBlocks(List<proto.Types.Block> Nblocks, int start) {
        for (int i = start ; i < start + Nblocks.size() ; i++) {
            if (blocks.size() <= i) {
                blocks.add(Nblocks.get(i - start));
            } else {
                blocks.set(i, Nblocks.get(i - start));
            }

        }
    }

    public void setBlock(int index, proto.Types.Block b) {
        synchronized (blocks) {
            blocks.set(index, b);
        }
    }
    public boolean validateBlockHash(proto.Types.Block b) {
        byte[] d = DigestMethod.hash(blocks.get(b.getHeader().getHeight() - 1).getHeader().toByteArray());
        return DigestMethod.validate(b.getHeader().getPrev().toByteArray(),
                Objects.requireNonNull(d));
    }

    public void addBlock(proto.Types.Block b) {
        synchronized (blocks) {
            blocks.add(b);
        }
    }

    public proto.Types.Block getBlock(int index) {
        synchronized (blocks) {
            return blocks.get(index);
        }
    }

    public List<proto.Types.Block> getBlocks(int start, int end) {
        synchronized (blocks) {
            return blocks.subList(start, end);
        }
    }

    public List<proto.Types.Block> getBlocksCopy(int start, int end) {
        synchronized (blocks) {
            return new ArrayList<proto.Types.Block>(blocks.subList(start, end));
        }
    }

    public int getHeight() {
        synchronized (blocks) {
            return blocks.size() - 1;
        }
    }

    public void removeBlock(int index) {

        synchronized (blocks) {
            blocks.remove(index);
        }
    }

}
