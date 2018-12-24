package toy.examples;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import toy.config.Config;
import toy.proto.Types;
import toy.proto.blockchainServiceGrpc;
import toy.servers.CTServer;
import toy.servers.ToyServer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerApp extends blockchainServiceGrpc.blockchainServiceImplBase {
    private static org.apache.log4j.Logger logger;
    private int port;
    private Server reqReciever;
    private ToyServer tServer;

    public ServerApp(int id, int port, Path config) {
        this.port = port;
        Config.setConfig(config, id);
        logger = org.apache.log4j.Logger.getLogger(ServerApp.class);
        tServer = new CTServer(Config.getAddress(id), Config.getPort(id), id, 0, Config.getF()
                , Config.getTMO(), Config.getTMOInterval(), Config.getMaxTransactionsInBlock(),  Config.getFastMode()
                , Config.getCluster(), Config.getRMFbbcConfigHome(), Config.getPanicRBConfigHome()
                , Config.getSyncRBConfigHome(), Config.getServerCrtPath(), Config.getServerTlsPrivKeyPath()
                , Config.getCaRootPath());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void serve() {
        tServer.start();
        tServer.serve();
        try {
            reqReciever =  NettyServerBuilder
                    .forPort(port)
                    .addService(this)
                    .build()
                    .start();
        } catch (IOException e) {
            logger.error(e);
        }
        while (true);
    }

    public void shutdown() {
        reqReciever.shutdown();
        tServer.shutdown();
    }

    @Override
    public void addTransaction(Types.Transaction request, StreamObserver<Types.accepted> responseObserver) {
        boolean ac = true;
        Types.txID id = tServer.addTransaction(request);
        if (id == null) ac = false;
        responseObserver.onNext(Types.accepted.newBuilder().setTxID(id).setAccepted(ac).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTransaction(Types.read request, StreamObserver<Types.approved> responseObserver) {
        Types.Transaction res = Types.Transaction.getDefaultInstance();
        for (int i = 0 ; i < tServer.getBCSize() ; i++) {
            for (Types.Transaction t : tServer.nonBlockingDeliver(i).getDataList()) {
                if (request.getTxID().equals(t.getId())) {
                    res = t;
                    break;
                }
            }
        }
        responseObserver.onNext(Types.approved.newBuilder()
            .setTx(res)
            .build());
        responseObserver.onCompleted();
    }

    public static void main(String argv[]) {
        int serverID = Integer.parseInt(argv[0]);
        int port = Integer.parseInt(argv[1]);
        Path configPath = null;
        if (argv.length == 3) {
            configPath = Paths.get(argv[2]);
        }
        ServerApp s = new ServerApp(serverID, port, configPath);
        s.serve();
    }
}
