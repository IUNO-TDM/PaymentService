package iuno.tdm.paymentservice;

import io.swagger.model.AddressValuePair;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

public class TransferPair {
    final Address address;
    final Coin targetValue;

    public TransferPair(Address a, Coin target) {
        address = a;
        targetValue = target;
    }

    AddressValuePair getAddressValuePair() {
        return new AddressValuePair().address(address.toBase58()).coin(targetValue.getValue());
    }
}
