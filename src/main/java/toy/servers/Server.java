package toy.servers;
import toy.proto.Types;
import java.util.List;

/**
 * This interface define the method a Toy based server supports.
 * To simplify work with different designs (e.g a Top server) every Toy based server should implement this interface.
 * @author Yehontan Buchnik
 */
public interface Server {
    /**
     * Initiates the underlying protocols of a Toy based server (i.e bft-SMaRt).
     */
    void start();

    /**
     * Start the server. After calling this method the server should be able to serve clients requests.
     * The reason for why  <i>start</i> ane <i>serve</i> are separated is dut to the fact that TOY great power is when
     * the system is fault-less and synchronized. By this separation we can wait for the system to get synchronized
     * before starting to server clients requests.
     */
    void serve();

    /**
     * Shutting down the server. After invoking this method the server is unable to server more requests as well as to
     * participate the algorithm.
     */
    void shutdown();

    /**
     * Add a transaction to the transaction pool.
     * @param tx the transaction to be added
     * @return An object that identifies the transaction by unique GUID
     */
    Types.txID addTransaction(Types.Transaction tx);

    /**
     * Check the state of the transaction with the given txID.
     * @param txID the ID of the transaction to be checked
     * @return An integer that indicates the state of the transaction
     */
    int isTxPresent(String txID);

    /**
     * Get the ID of the server.
     * @return ID of the server
     */
    int getID();

    /**
     * Get the current Blockchain size.
     * @return the current Blockchain size
     */
    int getBCSize();

    /**
     * Deliver the i_th block of the Blockchain. Note that this method does not block the caller.
     * @param index the index of the block to be delivered
     * @return The desired block if exists or null if it does not
     */
    Types.Block nonBlockingDeliver(int index);
}
