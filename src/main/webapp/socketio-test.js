console.log('Starting Websocket Client');

var invoice = {
    totalAmount: 200000,
    referenceId: 'Brot',
    expiration: new Date(new Date().getTime() + (7 * 24 * 60 * 60 * 1000)).toISOString(),
    transfers: [{
        address: pubkeys[Math.floor(Math.random()*pubkeys.length)] || 'n2oGNcjsnzB34UYdAvipFoEyR9z4qnLsd5',
        coin: 100000
    }]
};

// http://jsfiddle.net/unLSJ/1309/
if (!library)
   var library = {};

library.json = {
   replacer: function(match, pIndent, pKey, pVal, pEnd) {
      var key = '<span class=json-key>';
      var val = '<span class=json-value>';
      var str = '<span class=json-string>';
      var r = pIndent || '';
      if (pKey)
         r = r + key + pKey.replace(/[": ]/g, '') + '</span>: ';
      if (pVal)
         r = r + (pVal[0] == '"' ? str : val) + pVal + '</span>';
      return r + (pEnd || '');
      },
   prettyPrint: function(obj) {
      var jsonLine = /^( *)("[\w]+": )?("[^"]*"|[\w.+-]*)?([,[{])?$/mg;
      return JSON.stringify(obj, null, 3)
         .replace(/&/g, '&amp;').replace(/\\"/g, '&quot;')
         .replace(/</g, '&lt;').replace(/>/g, '&gt;')
         .replace(jsonLine, library.json.replacer);
      }
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

var socket = io('/invoices', {
    transports: ['websocket']
});

socket.on('connect', function(){
    console.log('Connected');
    document.getElementById('log').innerHTML = 'connected';
    resetPage();

    var http = new XMLHttpRequest();
    var url = "/v1/invoices";
    http.open("POST", url, true);

    //Send the proper header information along with the request
    http.setRequestHeader("Content-type", "application/json");

    http.onreadystatechange = function() {//Call a function when the state changes.
        if (http.readyState === 4 && http.status === 201) {
            console.log('Invoice Created:');
            console.log(http.responseText);
            const jsonData = JSON.parse(http.responseText);
            socket.emit('room', jsonData['invoiceId']);

            $('#invoice').html(library.json.prettyPrint(jsonData));
            document.getElementById("invoiceId").innerHTML = jsonData.invoiceId;

            var request = new XMLHttpRequest();
            request.open("GET","/v1/invoices/" + jsonData['invoiceId'] + '/bip21');
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
        } else if (http.readyState === 4 && http.status === 503) {
            setTimeout( function() {
                http.open("POST", url, true);
                http.setRequestHeader("Content-type", "application/json");
                http.send(JSON.stringify(invoice));
            }, 2000);
        } else {
            showFailMark();
        }
    };
    http.send(JSON.stringify(invoice));
    document.getElementById("referenceId").innerHTML = invoice.referenceId;

});

function stateChange(txPrefix, data){
    document.getElementById(txPrefix+'state').innerHTML = data.state;
    var barElement = document.getElementById(txPrefix+'bar');

    var depthInBlocks = data.depthInBlocks;
    var seenByPeers = data.seenByPeers;
    $('#' + txPrefix + 'txid').html('<a href="https://www.blocktrail.com/tBTC/tx/' + data.txid + '">' + data.txid + '</a>');

    if ('pending' == data.state) {
        if ("p-" == txPrefix) showWaitMark();
        document.getElementById(txPrefix+'confidence').innerHTML = "Seen by peers:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = seenByPeers;
        barElement.classList.add('bg-warning');
        barElement.classList.remove('bg-success');
        barElement.classList.remove('bg-danger');
        barElement.style.width = seenByPeers + '%';

    } else if ('building' == data.state) {
        if ("p-" == txPrefix) showCheckMark();
        document.getElementById(txPrefix+'confidence').innerHTML = "Depth in blocks:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = depthInBlocks;
        barElement.classList.add('bg-success');
        barElement.classList.remove('bg-warning');
        barElement.classList.remove('bg-danger');
        barElement.style.width = depthInBlocks/6*100 + '%';

    } else {
        if ("p-" == txPrefix) showFailMark();
        document.getElementById(txPrefix+'confidence').innerHTML = "Confidence:";
        document.getElementById(txPrefix+'confidenceV').innerHTML = "n/a";
        barElement.classList.add('bg-danger');
        barElement.classList.remove('bg-warning');
        barElement.classList.remove('bg-success');
    }

}

function resetPage() {
    showWaitMark();

    $('#progressbar').width('0%');
    $('#invoiceId').html('n/a');
    $('#referenceId').html('n/a');
    $('#bip21').html('n/a');
    $('#p-txid').html('n/a');
    $('#p-state').html('n/a');
    $('#p-bar').width('0%');
    $('#p-confidenceV').html('n/a');
    $('#t-confidenceV').html('n/a');
    $('#t-txid').html('n/a');
    $('#t-state').html('n/a');
    $('#t-bar').width('0%');
    $('#invoice').html('');
    $('#PaymentStateChange').html('');
    $('#TransferStateChange').html('');
    $('#PayingTransactionsChange').html('');
    $('#TransferTransactionsChange').html('');
    $('#log').html('...');
}

socket.on('PaymentStateChange', function(data){
    console.log('PaymentStateChange: ' + data);
    const jd = JSON.parse(data);
    document.getElementById('log').innerHTML = 'PaymentStateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
    $('#PaymentStateChange').html(library.json.prettyPrint(jd));
    stateChange('p-', jd);

    $('#progressbar').width(jd.depthInBlocks/6*100 + '%');
});

socket.on('TransferStateChange', function(data){
    console.log('TransferStateChange: ' + data);
    const jd = JSON.parse(data);
    document.getElementById('log').innerHTML = 'TransferStateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
    $('#TransferStateChange').html(library.json.prettyPrint(jd));
    stateChange('t-', jd);
});

socket.on('disconnect', function(){
    showFailMark();
    console.log('Disconnected')
});
