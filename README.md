# Toy - A Total ordering Optimistic sYstem
## Introduction
### Abstract
Toy is a total ordering optimistic system targeting permissioned Blockchains. It assumes an optimal conditions and so amortized the 
the cost of handling failures among all rounds, creating a robust, scalable and highly efficient Blockchain platform.

### System Model
Toy assumes a Byzantine partial synchronous environment in which the number of faulty nodes, _f_ is less then third of the nodes.
_Partial synchronous_ means that after an unknown time _t_ there is an unknown upper bound _k_ on the messages transfer delays.

### Related Work
Toy currently uses [bft-SMaRt](https://github.com/bft-smart/library) as an underlying platform to tolerate the non-optimistic scenarios.  
### Papers
This work is an implementation of the TOY algorithm described in TBA

## Getting Started
### Installation (for Ubuntu)
1. Install [Maven](https://maven.apache.org/):
    * `sudo apt install maven` 
1. Download the project:
    * `git pull https://github.com/NugasDSL/toy.git`.
    * Or via this [link](https://github.com/NugasDSL/toy/archive/master.zip).
1. Go to the project directory and run:
    * `mvn package`
1. `lib/toy-core-1.0.jar` will be created under the project directory.

### Configurations
An example configuration can be found under `conf/`
#### bft-SMaRt Configuration
Toy uses bft-SMaRt as an underlying platform in three different modules (_bbc_, _panic_ and _sync_). Hence, Toy configuration should include
three different configuration directories - each one for each module. `conf/bbc`, `conf/panic` and `conf/sync` are samples for
such configurations with a single node.

Deeper explanation of bft-SMaRt configuration can be found [here](https://github.com/bft-smart/library/wiki/BFT-SMaRt-Configuration)
#### Toy Configuration
Toy configuration is consist of a single `config.toml` file that describes Toy's settings as well as the paths to bft-SMaRt's configurations.

More about Toy's configuration can be found in the [WiKi](https://github.com/NugasDSL/toy/wiki/Configuration).

#### Generating Key Pair
Toy requires messages to be signed and use an ECDSA with the `secp256k1` curve to sign them. You must specify in `config.toml`
for each server its public key. 

To generate a key pair run:

```
./bin/keysGenerator.sh $ID
```
where `$ID` is the server id. The keys will generated under `generatedKeys/$ID/priv.key` and `generatedKeys/$ID/priv.key`.
You should copy the keys to `config.toml`.
### Example
We now expline the example provaided in `src/main/java/toy/examples`. The example establishes a simple client/server Blockchain application 
using Toy library.
#### Server
The server is implemented in `ServerApp.java` and uses gRPC for client\server communication. We already built an IDL in 
`src/main/java/protos` but you may implement your own IDL. 
1. In the constructor we initialize the ToyServer as well as some local fields.
    ```
    public ServerApp(int id, int port, Path config) {
        this.port = port;
        Config.setConfig(config, id);
        logger = org.apache.log4j.Logger.getLogger(ServerApp.class);
        tServer = new CTServer(Config.getAddress(id), Config.getPort(id), id, 0, Config.getF()
            , Config.getTMO(), Config.getTMOInterval(), Config.getMaxTransactionsInBlock()
            , Config.getFastMode(), Config.getCluster(), Config.getRMFbbcConfigHome()
            , Config.getPanicRBConfigHome(), Config.getSyncRBConfigHome(), Config.getServerCrtPath()
            , Config.getServerTlsPrivKeyPath(), Config.getCaRootPath());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    ```   
    * The Toy server is initialized using `conf/config.toml`.
    * When implementing a Toy server the third argument is always 0.
    * If the server is shutdown, the `shutdown` method will be called.
1. After initializer the server should serve clients requests. This is done in the `serve` method:
    ```
    private void serve() {
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
    ```
    * Toy server starts the bft-SMaRt based modules in the `start` method. This operation may take a while.
    * The `serve` method of ToyServer starts the main thread and initiates an infinite loop in which, 
        it continuously creates new blocks and disseminates them to the other servers. The main thread is also responsible 
        for delivering new blocks from the network. 
    * reqReceiver is a gRPC server that is responsible to handle the client's requests.
1. To shutdown the server we need to shutdown both, the ToyServer and the gRPC server.
    ```
    void shutdown() {
        reqReciever.shutdown();
        tServer.shutdown();
    }
    ```
1. As our server uses gRPC it also has to implement gRPC methods:
    ```
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
    ```
    * Note that `getTransaction` is not very efficient as for every request it goes over the whole Blockchain.
        Of course, different and more efficient design choices but, in this example,
        due to its simplicity we preferred the above.
1. Finally, we implement the main method. It expect 2 ot 3 parameters: 
    1. The server ID. Each server should have an ID that is corresponding to the one that specified in `config.toml`.
    1. The port to which the client should connect.
    1. Optionally, a path to `config.toml`. If this parameter is missing, ToyServer expects to find it at 
        `src\main\resources\config.toml`
    ```
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
    ```  
    * To stop the server, hit `Ctrl+c` and kill the process.
#### Client
The client implements a simple gRPC client. Note that:
* The client uses blocking stub (although non of the server RPCs is blocking).
* A transaction may denied due to lack of space in the server transaction pool.
* The `add` action only adds a transaction to the transaction pool, and it may took a while 
    to add it to the Blockchain.
* Hence, even if `get` failed, it might be either because the transaction was never submitted or because it has not 
added to the Blockchain yet.