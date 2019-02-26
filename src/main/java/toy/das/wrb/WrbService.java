package toy.das.wrb;

import com.google.protobuf.ByteString;
import toy.config.Config;
import toy.config.Node;
import toy.crypto.BlockDS;
import toy.das.bbc.BbcService;
import toy.crypto.DigestMethod;
import toy.crypto.SslUtils;
import io.grpc.*;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import toy.proto.Types;
import toy.proto.WrbGrpc;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static java.lang.Math.max;
import static java.lang.String.format;

/**
 * A class that implements the WRB abstraction.
 */
public class WrbService extends WrbGrpc.WrbImplBase {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(WrbService.class);
    class authInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall,
                                                                     Metadata metadata,
                                                                     ServerCallHandler<ReqT, RespT> serverCallHandler) {
            ServerCall.Listener res = new ServerCall.Listener() {};
//            try {
//                String peerDomain = serverCall.getAttributes()
//                        .get(Grpc.TRANSPORT_ATTR_SSL_SESSION).getPeerPrincipal().getName().split("=")[1];
////                int peerId = Integer.parseInt(Objects.requireNonNull(metadata.get
//                        (Metadata.Key.of("id", ASCII_STRING_MARSHALLER))));
                String peerIp = Objects.requireNonNull(serverCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)).
                        toString().split(":")[0].replace("/", "");
                int peerId = Integer.parseInt(Objects.requireNonNull(metadata.get
                        (Metadata.Key.of("id", ASCII_STRING_MARSHALLER))));
//                logger.debug(format("[#%d] peerDomain=%s", id, peerIp));
//                if (nodes.get(peerId).getAddr().equals(peerIp)) {
                    return serverCallHandler.startCall(serverCall, metadata);
