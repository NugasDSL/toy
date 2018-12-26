package toy.blockchain;

import toy.proto.Types;

/**
 * This class implement a basic block that holds a simple list of transactions.
 */
public class BasicBlock extends Block {

    @Override
    public boolean validateTransaction(Types.Transaction t) {
        return true;
    }
}
