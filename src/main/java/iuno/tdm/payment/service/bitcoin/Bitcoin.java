package iuno.tdm.payment.service.bitcoin;
/**
 * Created by bockha on 20.02.17.
 */
public class Bitcoin {

    private static Bitcoin instance;

    private Bitcoin() {}

    public static synchronized Bitcoin getInstance () {
        if (Bitcoin.instance == null) {
            Bitcoin.instance = new Bitcoin ();
        }
        return Bitcoin.instance;
    }
}
