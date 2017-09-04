package org.wallet.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import org.wallet.service.AccountService.{Fail, Success}
import org.wallet.service.{AccountService, TransferService}
import org.wallet.transfer.TransferActor._
import spray.json._

class Routes(accountService: AccountService, transferService: TransferService) {

  import Routes._

  lazy val routes: Route =
    pathPrefix("account" / Segment) {
      accountId =>
        get {
          onSuccess(accountService.getBalance(accountId)) {
            case Success(_, balance) => complete(Balance(balance))
          }
        } ~
          path("deposit") {
            post {
              entity(as[Deposit]) { payload =>
                onSuccess(accountService.deposit(accountId, payload.amount)) {
                  case Success(change, finalBalance) =>
                    complete(Deposited(change, finalBalance))
                  case Fail(change, finalBalance, msg) =>
                    complete(BadRequest, FailedRequest(-change, finalBalance, msg))
                }
              }
            }
          } ~ path("withdraw") {
          post {
            entity(as[Withdraw]) { payload =>
              onSuccess(accountService.withdraw(accountId, payload.amount)) {
                case Success(change, finalBalance) =>
                  complete(Withdrawn(change, finalBalance))
                case Fail(change, finalBalance, msg) =>
                  complete(BadRequest, FailedRequest(-change, finalBalance, msg))
              }
            }
          }
        } ~ path("transfer" / Segment) {
          recipientId =>
            post {
              entity(as[Transfer]) { payload =>
                onSuccess(transferService.transfer(accountId, recipientId, payload.amount)) {
                  case TransferService.Transferred(transactionId, donorId, recipientId, change, donorBalance) =>
                    complete(Transferred(transactionId, change, donorBalance))

                  case TransferService.InsufficientFunds(transactionId, donorId, recipientId, amount, donorBalance) =>
                    complete(BadRequest, TransferRejected(transactionId, donorId, recipientId, amount, "Insufficient Funds", Some(donorBalance)))

                  case TransferService.Incorrect(transactionId, donorId, recipientId, amount, msg) =>
                    complete(BadRequest, TransferRejected(transactionId, donorId, recipientId, amount, msg))
                }
              }
            }
        } ~ path("asyncTransfer" / Segment) {
          recipientId =>
            post {
              entity(as[Transfer]) { payload =>
                transferService.asyncTransfer(accountId, recipientId, payload.amount) match {
                  case TransferService.Accepted(transactionId, donorId, recipientId, amount) =>
                    complete(TransferState(transactionId, donorId, recipientId, amount, "Accepted", 0f))

                  case TransferService.Incorrect(transactionId, donorId, recipientId, amount, msg) =>
                    complete(TransferRejected(transactionId, donorId, recipientId, amount, msg))
                }
              }
            }
        }
    } ~
      pathPrefix("transfer" / Segment) {
        transferId =>
          get {
            onSuccess(transferService.transferState(transferId)) {
              case Uninitialized() => complete(NotFound)
              case m@ProcessingWithdrawal(tId, donorId, recipientId, amount) => complete(TransferState(tId, donorId, recipientId, amount, m.getClass.getSimpleName, 1/3))
              case m@ProcessingDeposit(tId, donorId, recipientId, amount) => complete(TransferState(tId, donorId, recipientId, amount, m.getClass.getSimpleName, 2/3))
              case m@Succeeded(tId, donorId, recipientId, amount) => complete(TransferState(tId, donorId, recipientId, amount, m.getClass.getSimpleName, 1))
              case m@Failed(tId, donorId, recipientId, amount, msg) => complete(TransferState(tId, donorId, recipientId, amount, m.getClass.getSimpleName, 1, Some(msg)))
            }
          }
      }
}


  object Routes extends SprayJsonSupport with DefaultJsonProtocol {

    //account
    case class Balance(balance: Double)

    case class Deposit(amount: Double)

    case class Deposited(change: Double, currentBalance: Double)


    case class Withdraw(amount: Double)

    case class Withdrawn(change: Double, currentBalance: Double)

    case class FailedRequest(change: Double, currentBalance: Double, msg: String)

    //transfer
    case class Transfer(amount: Double)

    case class Transferred(transactionId:String, amount: Double, currentBalance: Double)

    case class TransferRejected(transactionId:String, donorId:String, recipientId:String, amount: Double, msg:String,  currentBalance: Option[Double] = None)

    case class TransferState(transactionId:String, donorId:String, recipientId:String, amount:Double, stateName:String, progress:Float, msg:Option[String] = None)


    implicit val balanceFormat: RootJsonFormat[Balance] = jsonFormat1(Balance)
    implicit val depositFormat: RootJsonFormat[Deposit] = jsonFormat1(Deposit)
    implicit val depositedFormat: RootJsonFormat[Deposited] = jsonFormat2(Deposited)
    implicit val withdrawFormat: RootJsonFormat[Withdraw] = jsonFormat1(Withdraw)
    implicit val withdrawnFormat: RootJsonFormat[Withdrawn] = jsonFormat2(Withdrawn)
    implicit val transferformat: RootJsonFormat[Transfer] = jsonFormat1(Transfer)
    implicit val transferredFormat: RootJsonFormat[Transferred] = jsonFormat3(Transferred)
    implicit val failedRequestFormat: RootJsonFormat[FailedRequest] = jsonFormat3(FailedRequest)
    implicit val transferRejectedFormat: RootJsonFormat[TransferRejected] = jsonFormat6(TransferRejected)
    implicit val transferStateFormat: RootJsonFormat[TransferState] = jsonFormat7(TransferState)
  }