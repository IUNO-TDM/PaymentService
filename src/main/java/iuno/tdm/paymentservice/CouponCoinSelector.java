package iuno.tdm.paymentservice;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.util.ArrayList;
import java.util.List;

public class CouponCoinSelector implements CoinSelector {

    @Override
    public CoinSelection select(Coin coin, List<TransactionOutput> list) {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        long total = 0;
        for (TransactionOutput txOut : list) {
            if (TransactionConfidence.ConfidenceType.BUILDING == txOut.getParentTransaction().getConfidence().getConfidenceType()) {
                total += txOut.getValue().value;
                selected.add(txOut);
            }
        }
        return new CoinSelection(Coin.valueOf(total), selected);
    }
}