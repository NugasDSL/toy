package toy.das.bbc;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.MessageContext;
import bftsmart.tom.RequestContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import toy.proto.Types;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static java.lang.String.format;

/**
 * This class implements a Byznatine Binary Consensus (BBC) based on Atomic Broadcast OF bft-SMaRt.
 */
public class BbcService extends DefaultSingleRecoverable {
    class consVote {
        int pos = 0;
        int neg = 0;
        ArrayList<Integer> proposers = new ArrayList<>();
    }

    class fastVotePart {
        Types.BbcDecision d;
        boolean done;
    }
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(BbcService.class);

    private int id;
    private Semaphore[] channelNotifyer;
    private AsynchServiceProxy bbcProxy;

    private final ConcurrentHashMap<Types.Meta, consVote> rec;

    private int quorumSize;
    private String configHome;
    private ServiceReplica sr;
    private boolean stopped = false;
    private ConcurrentHashMap<Types.Meta, fastVotePart> fastVote = new ConcurrentHashMap<>();

    /**
     * Constructor
     * @param channels the number of channels in the system (with a single Toy group this is always 1)
     * @param id the server id
     * @param quorumSize the required quorum size (2f + 1 in the Byznatine settings)
     * @param configHome a path to bft-SMaRt configuration directory
     */
    public BbcService(int channels, int id, int quorumSize, String configHome) {
        this.id = id;
        rec = new ConcurrentHashMap<>();
        sr = null;
        this.quorumSize = quorumSize;
        this.configHome = configHome;
        channelNotifyer = new Semaphore[channels];
        for (int i = 0 ; i < channels ; i++) {
            channelNotifyer[i] = new Semaphore(0);
        }
    }

    /**
     * Clearing the buffers from irrelevant messages. This method is called bt <i>gc</i> of ToyServer.
     * @param key The key of the message to be deleted
     */
    public void clearBuffers(Types.Meta key) {
        rec.remove(key);
        fastVote.remove(key);
    }

