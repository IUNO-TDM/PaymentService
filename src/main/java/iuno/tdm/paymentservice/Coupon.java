package iuno.tdm.paymentservice;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.util.Map;

public class Coupon {
    final ECKey ecKey;
    long value;Map<Sha256Hash, Transaction> transactions = null;

    public Coupon(ECKey ecKey) {
        this.ecKey = ecKey;
    }
}
