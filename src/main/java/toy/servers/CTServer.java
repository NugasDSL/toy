package toy.servers;

import toy.blockchain.Blockchain;
import toy.config.Node;
import toy.das.atomicBroadcast.RBrodcastService;
import toy.das.wrb.WrbNode;
import toy.blockchain.BasicBlockchain;
import toy.proto.Types;

import java.util.ArrayList;

import static java.lang.String.format;

/**
 * This class implement ToyServer as a correct one.
 */
public class CTServer extends ToyServer {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CTServer.class);

    /**
     * CTServer (Correct Toy) constructor.
     * @param addr the ip of the server
     * @param wrbPort the port on which it receives new blocks
     * @param id server ID
     * @param channel the number of channels (with single Toy group it is always 1)
     * @param f an upper bound on the number of faulty nodes
     * @param maxTx max transactions per block
     * @param fastMode indicates to run a single communication step protocol (rhater than two) by piggybacking
     *                 the next block to the first BBC message
     * @param wrb an instance of wrb service
     * @param panic an instance of panic service
     * @param sync an instance of sync service
     */
    public CTServer(String addr, int wrbPort, int id, int channel, int f,
                    int maxTx, boolean fastMode, WrbNode wrb, RBrodcastService panic, RBrodcastService sync) {
        super(addr, wrbPort, id, channel, f, maxTx, fastMode, wrb, panic, sync);
    }

    /**
     * CTServer (Correct Toy constructor.
     * @param addr the ip of the server
     * @param wrbPort the port on which it receives new blocks
     * @param id server ID
     * @param channel the number of channels (with a single Toy group it is always 1)
     * @param f an upper bound on the number of faulty nodes
     * @param tmo the initial time-out to wait in wrb-deliver
     * @param tmoInterval the interval to add to tmo in case wrb was failed to deliver the message
     *                    in a single round
     * @param maxTx max transactions per block
     * @param fastMode indicates to run a single communication step protocol (rhater than two) by piggybacking
     *      *                 the next block to the first BBC message
     * @param cluster a list of Nodes that compose the network
     * @param bbcConfig a path to the configuration directory of wrb
     * @param panicConfig a path to the configuration directory of panic
     * @param syncConfig a path to the configuration directory of sync
     * @param serverCrt a path to the server certificate
     * @param serverPrivKey a path to the server ssl private key
     * @param caRoot a path to the ca certificate. If equals to "" the server will trust any certificate
     */
    public CTServer(String addr, int wrbPort, int id, int channel, int f, int tmo, int tmoInterval,
                    int maxTx, boolean fastMode, ArrayList<Node> cluster,
                    String bbcConfig, String panicConfig, String syncConfig, String serverCrt, String serverPrivKey, String caRoot) {
        super(addr, wrbPort, id, channel, f, tmo, tmoInterval, maxTx, fastMode, cluster,
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

    private Types.Block normalLeaderPhase() {
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

    private Types.Block fastModePhase() {
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