    /**
     * Starts the service.
     */
    public void start() {
        sr = new ServiceReplica(id, this, this, configHome);
        bbcProxy = new AsynchServiceProxy(id, configHome);
        logger.info(format("[#%d] bbc service is up", id));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped) return;
         // Use stderr here since the logger may have been reset by its JVM shutdown hook.
         logger.warn(format("[#%d] shutting down bbc Server since JVM is shutting down", id));
         shutdown();
        }));
    }

    /**
     * shutdown the service.
     */
    public void shutdown() {
        stopped = true;
        if (bbcProxy != null) {
            bbcProxy.close();
            logger.debug(format("[#%d] shutting down bbc client", id));
        }
        if (sr != null) {
            sr.kill();
            logger.info(format("[#%d] shutting down bbc Server", id));
            sr = null;
        }
        logger.debug(format("[#%d] shutting down bbc service", id));
    }

    @Override
    public void installSnapshot(byte[] state) {
        logger.debug(format("[#%d] installSnapshot called", id));
    }

    @Override
    public byte[] getSnapshot() {
        logger.debug(format("[#%d] getSnapshot called", id));
        return new byte[1];
    }

    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        try {
            Types.BbcMsg msg = Types.BbcMsg.parseFrom(command);
            int channel = msg.getM().getChannel();
            int cidSeries = msg.getM().getCidSeries();
            int cid = msg.getM().getCid();
            Types.Meta key = Types.Meta.newBuilder()
                    .setCidSeries(cidSeries)
                    .setCid(cid)
                    .setChannel(channel)
                    .build();
            logger.debug(String.format("[#%d] has received bbc message from [sender=%d ; channel=%d ; cidSeries=%d ; cid=%d]",
                    id, msg.getM().getSender(), channel, cidSeries, cid));
            fastVote.computeIfAbsent(key, k1 -> {
                rec.computeIfAbsent(key, k -> new consVote());
                rec.computeIfPresent(key, (k, v) -> {
                    if (v.proposers.contains(msg.getM().getSender())) return v;
                    if (v.pos + v.neg == quorumSize) return v;
                    v.proposers.add(msg.getM().getSender());
                    if (msg.getVote() == 1) {
                        v.pos++;
                    } else {
                        v.neg++;
                    }
                    if (v.pos + v.neg == quorumSize) {
                        logger.debug(format("[#%d] notifies on  [channel=%d ; cidSeries=%d ; cid=%d]", id, channel, cidSeries, cid));
                        channelNotifyer[channel].release();

                    }
                    return v;
                });

                return null;
            });
            fastVote.computeIfPresent(key, (k, v) -> {
                if (!v.done && msg.getM().getSender() != id) {
                    logger.debug(format("[#%d] #1 re-participating in a das " +
                            "[channel=%d ; cidSeries=%d ; cid=%d]", id, channel, cidSeries, cid));
                    propose(v.d.getDecosion(), channel, cidSeries, cid);
                    v.done = true;
                }
                return v;
            });

        } catch (Exception e) {
            logger.error(format("[#%d]", id), e);
        }
        return new byte[0];
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return new byte[1];
    }

    /**
     * Delivers the consensus with (cid, cidSeries) result.
     * @param channel the channel that wish to decide
     * @param cidSeries an identifier of the consensus series (series changes due to an invocation of the synchronization
     *                  mechanism)
     * @param cid an identifier of the consensus in the given cidSeries
     * @return the decision
     * @throws InterruptedException
     */
    public Types.BbcDecision decide(int channel, int cidSeries, int cid) throws InterruptedException {
            Types.Meta key = Types.Meta.newBuilder()
                    .setCidSeries(cidSeries)
                    .setCid(cid)
                    .setChannel(channel)
                    .build();


            consVote v = rec.get(key);
            while (v == null || v.neg + v.pos < quorumSize) {
                    channelNotifyer[channel].acquire();
                v = rec.get(key);
            }


            return Types.BbcDecision.newBuilder()
                    .setM(Types.Meta.newBuilder()
                            .setChannel(channel)
                            .setCidSeries(cidSeries)
                            .setCid(cid)
                            .build())
                    .setDecosion(v.pos > v.neg ? 1: 0)
                    .build();
    }

    /**
     * Propose a value in the consensus with (cid, cidSeries).
     * @param vote the proposal
     * @param channel the proposer channel (with a single Toy group this is always 0)
     * @param cidSeries the series of this consensus instance (series changes due to an invocation of the synchronization
     *                  mechanism)
     * @param cid the id of this consensus in the given cidSeries
     * @return
     */
    public int propose(int vote, int channel, int cidSeries, int cid) {
        Types.BbcMsg msg= Types.BbcMsg.newBuilder()
                .setM(Types.Meta.newBuilder()
                        .setChannel(channel)
                        .setCidSeries(cidSeries)
                        .setCid(cid)
                        .setSender(id)
                        .build())
                .setVote(vote)
                .build();
        byte[] data = msg.toByteArray();
        bbcProxy.invokeAsynchRequest(data, new ReplyListener() {
            @Override
            public void reset() {

            }

            @Override
            public void replyReceived(RequestContext context, TOMMessage reply) {

            }
        }, TOMMessageType.ORDERED_REQUEST);
        return 0;
    }

    /**
     * An inner method that supports speculative BBC (used by WrbSerivce)
     * @param b
     */
    public void updateFastVote(Types.BbcDecision b) {
            Types.Meta key = Types.Meta.newBuilder()
                    .setChannel(b.getM().getChannel())
                    .setCidSeries(b.getM().getCidSeries())
                    .setCid(b.getM().getCid())
                    .build();
            fastVote.computeIfAbsent(key, k -> {
                fastVotePart fv = new fastVotePart();
                fv.d = b;
                fv.done = false;
                rec.computeIfPresent(key, (k1, v1) -> {
                        logger.debug(String.format("[#%d] #2 re-participating in a das " +
                                "[channel=%d ; cidSeries=%d ; cid=%d]", id, b.getM().getChannel(), b.getM().getCidSeries(), b.getM().getCid()));
                        propose(b.getDecosion(), b.getM().getChannel(), b.getM().getCidSeries(), b.getM().getCid());
                        fv.done = true;
                    return v1;
                });
                return fv;
            });
    }

    /**
     * An inner method that supports speculative BBC (used by WrbSerivce)
     * @param b
     */
    public void periodicallyVoteMissingConsensus(Types.BbcDecision b) {
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(b.getM().getChannel())
                .setCidSeries(b.getM().getCidSeries())
                .setCid(b.getM().getCid())
                .build();
        if (rec.containsKey(key)) {
            fastVote.computeIfAbsent(key, k -> {
                fastVotePart fv = new fastVotePart();
                fv.d = b;
                fv.done = false;
                return fv;
            });
            fastVote.computeIfPresent(key, (k, v) -> {
                if (!v.done) {
                    logger.debug(format("[#%d] re-participating in a das (periodicallyVoteMissingConsensus) " +
                                    "[channel=%d ; cidSeries=%d ; cid=%d]", id, key.getChannel(), key.getCidSeries()
                            , key.getCid()));
                    propose(b.getDecosion(), key.getChannel(), key.getCidSeries(), key.getCid());
                    v.done = true;
                }
                return v;
            });
        }
    }
}

