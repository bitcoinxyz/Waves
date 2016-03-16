package scorex.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import com.wordnik.swagger.annotations._
import play.api.libs.json.{JsArray, Json}
import scorex.transaction.{BlockChain, History}
import scorex.wallet.Wallet
import spray.routing.Route


@Api(value = "/blocks", description = "Info about blockchain & individual blocks within it")
case class BlocksApiRoute(history: History, wallet: Wallet)(implicit val context: ActorRefFactory)
  extends ApiRoute with CommonTransactionApiFunctions {

  override lazy val route =
    pathPrefix("blocks") {
      signature ~ first ~ last ~ at ~ seq ~ height ~ heightEncoded ~ child ~ address ~ delay
    }

  @Path("/address/{address}")
  @ApiOperation(value = "Address", notes = "Get list of blocks generated by specified address", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "address", value = "Wallet address ", required = true, dataType = "String", paramType = "path")
  ))
  def address: Route = {
    path("address" / Segment) { case address =>
      jsonRoute {
        withPrivateKeyAccount(wallet, address) { account =>
          Json.arr(history.generatedBy(account).map(_.json))
        }.toString()
      }
    }
  }

  @Path("/child/{signature}")
  @ApiOperation(value = "Child", notes = "Get children of specified block", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "String", paramType = "path")
  ))
  def child: Route = {
    path("child" / Segment) { case encodedSignature =>
      jsonRoute {
        withBlock(history, encodedSignature) { block =>
          history match {
            case blockchain: BlockChain =>
              blockchain.children(block).headOption.map(_.json).getOrElse(
                Json.obj("status" -> "error", "details" -> "No child blocks"))
            case _ =>
              Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain")
          }
        }.toString()
      }
    }
  }

  @Path("/delay/{signature}/{blockNum}")
  @ApiOperation(value = "Average delay", notes = "Average delay in milliseconds between last $blockNum blocks starting from $height", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "String", paramType = "path"),
    new ApiImplicitParam(name = "blockNum", value = "Number of blocks to count delay", required = true, dataType = "String", paramType = "path")
  ))
  def delay: Route = {
    path("delay" / Segment / IntNumber) { case (encodedSignature, count) =>
      jsonRoute {
        withBlock(history, encodedSignature) { block =>
          history.averageDelay(block, count).map(d => Json.obj("delay" -> d))
            .getOrElse(Json.obj("status" -> "error", "details" -> "Internal error"))
        }.toString
      }
    }
  }

  @Path("/height/{signature}")
  @ApiOperation(value = "Height", notes = "Get height of a block by its Base58-encoded signature", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "String", paramType = "path")
  ))
  def heightEncoded: Route = {
    path("height" / Segment) { case encodedSignature =>
      jsonRoute {
        withBlock(history, encodedSignature) { block =>
          Json.obj("height" -> history.heightOf(block))
        }.toString()
      }
    }
  }

  @Path("/height")
  @ApiOperation(value = "Height", notes = "Get blockchain height", httpMethod = "GET")
  def height: Route = {
    path("height") {
      jsonRoute {
        Json.obj("height" -> history.height()).toString()
      }
    }
  }

  @Path("/at/{height}")
  @ApiOperation(value = "At", notes = "Get block at specified height", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "height", value = "Block height", required = true, dataType = "Long", paramType = "path")
  ))
  def at: Route = {
    path("at" / IntNumber) { case height =>
      jsonRoute {
        history match {
          case blockchain: BlockChain =>
            blockchain
              .blockAt(height)
              .map(_.json)
              .getOrElse(Json.obj("status" -> "error", "details" -> "No block for this height")).toString()
          case _ =>
            Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain").toString()
        }
      }
    }
  }

  @Path("/seq/{from}/{to}")
  @ApiOperation(value = "Seq", notes = "Get block at specified heights", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "from", value = "Start block height", required = true, dataType = "Int", paramType = "path"),
    new ApiImplicitParam(name = "to", value = "End block height", required = true, dataType = "Int", paramType = "path")
  ))
  def seq: Route = {
    path("seq" / IntNumber / IntNumber) { case (start, end) =>
      jsonRoute {
        history match {
          case blockchain: BlockChain =>
            JsArray(
            (start to end).map { height =>
               blockchain.blockAt(height).map(_.json).getOrElse(Json.obj("error" -> s"No block at height $height"))
            }).toString()
          case _ =>
            Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain").toString()
        }
      }
    }
  }


  @Path("/last")
  @ApiOperation(value = "Last", notes = "Get last block data", httpMethod = "GET")
  def last: Route = {
    path("last") {
      jsonRoute {
        history.lastBlock.json.toString()
      }
    }
  }

  @Path("/first")
  @ApiOperation(value = "First", notes = "Get genesis block data", httpMethod = "GET")
  def first: Route = {
    path("first") {
      jsonRoute {
        history.genesis.json.toString()
      }
    }
  }

  @Path("/signature/{signature}")
  @ApiOperation(value = "Signature", notes = "Get block by a specified Base58-encoded signature", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "String", paramType = "path")
  ))
  def signature: Route = {
    path("signature" / Segment) { case encodedSignature =>
      jsonRoute {
        withBlock(history, encodedSignature)(_.json).toString()
      }
    }
  }
}
