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

So basically, it is very much like when dApps use user's node directly!

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


### Request result

## Configuration
TODO

## Running the code
TODO

