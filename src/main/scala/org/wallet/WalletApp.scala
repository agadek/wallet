package org.wallet

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import org.wallet.account.AccountActor
import org.wallet.routes.Routes
import org.wallet.service.{AccountService, TransferService}
import org.wallet.transfer.TransferRegisterActor.ShardConfig
import org.wallet.transfer.{TransferActor, TransferRegisterActor}

import scala.util.{Failure, Success}

object WalletApp extends App with LazyLogging{
  implicit val system: ActorSystem = ActorSystem("wallet-system")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val accountsShard = AccountActor.shard(system)

  val accountsService = new AccountService(accountsShard)

  //todo change to distributed poll or cluster singleton
  val transferRegistrator = system.actorOf(TransferRegisterActor.props())
  val transferShard = TransferActor.shard(system, accountsService.deposit, accountsService.withdraw, transferRegistrator)
  transferRegistrator ! ShardConfig(transferRegistrator)

  val transferService = new TransferService(transferShard)

  def routes: Route  = new Routes(accountsService, transferService).routes


  val interface = "localhost"
  val port = 8080
  val serverBinding = Http().bindAndHandle(routes, interface, port)


  serverBinding.onComplete {
    case Success(_) => logger.info(s"Wallet server $interface:$port started")
    case Failure(e) => logger.error(s"Wallet server $interface:$port cannot be started - ${e.getMessage}", e)
    system.terminate()
  }(system.dispatcher)

}
