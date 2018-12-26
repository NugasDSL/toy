package toy.servers;

import toy.blockchain.Blockchain;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import toy.config.Config;
import toy.config.Node;
import toy.crypto.BlockDS;
import toy.das.atomicBroadcast.RBrodcastService;
import toy.proto.Types;
import toy.das.wrb.WrbNode;
import toy.blockchain.Block;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.String.format;

/**
 * An implementation of the base class of a Toy server.
 * Every specific Toy implementation should derive from this class and implement the leader phase (for example, implement
 * an asynchronous server)
 */
public abstract class ToyServer extends Node implements Server {
    class fpEntry {
        Types.ForkProof fp;
        boolean done;
    }
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ToyServer.class);
    WrbNode wrbServer;
    private RBrodcastService panicRB;
    private RBrodcastService syncRB;
    final Blockchain bc;
    int currHeight;
    protected int f;
    protected int n;
    int currLeader;
    protected AtomicBoolean stopped = new AtomicBoolean(false);
    Block currBlock;
    private final Object newBlockNotifyer = new Object();
    private int maxTransactionInBlock;
    private Thread mainThread;
    private Thread panicThread;
    int cid = 0;
    int cidSeries = 0;
    private final HashMap<Integer, fpEntry> fp;
    private HashMap<Integer, ArrayList<Types.subChainVersion>> scVersions;
    private final ConcurrentLinkedQueue<Types.Transaction> transactionsPool = new ConcurrentLinkedQueue<>();;
    boolean configuredFastMode;
    boolean fastMode;
    int channel;
    boolean testing = Config.getTesting();
    int txPoolMax = 100000;
    int bareTxSize = Types.Transaction.newBuilder()
            .setClientID(0)
            .setId(Types.txID.newBuilder().setTxID(UUID.randomUUID().toString()).build())
            .build().getSerializedSize() + 8;
    int txSize = 0;
    int cID = new Random().nextInt(10000);

    /**
     * Toy server constructor
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
    public ToyServer(String addr, int wrbPort, int id, int channel, int f,
                     int maxTx, boolean fastMode,
                     WrbNode wrb, RBrodcastService panic, RBrodcastService sync) {
        super(addr, wrbPort, id);
        this.f = f;
        this.n = 3*f + 1;
        this.panicRB = panic;
        this.syncRB = sync;
        bc = initBC(id, channel);
        currBlock = null;
        currHeight = 1; // starts from 1 due to the genesis Block
        currLeader = 0;
        this.maxTransactionInBlock = maxTx;
        fp = new HashMap<>();
        scVersions = new HashMap<>();
        mainThread = new Thread(() -> {
            try {
                mainLoop();
            } catch (Exception ex) {
                logger.error(format("[#%d-C[%d]]", getID(), channel), ex);
                shutdown(true);
            }
        });
        panicThread = new Thread(this::deliverForkAnnounce);
        this.configuredFastMode = fastMode;
        this.fastMode = fastMode;
        this.channel = channel;
        wrbServer = wrb;
        currLeader = channel % n;

        if (testing) {
            txSize = StrictMath.max(0, Config.getTxSize() - bareTxSize);
        }
    }

    /**
     * Toy server constructor
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
    public ToyServer(String addr, int wrbPort, int id, int channel, int f, int tmo, int tmoInterval,
                     int maxTx, boolean fastMode, ArrayList<Node> cluster,
                     String bbcConfig, String panicConfig, String syncConfig,
                     String serverCrt, String serverPrivKey, String caRoot) {

        super(addr, wrbPort, id);
        this.f = f;
        this.n = 3*f + 1;
        wrbServer = new WrbNode(1, id, addr, wrbPort, f, tmo, tmoInterval,
               cluster, bbcConfig, serverCrt, serverPrivKey, caRoot);
        panicRB = new RBrodcastService(1, id, panicConfig);
        syncRB = new RBrodcastService(1, id, syncConfig);
        bc = initBC(id, channel);
        currBlock = null;
        currHeight = 1; // starts from 1 due to the genesis Block
        currLeader = 0;
        this.maxTransactionInBlock = maxTx;
        fp = new HashMap<>();
        scVersions = new HashMap<>();
        mainThread = new Thread(this::mainLoop);
        panicThread = new Thread(this::deliverForkAnnounce);
        this.configuredFastMode = fastMode;
        this.fastMode = fastMode;
        currLeader = channel % n;
        if (testing) {
            txSize = StrictMath.max(0, Config.getTxSize() - bareTxSize);
        }

    }

    @Override
    public void start() {
        start(false);
    }

    /**
     * Starts the server as either part of Toys (e.g as with Top) or a single server
     * @param group indicates if this server is part of group of Toys that collaborate
     *             wrb, panic and sync services
     */
    public void start(boolean group) {
        if (!group) {
            wrbServer.start();
            logger.debug(format("[#%d-C[%d]] wrb Server is up", getID(), channel));
            syncRB.start();
            logger.debug(format("[#%d-C[%d]] sync Server is up", getID(), channel));
            panicRB.start();
            logger.debug(format("[#%d-C[%d]] panic Server is up", getID(), channel));
        }
        logger.info(format("[#%d-C[%d]] is up", getID(), channel));
    }

    @Override
    public void serve() {
        panicThread.start();
        logger.debug(format("[#%d-C[%d]] starts panic thread", getID(), channel));
        mainThread.start();
        logger.debug(format("[#%d-C[%d]] starts main thread", getID(), channel));
        logger.info(format("[#%d-C[%d]] starts serving", getID(), channel));
    }

    @Override
    public void shutdown() {
        shutdown(false);
    }

    /**
     * shutdown the server as either part of Toys (e.g as with Top) or a single server
     * @param group indicates if this server is part of group of Toys that collaborate
     *              wrb, panic and sync services
     */
    public void shutdown(boolean group) {
        stopped.set(true);
        logger.debug(format("[#%d-C[%d]] interrupt main thread", getID(), channel));
        AtomicBoolean j = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            while (!j.get()) {
                mainThread.interrupt();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("", e);
                }
            }
        });
        t.start();
        try {
            mainThread.join();
            j.set(true);
            t.join();
        } catch (InterruptedException e) {
            logger.error(format("[#%d-C[%d]]", getID(), channel), e);
        }

        if (!group) {
            if (wrbServer != null) wrbServer.stop();
            if (panicRB != null) panicRB.shutdown();
            if (syncRB != null) syncRB.shutdown();
        }
        logger.debug(format("[#%d-C[%d]] interrupt panic thread", getID(), channel));
        j.set(false);
        t = new Thread(() -> {
            while (!j.get()) {
                panicThread.interrupt();
                try {
                    Thread.sleep( 1000);
                } catch (InterruptedException e) {
                    logger.error("", e);
                }
            }
        });
        t.start();

        try {
            panicThread.join();
            j.set(true);
            t.join();
        } catch (InterruptedException e) {
            logger.error(format("[#%d-C[%d]]", getID(), channel), e);
        }
        logger.info(format("[#%d-C[%d]] shutdown bc Server", getID(), channel));
    }

    private void updateLeaderAndHeight() {
        currHeight = bc.getHeight() + 1;
        currLeader = (currLeader + 1) % n;
        cid++;
    }

    /**
     * Clear the data of block i from all layers of the algorithm, leaving only its header in the Blockchain.
     * This method is essential to ease the memory bounds of Toy.
     * @param index the block to evacuate
     */
    public void gc(int index)  {
        logger.debug(format("[#%d-C[%d]] clear buffers of [height=%d]", getID(), channel, index));
        Types.Meta key = bc.getBlock(index).getHeader().getM();
        Types.Meta rKey = Types.Meta.newBuilder()
                .setChannel(key.getChannel())
                .setCidSeries(key.getCidSeries())
                .setCid(key.getCid())
                .build();
         wrbServer.clearBuffers(rKey);
        syncRB.clearBuffers(rKey);
        panicRB.clearBuffers(rKey);
        bc.setBlock(index, null);
    }

    private void mainLoop() throws RuntimeException {
        while (!stopped.get()) {
            synchronized (fp) {
                for (Integer key : fp.keySet().stream().filter(k -> k < currHeight).collect(Collectors.toList())) {
                    fpEntry pe = fp.get(key);
                    if (!pe.done) {
                        logger.info(format("[#%d-C[%d]] have found panic message [height=%d] [fp=%d]",
                                getID(), channel, currHeight, key));
                        handleFork(pe.fp);
                        fp.get(key).done = true;
                        fastMode = false;
                    }

                }
            }
            if (!bc.validateCurrentLeader(currLeader, f)) {
                currLeader = (currLeader + 1) % n;
                fastMode = false;
                cid += 2;
            }
            Types.Block next;
            try {
                next = leaderImpl();
            } catch (InterruptedException e) {
                logger.debug(format("[#%d-C[%d]] main thread has been interrupted on leader impl",
                        getID(), channel));
                continue;
            }
            Types.Block recBlock;
            try {
                long startTime = System.currentTimeMillis();
                recBlock = wrbServer.deliver(channel, cidSeries, cid, currHeight, currLeader, next);
                logger.debug(format("[#%d-C[%d]] deliver took about [%d] ms [cidSeries=%d ; cid=%d]",
                        getID(), channel, System.currentTimeMillis() - startTime, cidSeries, cid));
            } catch (InterruptedException e) {
                logger.debug(format("[#%d-C[%d]] main thread has been interrupted on wrb deliver",
                        getID(), channel));
                continue;
            }
            fastMode = configuredFastMode;
            if (recBlock == null) {
                currLeader = (currLeader + 1) % n;
                fastMode = false;
                cid += 2;
                continue;
            }
            if (currLeader == getID()) {
                logger.debug(String.format("[#%d-C[%d]] nullifies currBlock [sender=%d] [height=%d] [cidSeries=%d, cid=%d]",
                        getID(), channel, recBlock.getHeader().getM().getSender(), currHeight, cidSeries, cid));
                currBlock = null;
            }
            if (!bc.validateBlockHash(recBlock)) {
                bc.validateBlockHash(recBlock);
                announceFork(recBlock);
                fastMode = false;
                continue;
            }

            synchronized (newBlockNotifyer) {
                recBlock = recBlock.toBuilder().setSt(recBlock.getSt().toBuilder().setChannelDecided(System.currentTimeMillis())).build();
                bc.addBlock(recBlock);
                if (recBlock.getHeader().getHeight() - (f + 2) > 0) {
                    Types.Block permanent = bc.getBlock(recBlock.getHeader().getHeight() - (f + 2));
                    permanent = permanent.toBuilder().setSt(permanent.getSt().toBuilder().setPd(System.currentTimeMillis())).build();
                     bc.setBlock(recBlock.getHeader().getHeight() - (f + 2), permanent);


                }

                logger.debug(String.format("[#%d-C[%d]] adds new Block with [height=%d] [cidSeries=%d ; cid=%d] [size=%d]",
                        getID(), channel, recBlock.getHeader().getHeight(), cidSeries, cid, recBlock.getDataCount()));
                newBlockNotifyer.notify();

            }
            updateLeaderAndHeight();
        }
    }

    /**
     * Implement the specific leader phase of the Toy server. For example, if you wish to implement an
     * async Toy server (for testing) you should implement a custom leader phase in which at
     * every round the server sleeps for random time
     * @return the block the leader wish to disseminated on its turn
     * @throws InterruptedException
     */
    abstract Types.Block leaderImpl() throws InterruptedException;

    /**
     * Initiate the Blockchain of this server
     * @param id the server ID
     * @param channel the channel ID (with a single Toy group this is always 0)
     * @return new empty Blockchain
     */
    abstract public Blockchain initBC(int id, int channel);

    /**
     * Get sub-blockchain between indexes <i>start</i> and <i>end</i>
     * @param start the index to start with the sub-blockchain
     * @param end the index to end with the sub-blockchain
     * @return a sub-blockchain fron <i>start</i> to <i>end</i>
     */
    abstract public Blockchain getBC(int start, int end);

    /**
     * Get the transaction pool size
     * @return the transaction pool size
     */
    public int getTxPoolSize() {
        return transactionsPool.size();
    }

    @Override
    public Types.txID addTransaction(Types.Transaction tx) {
        if (transactionsPool.size() > txPoolMax) return null;
        transactionsPool.add(tx);
        return tx.getId();
    }

    @Override
    public int isTxPresent(String txID) {
        /*
            Currently nou supported
         */
        return -1;
    }

    void createTxToBlock() {
        while (currBlock.getTransactionCount() < maxTransactionInBlock) {
            long ts = System.currentTimeMillis();
            SecureRandom random = new SecureRandom();
            byte[] tx = new byte[txSize];
            random.nextBytes(tx);
            currBlock.addTransaction(Types.Transaction.newBuilder()
                    .setId(Types.txID.newBuilder().setTxID(UUID.randomUUID().toString()).build())
                    .setClientID(cID)
                    .setClientTs(ts)
                    .setServerTs(ts)
                    .setData(ByteString.copyFrom(tx))
                    .build());
        }

    }

    void addTransactionsToCurrBlock() {
        if (currBlock != null) return;
        currBlock = bc.createNewBLock();
        currBlock.blockBuilder.setSt(Types.blockStatistics
                .newBuilder()
                .build());
        while ((!transactionsPool.isEmpty()) && currBlock.getTransactionCount() < maxTransactionInBlock) {
            Types.Transaction t = transactionsPool.poll();
            if (!currBlock.validateTransaction(t)) {
                logger.debug(format("[#%d-C[%d]] detects an invalid transaction from [client=%d]",
                        getID(), channel, t.getClientID()));
                continue;
            }
            currBlock.addTransaction(t);
        }
        if (testing) {
            createTxToBlock();
        }

    }

    private int validateForkProof(Types.ForkProof p)  {
        logger.debug(format("[#%d-C[%d]] starts validating fp", getID(), channel));
        Types.Block curr = p.getCurr();
        Types.Block prev = p.getPrev();
        int prevBlockH = prev.getHeader().getHeight();

        if (bc.getBlock(prevBlockH).getHeader().getM().getSender() != prev.getHeader().getM().getSender()) {
            logger.debug(format("[#%d-C[%d]] invalid fork proof #1", getID(), channel));
            return -1;
        }

        int currCreator = currLeader;
        if (bc.getHeight() >= curr.getHeader().getHeight()) {
            currCreator = bc.getBlock(curr.getHeader().getHeight()).getHeader().getM().getSender();
        }
        if (currCreator != curr.getHeader().getM().getSender()) {
            logger.debug(format("[#%d-C[%d]] invalid fork proof #2", getID(), channel));
            return -1;
        }
        if (!BlockDS.verify(curr.getHeader().getM().getSender(), curr)) {
                logger.debug(format("[#%d-C[%d]] invalid fork proof #3", getID(), channel));
                return -1;
        }

        if (!BlockDS.verify(prev.getHeader().getM().getSender(), prev)) {
            logger.debug(format("[#%d-C[%d]] invalid fork proof #4", getID(), channel));
            return -1;
        }

        logger.debug(String.format("[#%d-C[%d]] panic for fork is valid [fp=%d]", getID(), channel, p.getCurr().getHeader().getHeight()));
        return prev.getHeader().getHeight();
    }

    private void deliverForkAnnounce() {
        while (!stopped.get()) {
            Types.ForkProof p;
            try {
                p = Types.ForkProof.parseFrom(panicRB.deliver(channel));
            } catch (Exception e) {
                logger.error(format("[#%d-C[%d]]", getID(), channel), e);
                continue;
            }
            synchronized (fp) {
                int pHeight = p.getCurr().getHeader().getHeight();
                if (!fp.containsKey(pHeight)) {
                    fpEntry fpe = new fpEntry();
                    fpe.done = false;
                    fpe.fp = p;
                    fp.put(pHeight, fpe);
                    logger.debug(format("[#%d-C[%d]] interrupts the main thread panic from [#%d]",
                            getID(), channel, p.getSender()));
                    mainThread.interrupt();
                }
            }
        }
    }
    private void announceFork(Types.Block b) {
        logger.info(format("[#%d-C[%d]] possible fork! [height=%d]",
                getID(), channel, currHeight));
        Types.ForkProof p = Types.ForkProof.
                newBuilder().
                setCurr(b).
                setPrev(bc.getBlock(bc.getHeight())).
                setSender(getID()).
                build();
        synchronized (fp) {
            int pHeight = p.getCurr().getHeader().getHeight();
            if (!fp.containsKey(pHeight)) {
                fpEntry fpe = new fpEntry();
                fpe.done = false;
                fpe.fp = p;
                fp.put(pHeight, fpe);
            }
        }
        panicRB.broadcast(p.toByteArray(), channel, getID());
        handleFork(p);
    }

    /**
     * Deliver the index_th block and blocks the calling thread
     * @param index the index of the block to return
     * @return the block in height <i>index</i>
     * @throws InterruptedException
     */
    public Types.Block deliver(int index) throws InterruptedException {
        synchronized (newBlockNotifyer) {
            while (index >= bc.getHeight() - (f + 1)) {
                newBlockNotifyer.wait();
            }
        }
        return bc.getBlock(index);
    }

    @Override
    public Types.Block nonBlockingDeliver(int index) {
        synchronized (newBlockNotifyer) {
            if (index >= bc.getHeight() - (f + 1)) {
                return null;
            }
        }
        return bc.getBlock(index);
    }


    private void handleFork(Types.ForkProof p) {
        if (validateForkProof(p) == -1) return;
        int fpoint = p.getCurr().getHeader().getHeight();
        logger.debug(format("[#%d-C[%d]] handleFork has been called", getID(), channel));
        try {
            sync(fpoint);
            synchronized (fp) {
                fp.get(fpoint).done = true;
            }
        } catch (Exception e) {
            logger.error(format("[#%d-C[%d]]", getID(), channel), e);
        }

    }

    private int disseminateChainVersion(int forkPoint) {
        Types.subChainVersion.Builder sv = Types.subChainVersion.newBuilder();
            int low = max(forkPoint - f, 1);
            int high = bc.getHeight() + 1;
            for (Types.Block b : bc.getBlocks(low, high)) {
                sv.addV(b);
            }
            sv.setSuggested(1);
            sv.setSender(getID());
            sv.setForkPoint(forkPoint);
        return syncRB.broadcast(sv.build().toByteArray(), channel, getID());
    }

    private boolean validateSubChainVersion(Types.subChainVersion v, int forkPoint) {
        int lowIndex = v.getV(0).getHeader().getHeight();
        if (lowIndex != forkPoint - f && forkPoint >= f) {
            logger.debug(format("[#%d-C[%d]] invalid sub chain version, [lowIndex=%d != forkPoint -f=%d] [fp=%d ; sender=%d]",
                    getID(),channel, lowIndex, forkPoint - f, forkPoint, v.getSender()));
            return false;
        }

        if (v.getVList().size() < f && forkPoint >= f) {
            logger.debug(format("[#%d-C[%d]] invalid sub chain version, Block list size is smaller then f [size=%d] [fp=%d]",
                    getID(), channel,v.getVList().size(), forkPoint));
            return false;
        }

        Blockchain lastSyncBC = getBC(0, lowIndex);
        for (Types.Block pb : v.getVList()) {
            Types.Block.Builder dataAsInRmf = Types.Block.newBuilder()
                    .setHeader(pb.getHeader());
            for (int i = 0 ; i < pb.getDataList().size() ; i++ ) {
                dataAsInRmf.addData(i, pb.getData(i));
            }
            if (!BlockDS.verify(pb.getHeader().getM().getSender(), pb)) {
                logger.debug(String.format("[#%d-C[%d]] invalid sub chain version, Block [height=%d] digital signature is invalid " +
                        "[fp=%d ; sender=%d]", getID(),channel, pb.getHeader().getHeight(), forkPoint, v.getSender()));
                return false;
            }

            if (!lastSyncBC.validateBlockHash(pb)) {
                logger.debug(String.format("[#%d-C[%d]] invalid sub chain version, Block hash is invalid [height=%d] [fp=%d]",
                        getID(),channel, pb.getHeader().getHeight(), forkPoint));
                return false;
            }
            if (!lastSyncBC.validateBlockCreator(pb, f)) {
                logger.debug(String.format("[#%d-C[%d]] invalid invalid sub chain version, Block creator is invalid [height=%d] [fp=%d]",
                        getID(),channel, pb.getHeader().getHeight(), forkPoint));
                return false;
            }
            lastSyncBC.addBlock(pb);
        }
        return true;
    }

    private void sync(int forkPoint) throws InvalidProtocolBufferException {
        logger.info(format("[#%d-C[%d]] start sync method with [fp=%d]", getID(),channel, forkPoint));
        disseminateChainVersion(forkPoint);
        while (!scVersions.containsKey(forkPoint) || scVersions.get(forkPoint).size() < 2*f + 1) {
            Types.subChainVersion v;
            try {
                v = Types.subChainVersion.parseFrom(syncRB.deliver(channel));
            } catch (InterruptedException e) {
                if (!stopped.get()) {
                    // might interrupted if more then one panic message received.
                    logger.debug(format("[#%d-C[%d]] sync operation has been interrupted, try again...",
                            getID(), channel));
                    continue;
                } else {
                    return;
                }
            }
            if (v == null) {
                logger.debug(format("[#%d-C[%d]] Unable to parse sub chain version [fp=%d]]", getID(),channel, forkPoint));
                continue;
            }
            if (!validateSubChainVersion(v, v.getForkPoint())) {
                logger.debug(format("[#%d-C[%d]] Sub chain version is invalid [fp=%d]]", getID(), channel,v.getForkPoint()));
                continue;
            }
            ArrayList<Types.subChainVersion> l = new ArrayList<>();
            if (!scVersions.containsKey(v.getForkPoint())) {
                scVersions.put(v.getForkPoint(), l);
            }
            scVersions.get(v.getForkPoint()).add(v);
        }
        int max = scVersions.get(forkPoint).
                stream().
                mapToInt(v -> v.getV(v.getVCount() - 1).getHeader().getHeight()).
                max().getAsInt();
        Types.subChainVersion choosen = scVersions.
                get(forkPoint).
                stream().
                filter(v -> v.getV(v.getVCount() - 1).getHeader().getHeight() == max).
                findFirst().get();
        logger.info(format("[#%d-C[%d]] adopts sub chain version [length=%d] from [#%d]", getID(),channel, choosen.getVList().size(), choosen.getSender()));
        synchronized (newBlockNotifyer) {
            bc.setBlocks(choosen.getVList(), forkPoint - 1);
            if (!bc.validateBlockHash(bc.getBlock(bc.getHeight()))) {
                logger.debug(format("[#%d-C[%d]] deletes a Block [height=%d]", getID(),channel, bc.getHeight()));
                bc.removeBlock(bc.getHeight()); // meant to handle the case in which the front is split between the to leading blocks
            }
            newBlockNotifyer.notify();
        }
        currLeader = (bc.getBlock(bc.getHeight()).getHeader().getM().getSender() + 2) % n;
        currHeight = bc.getHeight() + 1;
        cid = 0;
        cidSeries++;
        logger.debug(format("[#%d-C[%d]] post sync: [cHeight=%d] [cLeader=%d] [cidSeries=%d]"
                , getID(), channel, currHeight, currLeader, cidSeries));
    }

    @Override
    public int getBCSize() {
        return max(bc.getHeight() - (f + 2), 0);
    }
}
