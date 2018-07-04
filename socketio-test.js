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
                            document.getElementById("qrcode").innerHTML = '';
                            new QRCode(document.getElementById("qrcode"), request.responseText);
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

        socket.on('StateChange', function(data){
            console.log('StateChange: ' + data);
            const jd = JSON.parse(data);
            document.getElementById('qrcode').innerHTML = jd.state;
            document.getElementById('log').innerHTML = 'StateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
            document.getElementById('p.depthInBlocks').innerHTML = jd.depthInBlocks;
            document.getElementById('p.state').innerHTML = jd.state;
        });

        socket.on('TransferStateChange', function(data){
            console.log('TransferStateChange: ' + data);
            const jd = JSON.parse(data);
            document.getElementById('log').innerHTML = 'TransferStateChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
            document.getElementById('t.depthInBlocks').innerHTML = jd.depthInBlocks;
            document.getElementById('t.state').innerHTML = jd.state;
        });

        socket.on('PayingTransactionsChange', function(data){
            console.log('PayingTransactionsChange: ' + data);
            const jd = JSON.parse(data);
            document.getElementById('log').innerHTML = 'PayingTransactionChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
            document.getElementById('p.txid').innerHTML = jd.transactions[0].transaction;
        });

        socket.on('TransferTransactionsChange', function(data){
            console.log('TransferTransactionsChange: ' + data);
            const jd = JSON.parse(data);
            document.getElementById('log').innerHTML = 'TransferTransactionChange: ' + data + '<br>' + document.getElementById('log').innerHTML;
            document.getElementById('t.txid').innerHTML = jd.transactions[0].transaction;
        });

        socket.on('disconnect', function(){
            document.getElementById("qrcode").innerHTML = 'disconnected'
            console.log('Disconnected')
        });
