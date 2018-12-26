package toy.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * This class responsible for configure Toy according to config.toml
 */
public class Config {
    static class tomlKeys {
        String SYSTEM_KEY = "system";
        String SYSTEM_N_KEY = "system.n";
        String SYSTEM_F_KEY = "system.f";
        String SYSTEM_C_KEY = "system.c";
        String SYSTEM_TESTING_KEY = "system.testing";
        String SYSTEM_TX_SIZE = "system.txSize";
        String SERVER_KEY = "server";
        String SERVER_ID_KEY = "server.id";
        String SERVER_IP_KEY = "server.ip";
        String SERVER_CRT_PATH = "server.TlsCertPath";
        String SERVER_TLS_PRIV_KEY_PATH = "server.TlsPrivKeyPath";
        String RMFCLUSTER_KEY = "cluster";
        String RMFCLUSTER_SERVER_KEY = "cluster.s";
        String SETTING_KEY = "setting";
        String SETTING_TMO_KEY = "setting.tmo";
        String SETTING_TMO_INTERVAL_KEY = "setting.tmoInterval";
        String SETTING_RMFBBCCONFIG_KEY = "setting.rmfBbcConfigPath";
        String SETTING_PAINCRBCONFIG_PATH = "setting.panicRBroadcastConfigPath";
        String SETTING_SYNCRBCONFIG_PATH = "setting.syncRBroadcastConfigPath";
        String SETTING_MAXTRANSACTIONSINBLOCK_KEY = "setting.maxTransactionInBlock";
        String SETTING_CA_ROOT_PATH = "setting.caRootPath";
        String SETTING_FAST_MODE = "setting.fastMode";
        String SERVER_PRIVKEY = "server.privateKey";
        String SERVER_PUBKEY = "server.publicKey";
    }

    private static tomlKeys tKeys;
    private static Toml conf;
    private static int s_id;
    private static Path tomlPath;

    private static org.apache.log4j.Logger logger;

