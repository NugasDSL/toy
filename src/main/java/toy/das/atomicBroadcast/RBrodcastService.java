package toy.das.atomicBroadcast;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.MessageContext;
import bftsmart.tom.RequestContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import com.google.protobuf.ByteString;
import toy.proto.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class RBrodcastService extends DefaultSingleRecoverable {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(RBrodcastService.class);
    private int id;
    private final HashMap<Integer, List<Types.RBMsg>> recMsg;
    private AsynchServiceProxy RBProxy;
    private ServiceReplica sr;
    private String configHome;
    Object[] cNotifyer;
    private int cid = 0;
    private boolean stopped = false;

    public RBrodcastService(int channels, int id, String configHome) {
        this.id = id;
        this.configHome = configHome;
        sr = null;
        recMsg = new HashMap<>();
        cNotifyer = new Object[channels];
//        notifyer = new Semaphore[channels];
        for (int i = 0 ; i < channels ; i++) {
            cNotifyer[i] = new Object();
        }
    }

    public void clearBuffers(Types.Meta key) {
        synchronized (recMsg) {
            int channel = key.getChannel();
            int cid = key.getCid();
            if (!recMsg.containsKey(channel)) return;
            recMsg.replace(channel, recMsg
                    .get(key.getChannel())
                    .stream()
                    .filter(m -> m.getM().getChannel() == channel && m.getM().getCid() == cid)
                    .collect(Collectors.toList()));
        }
    }

    public void start() {
        sr = new ServiceReplica(id, this, this, configHome);
        RBProxy = new AsynchServiceProxy(id, configHome);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped) return;
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            logger.warn(format("[#%d] shutting down RB Server since JVM is shutting down", id));
            shutdown();

        }));
    }
    public void shutdown() {
        stopped = true;
//        releaseWaiting();
        if (RBProxy != null) {
            RBProxy.close();
            logger.debug(format("[#%d] shut down rb client", id));
        }
        if (sr != null) {
            sr.kill();
            logger.debug(format("[#%d] shutting sown rb Server", id));
            sr = null;
        }
        logger.info(format("[#%d] shutting down rb service", id));
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
            Types.RBMsg msg = Types.RBMsg.parseFrom(command);
            if (msg == null) {
                logger.debug("Received NULL message!!!!!");
            }
            int channel = msg.getM().getChannel();
            synchronized (cNotifyer[channel]) {
                recMsg.computeIfAbsent(channel, k -> new ArrayList<>());
                recMsg.computeIfPresent(channel, (k, v) -> {
                    v.add(msg);
                    cNotifyer[channel].notifyAll();
                    return v;
                });
            }

        } catch (Exception e) {
            logger.error(format("[#%d]", id), e);
        }

        return new byte[0];
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return new byte[1];
    }

    public byte[] deliver(int channel) throws InterruptedException {
        synchronized (cNotifyer[channel]) {
            while (!recMsg.containsKey(channel) || recMsg.get(channel).isEmpty()) {
                cNotifyer[channel].wait();
            }
            Types.RBMsg msg = recMsg.get(channel).get(0);
            recMsg.get(channel).remove(0);
            return msg.getData().toByteArray();
        }
    }

    public int broadcast(byte[] m, int channel, int id) {
        Types.RBMsg msg = Types.RBMsg.
                newBuilder().
                setM(Types.Meta.newBuilder()
                        .setChannel(channel)
                        .setSender(id)
                        .setCid(cid)
                        .build()).
                setData(ByteString.copyFrom(m)).
                build();
        int ret = cid;
        cid++;
        byte[] data = msg.toByteArray();
        RBProxy.invokeAsynchRequest(data, new ReplyListener() {
            @Override
            public void reset() {

            }

            @Override
            public void replyReceived(RequestContext context, TOMMessage reply) {

            }
        }, TOMMessageType.ORDERED_REQUEST);
        return ret;
    }

}

