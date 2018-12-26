package toy.config;

/**
 * This class depicts a node in the cluster.
 */
public class Node {
    private String addr;
    private int rmfPort;
    private int id;

    /**
     * Get the node's ip address
     * @return the node's ip address
     */
    public String getAddr() {
        return addr;
    }

    /**
     * Get the node's wrb port
     * @return the node's wrb port
     */
    public int getRmfPort() {
        return rmfPort;
    }

    /**
     * Get the node's ID
     * @return the node's ID
     */
    public int getID() {
        return id;
    }

    /**
     * Constructor
     * @param addr the node ip address
     * @param rmfPort the node's wrb port
     * @param id the node's ID
     */
    public Node(String addr, int rmfPort, int id) {
        this.addr = addr;
        this.rmfPort = rmfPort;
        this.id = id;
    }

}
