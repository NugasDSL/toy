package servers;

import proto.Types;

import java.util.List;

public interface Server {
    void start();
    void serve();
    void shutdown();
    Types.txID addTransaction(Types.Transaction tx);
    int isTxPresent(String txID);
    int getID();
    int getBCSize();
    Types.Block nonBlockingDeliver(int index);
    void setByzSetting(boolean fullByz, List<List<Integer>> groups);
    void setAsyncParam(int time);
    Statistics getStatistics();
}
