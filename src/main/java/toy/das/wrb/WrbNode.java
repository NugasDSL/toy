package toy.das.wrb;

import toy.config.Node;
import toy.proto.Types;

import java.util.ArrayList;

import static java.lang.String.format;


public class WrbNode extends Node {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(WrbNode.class);
    WrbService wrbService;

    public WrbNode(int channels, int id, String addr, int rmfPort, int f , int tmo, int tmoInterval, ArrayList<Node> nodes, String bbcConfig,
                   String serverCrt, String serverPrivKey, String caRoot) {
        super(addr, rmfPort,  id);
        wrbService = new WrbService(channels, id, f, tmo, tmoInterval, nodes, bbcConfig, serverCrt, serverPrivKey, caRoot);
    }

    public void stop() {
        if (wrbService != null)
            wrbService.shutdown();
    }

    public void start() {
        wrbService.start();
    }

    public void broadcast(Types.Block data) {
        logger.debug(String.format("[#%d] broadcasts data message with [height=%d]", getID(), data.getHeader().getHeight()));

        wrbService.rmfBroadcast(data);
    }

    public Types.Block deliver(int channel, int cidSeries, int cid, int height, int sender, Types.Block msg)
            throws InterruptedException {
        long start = System.currentTimeMillis();
        Types.Block m = wrbService.deliver(channel, cidSeries, cid, sender, height, msg);
        logger.debug(format("[#%d-C[%d]] Deliver on wrb node took about %d [cidSeries=%d ; cid=%d]", getID(), channel,
                System.currentTimeMillis() - start, cidSeries, cid));
        return m;
    }

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