# Transaction Assembler Service for [ERGO](https://ergoplatform.org/en/)
A service to be used for bypassing node requirement of dApps on Ergo.
See [this](https://www.ergoforum.org/t/tx-assembler-service-bypassing-node-requirement-for-dapps/443)
for more technical details.

## WHY?
dApp developers need some kind of bridge to access user's assets in a secure manner.
For example, the [Ergo Auction House](http://ergoauctions.org) needs to use user's funds 
to place bid on user's desired auction. That kind of secure access is possible through some sort of wallet APIs;
For example, Yoroi wallet
has plans to add support for such [bridge between the wallet and dApps](https://cardanoupdates.com/docs/98524716-9e4c-4aeb-b462-08ec701b7f6c). However, that may take some time, also, from the
decentralization perspective, it is good to have alternative options!

By using this service, it is possible to generate any arbitrary transaction on user's behalf while letting user to use her favorite wallet!

## Does it make dApps slower?
Despite the fact that the service is an intermediate step which requires users to send assets to
a p2s address, it is very fast since it does not require user's transaction(s) to be mined by taking advantage of chained transactions.

So basically, it is very much like when dApps use user's node directly if she had one!

## How to use?
### Register a request
dApps can register some request to the service by posting to `/follow` with the necessary json fields explained bellow:
* `address`: This is the address that the service follows and basically is where the user sends her assets to.
This contract behind this address must be such that allows the service to do two things;
first, using assets to assemble the requsted transaction, second, return the assets to the user in case of any failures.
A well-designed contract and hense `address` must be such that prevent the service from stealing the assets.
For example in case of the Auction House and placing bids, the following is the contract for the `address` field of the registered request:
    ```scala
    {
      val userAddress = PK("$userAddress")
      val bidAmount = $bidAmountL
      val endTime = $endTime
      val placeBid = {
        HEIGHT < endTime && INPUTS(INPUTS.size - 1).id == fromBase64("$auctionId") &&
          OUTPUTS(0).R8[Coll[Byte]].get == userAddress.propBytes && OUTPUTS(0).value == bidAmount
      }
      val returnFunds = {
        val total = INPUTS.fold(0L, {(x:Long, b:Box) => x + b.value}) - 4000000
        OUTPUTS(0).value >= total && OUTPUTS(0).propositionBytes == userAddress.propBytes
      }
      sigmaProp(placeBid || returnFunds)
    }
    ```
    As explained, The two part of the contract allows the service to place specifically the bid that the user has requested
    or return the assets to the user! 
    
    Another important aspect of this `address` is that it should be unique per interaction;
    so for example, the above contract is unique per user (`userAddress` part) and
    auction box (`auctionId` part which is the current box id of the auction)
    hence different addresses for different auctions or users. This is essentially needed for the
    service to be able to distinguish between different assets!
* `returnTo`: This field must contain the user's address. Assets will be returned to this address in case of any failures.
* `startWhen`: This field hints the assembler service about when to start assembling the transaction. As an example:
    ```json
        { 
           "erg":1000000000,
           "d01bdce3959ff6b675f7ab40ec2479ca5ce1edf3ae6314631f09c0f9807752aa":71
        }
    ```
  the above data will hint the assembler to wait for the user to send 1 ERG and 71 of d01bd... token to the registered `address`.
  Currently, the assembler expects this requirements to be exactly satisfied.
  So if the user sends more than she is supposed to, all her assets will be returned to her.
  On the other hand, if the current deposited assets are less than the requirements, the service will wait for more deposits so the requirements become satisfied.
  
  As a future work, would be nice to have some more kind of requirements. For example `>=` and `<=` requirements in addition to exact equality.
  
* `txSpec`: This field is essentially the transaction generation request, very much like the node's `transaction/generate` endpoint
with some changes.
    - First of all instead of `inputsRaw` and `dataInputsRaw`, `inputs` and `dataInputs` must be provided which means
    dApss don't need to have access to a node to get raw serialization of boxes and they can provide the box id instead.
    - Second, this `txSpec` obviously should contain user's assets which will be sent to `address`. `inputs` field should contain
    `$userIns` wherever user's inputs should be placed. As an example, if user's input(s) must be the last input of the transaction,
    then the following would be what must be specified for `inputs` field of the `txSpec`:
        ```
      "txSpec": {
          # other fields
            "inputs": [..., "$userIns"] // ... can be other inputs (box ids)
      }
        ```
    The below is a complete example of `txSpec` field for placing a 0.2 ERG bid in the Ergo Auction House. Note that the user's inputs will be the first inputs of the transaction by specifying `$userIns` first; The second input is the current auction box.
    ```
     {
       // other fields
       "txSpec":{
         "requests":[
           {
             "value":200000000,
             "address":"B63bstXvgAmLqicW4SphqE6AkixAyU7xPpN8g5QwzKsCLvUmNSjZTkrWif7dDzHTBwtCFSvE9qsouL9LnkfJKKmhL4Mw4sA2gNKzgN4wuVcXqvPmPRq5yHky2LyADXmF6Py8vS8KGm7cyHB4U2voCMFqEbgPFoXLRYaa2m3rmCJiNmfdmbqYtMdzH9xRYEVZheDmuBJJnAEFqn6z4h5pitDKZeJJRMnWoJ8YJAooXw5bhxviqcB3HAWmLuJovSpcQ2btWK9h6QhkfyjxbRCmLkKhkbqnF49PkvaqFhqSar68uXVRf6s9FDC4WVND8KmVVh2DhyRrNjNx8u25hwas3q8S7MQfY2jmMJ7pMmgQ8NXZL9FjEeH7WUJbWnwLvm8rKf3yAACP6WD9s84R7Nvr2ijK21PhXkRFgGAPzjWfa4VHVXqcKYYJrA79eK5fVnM8QrLEEd3Rn9Km1LjjT7EEgZhTyym5QyFVzHrx6XipunbwBw2BAXj7HE9wCi",
             "assets":[
               {
                 "tokenId":"d01bdce3959ff6b675f7ab40ec2479ca5ce1edf3ae6314631f09c0f9807752aa",
                 "amount":2
               }
             ],
             "registers":{
               "R4":"0e240008cd03b04048a9708f0a2b109d75513a2483e3b9ede622efb3cc03bdddf93bccd93ac5",
               "R5":"04fe952c",
               "R6":"058084af5f",
               "R7":"0e052e2e2e2e2e",
               "R8":"0e240008cd02d84d2e1ca735ce23f224bd43cd43ed67053356713a8a6920965b3bde933648dc",
               "R9":"0e1a3130303030303030302c3130303030303030302c333631383330"
             }
           },
           {
             "value":100000000,
             "address":"9hoRnjysKfkwZSCgSFNzSXMohwYn8DqruuYxD6vT7Ubw55qnwiZ"
           }
         ],
         "fee":2000000,
         "inputs":[
           "$userIns",
           "156e63c67daae6724bfb6cd75b2692541342c1591d6f48ec5553ca58d3c5fb5a"
         ],
         "dataInputs":[
           "ed228293da075fc6364725e0f672930147a8a4d953e349a2452219fee23cd181"
         ]
       }
     }

    ```
  Currently, `$userIns` is only supported in the `inputs` field; As a future work,
  it should be supported in the `request` field too, for example for issuing tokens!
      


### Result
After registering a request you will get a result like the following:
```json
{
    "id": "b486666b-84ad-4780-a277-cb9677f9ca6f"
}
```
Then you can use the `id` to follow the request's result by calling `/result/:id`.
The below is the result if the transaction is assembled successfully:
```json

{
  "id": ":id",
  "tx": {"id": "..."},
  "detail": "success"
}
```
Other important values for `detail` field are: _pending_, _returning_ and _timeout_.
In casess other than _success_ and _returning_, the `tx` field will be null.

## How does it work?
The assember service uses the scan functionality of the node to follow registered addresses.
It also makes use of chained transaction and identifies the user's assets very soon after she
has transfered them.

There are several phases for each registered request:
- Assembling (_pending_ as `detail` field): in this phase the assembler service will follow the registered address
periodically in short intervals (every 10 seconds by default) - it will remain in this phsse
for some time (4 minutes by default) and if it couldn't identify the necessary assets by the end
of this time, then the request will be marked as _timeout_. As soon as it identifies the necessary assets
it will try to assemble the requested transaction and goes to the next phase with _success_ status! However if the 
assembling process fails (for example some inputs are already spent) or the request is _timeout_
and there are some deposited assets, then the service will return the assets to the user and the request
will be marked as _returning_.

- Mining (_success_ or _returning_ as `detail` field): if in the previous phase, the requested transaction or the returnung transaction was generated
then in this phase the assembler service waits to make sure the transaction is mined and gets some
confirmation number (configurable in the config file). If for any reason the tx gets rejected in this phase (the transaction is not valid anymore according to the current context)
then the request will again go to the first phase.

- Archiving: if the request is marked as `timeout` or in any other situations, it will still be archived
for some time (1 hour by default) for it to be queryable - after this time has passed, all the request's related information wil be removed.

The above phasing mechanism enables the service to be fast and also not become slower over time since it gets rid of requests - thus being kind of state less.

## Configuration
Every configuration parameter is explained below:
- _node.url_: node url which the assembler service uses to follow addresses.
- _node.api_key_: api key of the node.
- _followRequestFor_: follows each request for this amount of time for the first phase in seconds.
- _followRequestInterval_: the interval in which the service checks each request in seconds.
- _followTxFor_: the amount of time the assembler keeps the request in the _mining_ phase.
- _followTxForConf_: if the transaction gets at least this number of confirmations, then will be considered as mined and the request will be archived.
- _followTxInterval_: the interval in which the service will check the mining status of transactions.
- _keepSummaryFor_: will keep the request as archived for this amount of time in seconds.
- _returnTxFee_: fee that the service uses for returning transactions.

## Running the code
Download the jar file or create one yourself using:
```bash
sbt assembly
```
Run the jar file using:
```bash
java -jar -Dconfig.file=application.conf -Dhttp.port=8080 ergo-assembler-0.1.jar
```

## Future works
The following should be done in the future in order for the service to be more "general" and also easier to setup:
- Dockerizing the service: will allow the service to be scalable.
- More "starting" criteria types: currently the assembler supports only exact matching for user's assets
in order to know when to start assembling the requested transaction, however some dApss may need _less_, _greater_, etc.
- More support for `$userIns`: currently `$userIns` which represents user's assets in the registered address
can be used only in the `inputs` field of the `txSpec`. However different parameters of it should be usable in other places too.
For example `$userIns.id` will be useful for issuing new tokens. Basically, `$userIns` is the
only parameter unknown to the dApp developers and different aspects of it should be available to be
used in the `txSpec`.

## Docker quick start
TODO
