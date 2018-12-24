package toy.servers;

import toy.blockchain.Blockchain;
import toy.config.Node;
import toy.das.atomicBroadcast.RBrodcastService;
import toy.das.wrb.WrbNode;
import toy.blockchain.BasicBlockchain;
import toy.proto.Types;

import java.util.ArrayList;

import static java.lang.String.format;

public class CTServer extends ToyServer {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CTServer.class);

    public CTServer(String addr, int rmfPort, int id, int channel, int f,
                    int maxTx, boolean fastMode, WrbNode wrb, RBrodcastService panic, RBrodcastService sync) {
        super(addr, rmfPort, id, channel, f, maxTx, fastMode, wrb, panic, sync);
    }

    public CTServer(String addr, int rmfPort, int id, int channel, int f, int tmo, int tmoInterval,
                    int maxTx, boolean fastMode, ArrayList<Node> cluster,
                    String bbcConfig, String panicConfig, String syncConfig, String serverCrt, String serverPrivKey, String caRoot) {
        super(addr, rmfPort, id, channel, f, tmo, tmoInterval, maxTx, fastMode, cluster,
                bbcConfig, panicConfig, syncConfig, serverCrt, serverPrivKey, caRoot);
    }

    Types.Block leaderImpl() {
        if (!configuredFastMode) {
            return normalLeaderPhase();
        }
        if (currHeight == 1 || !fastMode) {
            normalLeaderPhase();
        }
        return fastModePhase();
    }

    Types.Block normalLeaderPhase() {
        if (currLeader != getID()) {
            return null;
        }
        logger.debug(format("[#%d -C[%d]] prepare to disseminate a new Block of [height=%d] [cidSeries=%d ; cid=%d]",
                getID(), channel, currHeight, cidSeries, cid));
        addTransactionsToCurrBlock();
        Types.Block sealedBlock = currBlock.construct(getID(), currHeight, cidSeries, cid, channel, bc.getBlock(currHeight - 1).getHeader());
        wrbServer.broadcast(sealedBlock);
        return null;
    }

    Types.Block fastModePhase() {
        if ((currLeader + 1) % n != getID()) {
            return null;
        }
        logger.debug(format("[#%d-C[%d]] prepare fast mode phase for [height=%d] [cidSeries=%d ; cid=%d]",
                getID(), channel, currHeight + 1, cidSeries, cid + 1));
        addTransactionsToCurrBlock();
        return currBlock.construct(getID(), currHeight + 1, cidSeries, cid + 1, channel, null);
    }

    @Override
    public Blockchain initBC(int id, int channel) {
        return new BasicBlockchain(id, channel);
    }

    @Override
    public Blockchain getBC(int start, int end) {
        return new BasicBlockchain(this.bc, start, end);
    }


}
