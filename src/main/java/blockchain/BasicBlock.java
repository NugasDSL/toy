package blockchain;


import proto.Types;

public class BasicBlock extends Block {

    @Override
    public boolean validateTransaction(Types.Transaction t) {
        return true;
    }
}
