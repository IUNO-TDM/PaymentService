package iuno.tdm.paymentservice;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
/**
 * Created by goergch on 09.03.17.
 */
public class BitcoinInvoicePersistence {
    private ArrayList<SerializableBitcoinInvoice> invoiceDataArray = new ArrayList<>();
    private File file;
    public BitcoinInvoicePersistence(File file){
        this.file = file;
    }

    public BitcoinInvoicePersistence(String fileName){
        this.file = new File(fileName);
    }


    /***
     *
     * @param bitcoinInvoice the BitcoinInvoice to be saved additionally to the existing
     */
    public void AddBitcoinInvoice(BitcoinInvoice bitcoinInvoice){


        SerializableBitcoinInvoice bitcoinInvoiceData = new SerializableBitcoinInvoice(bitcoinInvoice);
        invoiceDataArray.add(bitcoinInvoiceData);
        try {
            FileOutputStream fileOutputStream =  new FileOutputStream(file, false);
            ObjectOutputStream o = new ObjectOutputStream(fileOutputStream);
            o.writeObject(invoiceDataArray);
            o.flush();
            o.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public HashMap<UUID, BitcoinInvoice> restoreBitcoinInvoices(
            BitcoinInvoiceCallbackInterface bitcoinInvoiceCallbackInterface,
            Wallet wallet){
        HashMap<UUID, BitcoinInvoice> hashMap = new HashMap<>();
        if (file.exists()){
            try{
                FileInputStream fileInputStream = new FileInputStream(file);
                ObjectInputStream inputStream = new ObjectInputStream(fileInputStream);
                invoiceDataArray= (ArrayList<SerializableBitcoinInvoice>) inputStream.readObject();
                for(SerializableBitcoinInvoice data: invoiceDataArray ){
                    BitcoinInvoice bcInvoice = new BitcoinInvoice(data.getInvoice(),data.getPayDirectAddress(),data.getPayTransfersAddress(),bitcoinInvoiceCallbackInterface);
                    hashMap.put(data.getInvoice().getInvoiceId(),bcInvoice);
                    if(!data.getPayingTxHash().isEmpty()){
                        Transaction payingTx = wallet.getTransaction(Sha256Hash.wrap(data.getPayingTxHash()));
                        if(payingTx != null){
                            bcInvoice.setPayingTx(payingTx);
                        }
                    }
                    if(!data.getTransferTxHash().isEmpty()){
                        Transaction transferTx = wallet.getTransaction(Sha256Hash.wrap(data.getTransferTxHash()));
                        if(transferTx != null){
                            bcInvoice.setPayingTx(transferTx);
                        }
                    }

                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }


        return hashMap;

    }

}