//                }
//            } catch (SSLPeerUnverifiedException e) {
//                logger.error(format("[#%d]", id), e);
//            } finally {
//                return res;
//            }
//            return res;

        }
    }

    class clientTlsIntercepter implements ClientInterceptor {

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                                   CallOptions callOptions,
                                                                   Channel channel) {
            return new ForwardingClientCall.
                    SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(methodDescriptor, callOptions)) {
                        @Override
                        public void start(Listener<RespT> responseListener, Metadata headers) {
                            headers.put( Metadata.Key.of("id", ASCII_STRING_MARSHALLER), String.valueOf(id));
                            super.start(responseListener, headers);
                }
            };
        }
    }
    class fvote {
        int cid;
        int cidSeries;
        ArrayList<Integer> voters;
        Types.BbcDecision.Builder dec = Types.BbcDecision.newBuilder();
    }
    class peer {
        ManagedChannel channel;
        WrbGrpc.WrbStub stub;

        peer(Node node) {
            try {
                channel = SslUtils.buildSslChannel(node.getAddr(), node.getRmfPort(),
                        SslUtils.buildSslContextForClient(Config.getCaRootPath(),
                                Config.getServerCrtPath(), Config.getServerTlsPrivKeyPath())).
                        intercept(new clientTlsIntercepter()).build();
            } catch (SSLException e) {
                logger.fatal(format("[#%d]", id), e);
            }
            stub = WrbGrpc.newStub(channel);
        }

        void shutdown() {
                channel.shutdown();

        }
    }
    protected int id;
    private int n;
    protected int f;
    private BbcService bbcService;
    private final ConcurrentHashMap<Types.Meta, Types.Block>[] recMsg;
    private final ConcurrentHashMap<Types.Meta, fvote>[] fVotes;
    private final ConcurrentHashMap<Types.Meta, Types.Block>[] pendingMsg;
    private final ConcurrentHashMap<Types.Meta, Types.BbcDecision>[] fastBbcCons;
    private Object[] fastVoteNotifyer;
    private Object[] msgNotifyer;
    private Map<Integer, peer> peers;
    private List<Node> nodes;
    private Thread bbcServiceThread;
    private String bbcConfig;
    private final ConcurrentHashMap<Types.Meta, Types.BbcDecision>[] regBbcCons;
    private AtomicBoolean stopped = new AtomicBoolean(false);
    private Server rmfServer;
    private int channels;
    private int[] alive;
    private int tmo;
    private int tmoInterval;
    private int[] currentTmo;
    private AtomicInteger totalDeliveredTries = new AtomicInteger(0);
    private AtomicInteger optimialDec = new AtomicInteger(0);

    public WrbService(int channels, int id, int f, int tmo, int tmoInterval, ArrayList<Node> nodes, String bbcConfig,
                      String serverCrt, String serverPrivKey, String caRoot) {
        this.channels = channels;
        this.tmo = tmo;
        this.tmoInterval = tmoInterval;
        this.currentTmo = new int[channels];
        this.bbcConfig = bbcConfig;
        this.fVotes = new ConcurrentHashMap[channels];
        this.pendingMsg =  new ConcurrentHashMap[channels];
        this.recMsg =  new ConcurrentHashMap[channels];

        this.peers = new HashMap<>();
        this.f = f;
        this.n = 3*f +1;
        this.id = id;
        this.nodes = nodes;
        this.fastBbcCons = new ConcurrentHashMap[channels];
        this.regBbcCons = new ConcurrentHashMap[channels];
        this.alive = new int[channels];
        fastVoteNotifyer = new Object[channels];
        msgNotifyer = new Object[channels];
        for (int i = 0 ; i < channels ; i++) {
            fastVoteNotifyer[i] = new Object();
            msgNotifyer[i] = new Object();
            recMsg[i] = new ConcurrentHashMap<>();
            pendingMsg[i] = new ConcurrentHashMap<>();
            fVotes[i] = new ConcurrentHashMap<>();
            fastBbcCons[i] = new ConcurrentHashMap<>();
            regBbcCons[i] = new ConcurrentHashMap<>();
            alive[i] = tmo;
            this.currentTmo[i] = tmo;

        }
        startGrpcServer(serverCrt, serverPrivKey, caRoot);
    }


    private void startGrpcServer(String serverCrt, String serverPrivKey, String caRoot) {
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            logger.debug(format("[#%d] There are %d CPU's in the system", id, cores));
            Executor executor = Executors.newFixedThreadPool(channels * n);
            EventLoopGroup weg = new NioEventLoopGroup(cores);
            rmfServer = NettyServerBuilder.
                    forPort(nodes.get(id).getRmfPort()).
                    executor(executor)
                    .workerEventLoopGroup(weg)
                    .bossEventLoopGroup(weg)
                    .sslContext(SslUtils.buildSslContextForServer(serverCrt,
                            caRoot, serverPrivKey)).
                    addService(this).
                    intercept(new authInterceptor()).
                    build().
                    start();
        } catch (IOException e) {
            logger.fatal(format("[#%d]", id), e);
        }

    }

    void start() {
        CountDownLatch latch = new CountDownLatch(1);
            this.bbcService = new BbcService(channels, id, 2*f + 1, bbcConfig);
            bbcServiceThread = new Thread(() -> {
                /*
                    Note that in case that there is more than one bbc Server this call blocks until all servers are up.
                 */
                this.bbcService.start();
                latch.countDown();
            }
            );
            bbcServiceThread.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("", e);
        }
        for (Node n : nodes) {
            peers.put(n.getID(), new peer(n));
        }
        logger.debug(format("[#%d] initiates grpc clients", id));
        logger.debug(format("[#%d] starting wrb service", id));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.get()) return;
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            logger.warn(format("[#%d] *** shutting down wrb service since JVM is shutting down", id));
            shutdown();
        }));
    }

    long getTotalDeliveredTries() {
        return totalDeliveredTries.get();
    }

    long getOptimialDec() {
        return optimialDec.get();
    }

    void shutdown() {
        stopped.set(true);
        for (peer p : peers.values()) {
            p.shutdown();
        }

        logger.debug(format("[#%d] shutting down wrb clients", id));
        if (bbcService != null) bbcService.shutdown();

        try {
            if (bbcServiceThread != null) {
                bbcServiceThread.interrupt();
                bbcServiceThread.join();
            }

        } catch (InterruptedException e) {
            logger.error("", e);
        }
        if (rmfServer != null) {
            rmfServer.shutdown();
        }
        logger.info(format("[#%d] shutting down wrb service", id));
    }

    void sendDataMessage(WrbGrpc.WrbStub stub, Types.Block msg) {
        stub.disseminateMessage(msg, new StreamObserver<Types.Empty>() {
            @Override
            public void onNext(Types.Empty ans) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }

    private void sendFastVoteMessage(WrbGrpc.WrbStub stub, Types.BbcMsg v) {
        stub.fastVote(v, new StreamObserver<Types.Empty>() {
            @Override
            public void onNext(Types.Empty empty) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }
    private void sendReqMessage(WrbGrpc.WrbStub stub, Types.Req req, int channel, int cidSeries, int cid, int sender, int height) {
        stub.reqMessage(req, new StreamObserver<Types.Res>() {
            @Override
            public void onNext(Types.Res res) {
                Types.Meta key = Types.Meta.newBuilder()
                        .setChannel(channel)
                        .setCidSeries(cidSeries)
                        .setCid(cid)
                        .build();
                if (res.getData().getHeader().getM().getCid() == cid &&
                    res.getData().getHeader().getM().getCidSeries() == cidSeries
                    && res.getM().getChannel() == channel &&
                    res.getData().getHeader().getM().getChannel() == channel) {
                    if (recMsg[channel].containsKey(key)) return;
                    if (pendingMsg[channel].containsKey(key)) return;
                    if (!BlockDS.verify(sender, res.getData())) {
                        logger.debug(String.format("[#%d-C[%d]] has received invalid response message from [#%d] for [cidSeries=%d ; cid=%d]",
                                id, channel, res.getM().getSender(), cidSeries, cid));
                        return;
                    }
                    logger.debug(String.format("[#%d-C[%d]] has received response message from [#%d] for [cidSeries=%d ; cid=%d]",
                            id, channel, res.getM().getSender(), cidSeries,  cid));
                    synchronized (msgNotifyer[channel]) {
                        pendingMsg[channel].put(key, res.getData());
                        msgNotifyer[channel].notify();
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }
    private void broadcastReqMsg(Types.Req req, int channel, int cidSeries, int cid, int sender, int height) {
        logger.debug(String.format("[#%d-C[%d]] broadcasts request message [cidSeries=%d ; cid=%d]", id,
                req.getMeta().getChannel(), cidSeries, cid));
        for (int p : peers.keySet()) {
            if (p == id) continue;
            sendReqMessage(peers.get(p).stub, req, channel, cidSeries, cid, sender, height);
        }
    }
    private void broadcastFastVoteMessage(Types.BbcMsg v) {
        for (int p : peers.keySet()) {
            sendFastVoteMessage(peers.get(p).stub, v);
        }
    }

    void rmfBroadcast(Types.Block msg) {
        msg = msg
                .toBuilder()
                .setSt(msg.getSt()
                        .toBuilder()
                        .setProposed(System.currentTimeMillis()))
                .build();
        for (peer p : peers.values()) {
            sendDataMessage(p.stub, msg);
        }
    }
    private void addToPendings(Types.Block request) {
        int sender = request.getHeader().getM().getSender();
        long start = System.currentTimeMillis();
        if (!BlockDS.verify(sender, request)) return;
        request = request.toBuilder()
                .setSt(request.getSt().toBuilder().setVerified(System.currentTimeMillis() - start)
                .setProposed(System.currentTimeMillis()))
                .build();
        int cid = request.getHeader().getM().getCid();
        int channel = request.getHeader().getM().getChannel();
        int cidSeries = request.getHeader().getM().getCidSeries();
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
          if (recMsg[channel].containsKey(key)) return;
        synchronized (msgNotifyer[channel]) {
            pendingMsg[channel].putIfAbsent(key, request);
            msgNotifyer[channel].notify();
        }
        logger.debug(format("[#%d-C[%d]] has received data message from [%d], [cidSeries=%d ; cid=%d]"
                , id, channel, sender, cidSeries, cid));
    }

    @Override
    public void disseminateMessage(Types.Block request, StreamObserver<Types.Empty> responseObserver) {
        addToPendings(request);
    }

    @Override
    public void fastVote(Types.BbcMsg request, StreamObserver<Types.Empty> responeObserver) {
        if (request.hasNext()) {
            addToPendings(request.getNext());
        }
        int cid = request.getM().getCid();
        int cidSeries = request.getM().getCidSeries();
        int channel = request.getM().getChannel();
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
        fVotes[channel].computeIfAbsent(key, k -> {
            fvote v = new fvote();
            v.dec.setM(Types.Meta.newBuilder()
                    .setChannel(channel)
                    .setCidSeries(cidSeries)
                    .setCid(cid));
            v.cid = cid;
            v.cidSeries = cidSeries;
            v.voters = new ArrayList<>();
            return v;
        });

        if (fVotes[channel].get(key).voters.size() + 1 == n) {
            synchronized (fastVoteNotifyer[channel]) {
                fVotes[channel].computeIfPresent(key, (k, v) -> {
                    if (regBbcCons[channel].containsKey(key)) return v;
                    if (fastBbcCons[channel].containsKey(key)) return v;
                    int sender = request.getM().getSender();
                    if (v.voters.contains(sender)) {
                        logger.debug(format("[#%d-C[%d]] has received duplicate vote from [%d] for [channel=%d ; cidSeries=%d ; cid=%d]",
                                id, channel, sender, channel, cidSeries, cid));
                        return v;
                    }
                    v.voters.add(sender);
                    if (v.voters.size() == n) {
                        bbcService.updateFastVote(v.dec.setDecosion(1).build());
                        fastBbcCons[channel].put(k, v.dec.setDecosion(1).build());
                        fastVoteNotifyer[channel].notify();
                        logger.debug(format("[#%d-C[%d]] has received n fast votes for [channel=%d ; cidSeries=%d ; cid=%d]",
                                id, channel, channel, cidSeries, cid));
                    }
                    return v;
                });
            }
        } else if (fVotes[channel].get(key).voters.size() + 1 < n){
                fVotes[channel].computeIfPresent(key, (k, v) -> {
                    if (regBbcCons[channel].containsKey(key)) return v;
                    if (fastBbcCons[channel].containsKey(key)) return v;
                    int sender = request.getM().getSender();
                    if (v.voters.contains(sender)) {
                        logger.debug(format("[#%d-C[%d]] has received duplicate vote from [%d] for [channel=%d ; cidSeries=%d ; cid=%d]",
                                id, channel, sender, channel, cidSeries, cid));
                        return v;
                    }
                    v.voters.add(sender);
                    return v;
                });
            }
    }

    @Override
    public void reqMessage(Types.Req request, StreamObserver<Types.Res> responseObserver)  {
        Types.Block msg;
        int cid = request.getMeta().getCid();
        int cidSeries = request.getMeta().getCidSeries();
        int channel = request.getMeta().getChannel();
        Types.Meta key =  Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
        msg = recMsg[channel].get(key);
        if (msg == null) {
            msg = pendingMsg[channel].get(key);
        }
        if (msg != null) {
            logger.debug(String.format("[#%d-C[%d]] has received request message from [#%d] of [cidSeries=%d ; cid=%d]",
                    id, channel, request.getMeta().getSender(), cidSeries, cid));
            Types.Meta meta = Types.Meta.newBuilder().
                    setSender(id).
                    setChannel(channel).
                    setCid(cid).
                    setCidSeries(cidSeries).
                    build();
            responseObserver.onNext(Types.Res.newBuilder().
                    setData(msg).
                    setM(meta).
                    build());
        } else {
            logger.debug(String.format("[#%d-C[%d]] has received request message from [#%d] of [cidSeries=%d ; cid=%d] but buffers are empty",
                    id, channel, request.getMeta().getSender(), cidSeries, cid));
        }
    }

    Types.Block deliver(int channel, int cidSeries, int cid, int sender, int height, Types.Block next)
            throws InterruptedException
    {
        totalDeliveredTries.getAndIncrement();

        long estimatedTime;
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
        Types.Meta key2 = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid + 2)
                .build();
        int realTmo = currentTmo[channel];
        long startTime = System.currentTimeMillis();
        synchronized (msgNotifyer[channel]) {
            while (realTmo > 0 && !pendingMsg[channel].containsKey(key)) {
                if (pendingMsg[channel].containsKey(key2)) break;
                msgNotifyer[channel].wait(realTmo);
                realTmo -= System.currentTimeMillis() - startTime;
            }
        }

        estimatedTime = System.currentTimeMillis() - startTime;
        logger.debug(format("[#%d-C[%d]] have waited [%d] ms for data msg [cidSeries=%d ; cid=%d]",
                id, channel, estimatedTime, cidSeries, cid));
        pendingMsg[channel].computeIfPresent(key, (k, val) -> {
            long start = System.currentTimeMillis();
            if (val.getHeader().getM().getSender() != sender ||
                    val.getHeader().getM().getCidSeries() != cidSeries ||
                    val.getHeader().getM().getCid() != cid ||
                    val.getHeader().getM().getChannel() != channel) return val;
            Types.BbcMsg.Builder bv = Types.BbcMsg
                    .newBuilder()
                    .setM(Types.Meta.newBuilder()
                            .setChannel(channel)
                            .setCidSeries(cidSeries)
                            .setCid(cid)
                            .setSender(id)
                            .build())
                    .setVote(1);
            if (next != null) {
                logger.debug(String.format("[#%d-C[%d]] broadcasts [cidSeries=%d ; cid=%d] via fast mode",
                        id, channel, next.getHeader().getM().getCidSeries(), next.getHeader().getM().getCid()));
                long st = System.currentTimeMillis();
                bv.setNext(setFastModeData(val, next));
                logger.debug(String.format("[#%d-C[%d]] creating new Block of [cidSeries=%d ; cid=%d] took about [%d] ms",
                        id, channel, next.getHeader().getM().getCidSeries(), next.getHeader().getM().getCid(),
                        System.currentTimeMillis() - st));
            }
            broadcastFastVoteMessage(bv.build());
            logger.debug(format("[#%d-C[%d]] sending fast vote [cidSeries=%d ; cid=%d] took about[%d] ms",
                    id, channel, cidSeries, cid, System.currentTimeMillis() - start));
            return val;
        });

        startTime = System.currentTimeMillis();
        synchronized (fastVoteNotifyer[channel]) {
            fvote fv = fVotes[channel].get(key);
            if (currentTmo[channel] - estimatedTime > 0 && (fv == null || fv.voters.size() < n)) {
                logger.debug(format("[#%d-C[%d]] will wait at most [%d] ms for fast bbc", id, channel,
                        max(currentTmo[channel] - estimatedTime, 1)));
                fastVoteNotifyer[channel].wait(currentTmo[channel] - estimatedTime);
            }
            logger.debug(format("[#%d-C[%d]] have waited for more [%d] ms for fast bbc", id, channel,
                    System.currentTimeMillis() - startTime));
        }

        currentTmo[channel] += tmoInterval;

        fVotes[channel].computeIfPresent(key, (k, val) -> {
            int vote = 0;
            if (pendingMsg[channel].containsKey(k)) {
                currentTmo[channel] = tmo;
                vote = 1;
            }
            int votes = val.voters.size();
            if (votes < n) {
                regBbcCons[channel].put(k, Types.BbcDecision.newBuilder().setDecosion(vote).buildPartial());
                return val;
            }
            logger.debug(format("[#%d-C[%d]] deliver by fast vote [cidSeries=%d ; cid=%d]", id, channel, cidSeries, cid));
            return val;
        });

        fVotes[channel].computeIfAbsent(key, k -> {
            fvote vi = new fvote();
            vi.dec.setM(Types.Meta.newBuilder()
                    .setChannel(channel)
                    .setCidSeries(cidSeries)
                    .setCid(cid));
            vi.cid = cid;
            vi.cidSeries = cidSeries;
            vi.voters = new ArrayList<>();
            regBbcCons[channel].put(k, Types.BbcDecision.newBuilder().setDecosion(0).buildPartial());
            return vi;
        });
        if (regBbcCons[channel].containsKey(key)) {
            int v = regBbcCons[channel].get(key).getDecosion();
            int dec = fullBbcConsensus(channel, v, cidSeries, cid);
            logger.debug(format("[#%d-C[%d]] bbc returned [%d] for [cidSeries=%d ; cid=%d]", id, channel, dec, cidSeries, cid));
            if (dec == 0) {
                logger.debug(format("[#%d-C[%d]] timeout increased to [%d]", id, channel, currentTmo[channel]));
                pendingMsg[channel].remove(key);
                return null;
            }
        }  else {
            optimialDec.getAndIncrement();
        }

        requestData(channel, cidSeries, cid, sender, height);
        Types.Block msg = pendingMsg[channel].get(key);
        pendingMsg[channel].remove(key);
        recMsg[channel].put(key, msg);
        return msg;
    }

    private int fullBbcConsensus(int channel, int vote, int cidSeries, int cid) throws InterruptedException {
        logger.debug(format("[#%d-C[%d]] Initiates full bbc instance [cidSeries=%d ; cid=%d], [vote:%d]", id, channel, cidSeries, cid, vote));
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
        bbcService.propose(vote, channel, cidSeries, cid);
        Types.BbcDecision dec = bbcService.decide(channel, cidSeries, cid);
        regBbcCons[channel].remove(key);
        regBbcCons[channel].put(key, dec);
        return dec.getDecosion();
    }

    private void requestData(int channel, int cidSeries, int cid, int sender, int height) throws InterruptedException {
        Types.Meta key = Types.Meta.newBuilder()
                .setChannel(channel)
                .setCidSeries(cidSeries)
                .setCid(cid)
                .build();
        if (pendingMsg[channel].containsKey(key) &&
                pendingMsg[channel].get(key).getHeader().getM().getSender() == sender) return;
        pendingMsg[channel].remove(key);
        Types.Meta meta = Types.Meta.
                newBuilder().
                setCid(cid).
                setCidSeries(cidSeries).
                setChannel(channel)
                .setSender(id).
                build();
        Types.Req req = Types.Req.newBuilder().setMeta(meta).build();
        broadcastReqMsg(req,channel,  cidSeries, cid, sender, height);
        synchronized (msgNotifyer[channel]) {
            Types.Block msg = pendingMsg[channel].get(key);
            while ( msg == null ||  msg.getHeader().getM().getSender() != sender) {
                pendingMsg[channel].remove(key);
                msgNotifyer[channel].wait();
                msg = pendingMsg[channel].get(key);
            }
        }

    }

    void clearBuffers(Types.Meta key) {
        int channel = key.getChannel();

        recMsg[channel].remove(key);
        fVotes[channel].remove(key);
        pendingMsg[channel].remove(key);
        fastBbcCons[channel].remove(key);
        regBbcCons[channel].remove(key);

        bbcService.clearBuffers(key);
    }

    private Types.Block setFastModeData(Types.Block curr, Types.Block next) {
        Types.Block.Builder nextBuilder = next
                    .toBuilder()
                    .setHeader(next.getHeader().toBuilder()
                    .setPrev(ByteString
                            .copyFrom(DigestMethod
                                    .hash(curr.getHeader().toByteArray())))
                            .build());
        String signature = BlockDS.sign(next.getHeader());
        return nextBuilder.setHeader(nextBuilder.getHeader().toBuilder()
                .setProof(signature))
                .setSt(nextBuilder.getSt().toBuilder())
                .build();
    }
    
}