    /**
     * Configure the class, including set the logger directory and resolving the path to config.toml.
     * @param path path to config.toml
     * @param id server ID
     */
    public static void setConfig(Path path, int id) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy:hh:mm:ss");
        System.setProperty("current.date.time", dateFormat.format(new Date()));
        tomlPath =  Paths.get("src", "main", "resources", "config.toml");
        if (path != null) {
            tomlPath = path;
        }
        s_id = id;
        System.setProperty("s_id", Integer.toString(s_id));
        logger = org.apache.log4j.Logger.getLogger(Config.class);
        tKeys = new tomlKeys();
        conf = readConf(tomlPath);
    }
    static Toml readConf(Path tomlPath) {
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(tomlPath);
        } catch (IOException e) {
            logger.fatal("", e);
        }
        String content = new String(encoded, Charset.defaultCharset());
        return new Toml().read(content);

    }

    /**
     * Get the cluster size.
     * @return the cluster size
     */
    public static int getN() {
        return Math.toIntExact(conf.getLong(tKeys.SYSTEM_N_KEY));
    }

    /**
     * Get the upper bound on the faulty nodes number.
     * @return the upper bound on the faulty nodes number
     */
    public static int getF() {
        return Math.toIntExact(conf.getLong(tKeys.SYSTEM_F_KEY));
    }

    /**
     * Get the time-out for wrb-deliver.
     * @return the time-out for wrb-deliver
     */
    public static int getTMO() {
        return Math.toIntExact(conf.getLong(tKeys.SETTING_TMO_KEY));
    }

    /**
     * Get the interval to add if wrb-deliver did not deliver in a single round.
     * @return the interval
     */
    public static int getTMOInterval() {
        return Math.toIntExact(conf.getLong(tKeys.SETTING_TMO_INTERVAL_KEY));
    }

    /**
     * Get the ip of the server with <i>id</i>.
     * @param id the server to get its ip address
     * @return the ip of the id_th server
     */
    public static String getAddress(int id) {
        Toml t = conf.getTables(tKeys.RMFCLUSTER_KEY).get(0);
        Toml node = t.getTable("s" + id);
        return node.getString("ip");
    }

    /**
     * Get wrb port og the server with <i>id</i>.
     * @param id the server to get its wrb port
     * @return the wrb port of the id_th server
     */
    public static int getPort( int id) {
        Toml t = conf.getTables(tKeys.RMFCLUSTER_KEY).get(0);
        Toml node = t.getTable("s" + id);
        return Math.toIntExact(node.getLong("port"));
    }

    /**
     * Get the number of channels.
     * @return the number of channels
     */
    public static int getC() {
        return Math.toIntExact(conf.getLong(tKeys.SYSTEM_C_KEY));
    }

    /**
     * Get the indication if it is testing mode.
     * @return true if in testing mode, false if not
     */
    public static boolean getTesting() {
        return conf.getBoolean(tKeys.SYSTEM_TESTING_KEY);
    }

    /**
     * Get description of the whole cluster.
     * @return list of Nodes depicts the whole cluster
     */
    public static ArrayList<Node> getCluster() {
        Toml t = conf.getTables(tKeys.RMFCLUSTER_KEY).get(0);
        ArrayList<Node> ret = new ArrayList<>();
        for (int i = 0 ; i < getN() ; i++) {
            Toml node = t.getTable("s" + i);
            ret.add(new Node(node.getString("ip"),
                    Math.toIntExact(node.getLong("port")),
                    Math.toIntExact(node.getLong("id"))));
        }
        return ret;
    }

    /**
     * Get the cluster's members public keys
     * @return Hash table of [ID:String] contains the public keys
     */
    public static HashMap<Integer, String> getClusterPubKeys() {
        Toml t = conf.getTables(tKeys.RMFCLUSTER_KEY).get(0);
        HashMap<Integer, String> ret = new HashMap<>();
        for (int i = 0 ; i < getN() ; i++) {
            Toml node = t.getTable("s" + i);
            ret.put(Math.toIntExact(node.getLong("id")), node.getString("publicKey"));
        }
        return ret;
    }

    /**
     * Get maximal transactions in block.
     * @return the maximal amount of transactions in block
     */
    public static int getMaxTransactionsInBlock() {
        return Math.toIntExact(conf.getLong(tKeys.SETTING_MAXTRANSACTIONSINBLOCK_KEY));
    }

    /**
     * Get the path to wrb config directory.
     * @return path to wrb config directory
     */
    public static String getRMFbbcConfigHome() {
        return conf.getString(tKeys.SETTING_RMFBBCCONFIG_KEY);
    }

    /**
     * Get the path to panic config directory.
     * @return path to panic config directory
     */
    public static String getPanicRBConfigHome() {
        return conf.getString(tKeys.SETTING_PAINCRBCONFIG_PATH);
    }
    /**
     * Get the path to sync config directory.
     * @return path to sync config directory
     */
    public static String getSyncRBConfigHome() {
        return conf.getString(tKeys.SETTING_SYNCRBCONFIG_PATH);
    }
    /**
     * Get the server's private key.
     * @return the server's private key
     */
    public static String getPrivateKey() {
        return conf.getString(tKeys.SERVER_PRIVKEY);
    }

    /**
     * Get the path to the ca root certificate.
     * @return path to the ca root certificate
     */
    public static String getCaRootPath() {return conf.getString(tKeys.SETTING_CA_ROOT_PATH); }

    /**
     * Get the path to the server certificate.
     * @return path to the server certificate
     */
    public static String getServerCrtPath() { return conf.getString(tKeys.SERVER_CRT_PATH); }

    /**
     * Get the path to the server's ssl private key.
     * @return path to the server's ssl private key
     */
    public static String getServerTlsPrivKeyPath() { return conf.getString(tKeys.SERVER_TLS_PRIV_KEY_PATH); }

    /**
     * Get indication of working in fast mode.
     * @return true if the server should work in fast mode, false if not
     */
    public static boolean getFastMode() { return conf.getBoolean(tKeys.SETTING_FAST_MODE); }

    /**
     * Get the maximal size of transaction (meant for testing only).
     * @return maximal size of transaction
     */
    public static int getTxSize() {
        return Math.toIntExact(conf.getLong(tKeys.SYSTEM_TX_SIZE));
    }

}
