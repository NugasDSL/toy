package toy.examples;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import toy.proto.Types;
import toy.proto.blockchainServiceGrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class ClientApp {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ClientApp.class);
    private blockchainServiceGrpc.blockchainServiceBlockingStub stub;
    private ManagedChannel channel;
    private int clientID;

    public ClientApp(int clientID, String addr, int port) {
        this.clientID = clientID;
        channel = ManagedChannelBuilder.forAddress(addr, port).usePlaintext().build();
        stub = blockchainServiceGrpc.newBlockingStub(channel);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public int getID() {
        return clientID;
    }

    public Types.accepted addTx(byte[] data) {
        try {
            Types.Transaction t = Types.Transaction.newBuilder()
                    .setClientID(clientID)
                    .setData(ByteString.copyFrom(data))
                    .setClientTs(System.currentTimeMillis())
                    .build();
            return stub.addTransaction(t);
        } catch (Exception e) {
            logger.error("", e);
            return null;
        }
    }

    public Types.approved getTx(Types.read r) {
        return stub.getTransaction(r);
    }

    public void shutdown() {
        channel.shutdown();
    }

    static String[] getArgs(String cmd) {
        List<String> matchList = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");
        Matcher regexMatcher = regex.matcher(cmd);
        while (regexMatcher.find()) {
            matchList.add(regexMatcher.group());
        }
        return matchList.toArray(new String[0]);
    }

    static private void parse(String argv[], ClientApp c) {
        if (argv[0].equalsIgnoreCase("add")) {
            Types.accepted acc = c.addTx(argv[1].getBytes());
            if (acc.getAccepted()) {
                System.out.println(format("Transaction [%s] was successfully submitted and will be approved soon",
                        acc.getTxID()));
            } else {
                System.out.println("Unsuccessfully submission. Resubmit the transaction");
            }
            return;
        }
        if (argv[0].equalsIgnoreCase("get")) {
            Types.approved appr = c.getTx(Types.read.newBuilder()
                    .setTxID(Types.txID.newBuilder()
                            .setTxID(argv[1]).build())
                    .build());
            if (appr.getTx().equals(Types.Transaction.getDefaultInstance())) {
                System.out.println("Unknown transaction. It might not approved yet");
            } else {
                System.out.println(format("transaction [%s] approved",
                        new String(appr.getTx().getData().toByteArray())));
            }
            return;
        }

    }

    public static void main(String argv[]) {
        int clientID = Integer.parseInt(argv[0]);
        String addr = argv[1];
        int port = Integer.parseInt(argv[1]);
        ClientApp c = new ClientApp(clientID, addr, port);
        Scanner scan = new Scanner(System.in).useDelimiter("\\n");
        while (true) {
            System.out.print("Client>> ");
            if (!scan.hasNext()) {
                break;
            }
            parse(getArgs(scan.next()), c);
        }
    }
}
