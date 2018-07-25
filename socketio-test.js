console.log('Starting Websocket Client');

var invoice = {
    totalAmount: 100000000,
    referenceId: 'Brot',
    expiration: new Date(new Date().getTime() + (2 * 60 * 60 * 1000)).toISOString(),
    transfers: [{
        address: 'n2oGNcjsnzB34UYdAvipFoEyR9z4qnLsd5',
        coin: 99900000
    }]
};

function hideAll() {
    document.getElementById("checkmark").style.display="none";
    document.getElementById("waitmark").style.display="none";
    document.getElementById("failmark").style.display="none";
    document.getElementById("qrcode").style.display="none";
}

function showQRCode(qrCode) {
    document.getElementById("qrcode").innerHTML = '';
    new QRCode(document.getElementById("qrcode"), qrCode);
    hideAll();
    document.getElementById("qrcode").style.display="block";
}

function showCheckMark() {
    hideAll();
    document.getElementById("checkmark").style.display="block";
}

function showWaitMark() {
    hideAll();
    document.getElementById("waitmark").style.display="block";
}

function showFailMark() {
    hideAll();
    document.getElementById("failmark").style.display="block";
}

var socket = io('http://localhost:8080/invoices', {
    transports: ['websocket']
});
socket.on('connect', function(){
    console.log('Connected');
    document.getElementById('log').innerHTML = 'connected';

    var http = new XMLHttpRequest();
    var url = "http://localhost:8080/v1/invoices";
    http.open("POST", url, true);

    //Send the proper header information along with the request
    http.setRequestHeader("Content-type", "application/json");

    http.onreadystatechange = function() {//Call a function when the state changes.
        if(http.readyState === 4 && http.status === 201) {
            console.log('Invoice Created:');
            console.log(http.responseText);
            const jsonData = JSON.parse(http.responseText);
            socket.emit('room', jsonData['invoiceId']);

            document.getElementById("invoiceId").innerHTML = jsonData.invoiceId;

            var request = new XMLHttpRequest();

            request.open("GET","http://localhost:8080/v1/invoices/" + jsonData['invoiceId'] + '/bip21');
            request.addEventListener('load', function(event) {
                if (request.status >= 200 && request.status < 300) {
                    console.log(request.responseText);
                    showQRCode(request.responseText);
                    document.getElementById("bip21").innerHTML=request.responseText;
                } else {
                    console.warn(request.statusText, request.responseText);
                }
            });
            request.send();
        }
    };
    http.send(JSON.stringify(invoice));
    document.getElementById("referenceId").innerHTML = invoice.referenceId;

});

function stateChange(txPrefix, data){
    document.getElementById(txPrefix+'state').innerHTML = data.state;
    var barElement = document.getElementById(txPrefix+'bar');

    var depthInBlocks = data.depthInBlocks;

    if ('pending' == data.state) {
        var seenByPeers = (2147483648 + depthInBlocks); // TODO use "seen by peers"
        barElement.style.width = seenByPeers + '%';
        document.getElementById(txPrefix+'confidence').innerHTML = "Seen by peers:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = seenByPeers;

    } else if ('building' == data.state) {
        document.getElementById(txPrefix+'confidence').innerHTML = "Depth in blocks:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = data.depthInBlocks;
        barElement.classList.add('bg-success');
        barElement.classList.remove('bg-warning');
        barElement.style.width = depthInBlocks/6*100 + '%';

    } else {
        document.getElementById(txPrefix+'confidence').innerHTML = "Confidence:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = "n/a";
    }
}

socket.on('StateChange', function(data){
    console.log('StateChange: ' + data);
    const jd = JSON.parse(data);
    showCheckMark();
    document.getElementById('log').innerHTML = 'StateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;

    stateChange('p-', jd);

    document.getElementById('progressbar').style.width = jd.depthInBlocks/6*100 + '%';
});

socket.on('TransferStateChange', function(data){
    console.log('TransferStateChange: ' + data);
    const jd = JSON.parse(data);
    document.getElementById('log').innerHTML = 'TransferStateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
    stateChange('t-', jd);
});

socket.on('PayingTransactionsChange', function(data){
    console.log('PayingTransactionsChange: ' + data);
    const jd = JSON.parse(data);
    document.getElementById('log').innerHTML = 'PayingTransactionChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
    document.getElementById('p-txid').innerHTML = jd.transactions[0].transaction;
});

socket.on('TransferTransactionsChange', function(data){
    console.log('TransferTransactionsChange: ' + data);
    const jd = JSON.parse(data);
    document.getElementById('log').innerHTML = 'TransferTransactionChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
    document.getElementById('t-txid').innerHTML = jd.transactions[0].transaction;
});

socket.on('disconnect', function(){
    showFailMark();
    console.log('Disconnected')
});
