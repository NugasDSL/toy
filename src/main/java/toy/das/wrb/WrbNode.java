package toy.das.wrb;

import toy.config.Node;
import toy.proto.Types;

import java.util.ArrayList;

import static java.lang.String.format;

/**
 * This class implements a WRB node.
 */
public class WrbNode extends Node {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(WrbNode.class);
    WrbService wrbService;

    /**
     * Constructor
     * @param channels the number of channels in the system (with a single Toy group this is always 1).
     * @param id the node id
     * @param addr the node ip address
     * @param rmfPort the node port
     * @param f an upper bound on the number of faulty nodes
     * @param tmo the time-out to wait in WRB-deliver
     * @param tmoInterval the time-out interval to add to tmo if wrb-deliver did not deliver a requested message
     * @param nodes list of Nodes that depicts the WRB-cluster
     * @param bbcConfig path to the bbc configuration directory
     * @param serverCrt path to the server certificate file
     * @param serverPrivKey path to the server ssl private key path
     * @param caRoot path to the ca certificate root, if this equals to "" the server trusts any certificate
     */
    public WrbNode(int channels, int id, String addr, int rmfPort, int f , int tmo, int tmoInterval, ArrayList<Node> nodes, String bbcConfig,
                   String serverCrt, String serverPrivKey, String caRoot) {
        super(addr, rmfPort,  id);
        wrbService = new WrbService(channels, id, f, tmo, tmoInterval, nodes, bbcConfig, serverCrt, serverPrivKey, caRoot);
    }

    /**
     * Stops the node.
     */
    public void stop() {
        if (wrbService != null)
            wrbService.shutdown();
    }

    /**
     * Starts the node.
     */
    public void start() {
        wrbService.start();
    }

    /**
     * WRB-Broadcast method.
     * @param data the block to be disseminates (the block header includes the <i>cid</i> and <i>cidSeries</i>)
     */
    public void broadcast(Types.Block data) {
        logger.debug(String.format("[#%d] broadcasts data message with [height=%d]", getID(), data.getHeader().getHeight()));

        wrbService.rmfBroadcast(data);
    }

    /**
     * WRB-Deliver method.
     * @param channel the channel that wishes to deliver a message
     * @param cidSeries the cidSeirs of the WRB-Deliver instance (series changes due to an invocation of the synchronization
     *                  mechanism)
     * @param cid the id of the WRB-Deliver
     * @param height the height of the block to be delivered
     * @param sender the expected sender of the block
     * @param msg a massage to piggyback to the first message of BBC. If <i>fastMode</i>=false this is null
     * @return the delivered block or null if WRB-Deliver was not able to deliver the block
     * @throws InterruptedException
     */
    public Types.Block deliver(int channel, int cidSeries, int cid, int height, int sender, Types.Block msg)
            throws InterruptedException {
        long start = System.currentTimeMillis();
        Types.Block m = wrbService.deliver(channel, cidSeries, cid, sender, height, msg);
        logger.debug(format("[#%d-C[%d]] Deliver on wrb node took about %d [cidSeries=%d ; cid=%d]", getID(), channel,
                System.currentTimeMillis() - start, cidSeries, cid));
        return m;
    }

    /**
     * Clearing the buffers from irrelevant messages. This method is called bt <i>gc</i> of ToyServer.
     * @param key The key of the message to be deleted
     */
    public void clearBuffers(Types.Meta key) {
        wrbService.clearBuffers(key);
    }

    public long getTotolDec() {
        return wrbService.getTotalDeliveredTries();
    }

    public long getOptemisticDec() {
        return wrbService.getOptimialDec();
    }
}
