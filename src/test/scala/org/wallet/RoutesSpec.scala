package org.wallet

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpecLike}
import org.wallet.routes.Routes
import org.wallet.service.{AccountService, TransferService}

import scala.concurrent.Await
import scala.concurrent.duration._

class RoutesSpec extends WordSpecLike with Matchers with ScalatestRouteTest with WalletTestInfra {

  import Routes._

  override val hashingPollSize: Int = 100
    def routes: Route = new Routes(accountsService, transferService).routes

  "AccountsRoute" should {
    "GET `/account/$id` with 200 OK, and account state" in {
      Get("/account/1") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Balance] shouldBe Balance(0d)
      }
    }

    "Post `/account/$id/deposit` with OK 200, and deposit effect" in {
      val amount = 100d
      Post("/account/1/deposit", Deposit(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Deposited] shouldBe Deposited(amount, amount)
      }
    }

    "Post `/account/$id/deposit` with BadRequest 400, when negative deposit" in {
      val amount = -100d
      Post("/account/2/deposit", Deposit(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[FailedRequest] shouldBe FailedRequest(amount, 0, "Deposit amount must be positive value")
      }
    }

    "Post `/account/$id/withdraw` with OK 200, and withdrawal effect" in {
      val amount = 100d
      val accountId = "3"
      Await.result(accountsService.deposit(accountId, amount), 1 second)

      Post(s"/account/$accountId/withdraw", Withdraw(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Withdrawn] shouldBe Withdrawn(-amount, 0d)
      }
    }

    "Post `/account/$id/deposit` with BadRequest 400, when insufficient funds" in {
      val amount = 100d
      val accountId = "4"
      Post(s"/account/$accountId/withdraw", Withdraw(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[FailedRequest] shouldBe FailedRequest(amount, 0, "Insufficient Funds")
      }
    }

    "Post `/account/$id/deposit` with BadRequest 400, when negative withdrawal" in {
      val amount = -100d
      val accountId = "4"
      Post(s"/account/$accountId/withdraw", Withdraw(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[FailedRequest] shouldBe FailedRequest(amount, 0, "Withdrawal amount must be positive value")
      }
    }

    "Post `/account/$did/transfer/$rid with OK 200, and transfer effect" in {
      val amount1 = 1000d
      val amount2 = 100d
      val donorId = "10"
      val recipientId = "11"
      Await.result(accountsService.deposit(donorId, amount1), 1 second)

      Post(s"/account/$donorId/transfer/$recipientId", Transfer(amount2)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Transferred]
        response.amount shouldBe amount2
        response.currentBalance shouldBe amount1 - amount2
      }
    }

    "Post `/account/$did/transfer/$rid with BadRequest 400, when insufficient funds" in {
      val amount1 = 100d
      val amount2 = 1000d
      val donorId = "12"
      val recipientId = "13"
      Await.result(accountsService.deposit(donorId, amount1), 1 second)

      Post(s"/account/$donorId/transfer/$recipientId", Transfer(amount2)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        val response = responseAs[TransferRejected]
        response.donorId shouldBe donorId
        response.recipientId shouldBe recipientId
        response.amount shouldBe amount2
        response.msg shouldBe AccountService.INSUFFICIENT_FUNDS
        response.currentBalance shouldBe Some(amount1)
      }
    }

    "Post `/account/$did/transfer/$rid with BadRequest 400, when same ids" in {
      val amount = 100d
      val donorId = "12"
      val recipientId = "12"

      Post(s"/account/$donorId/transfer/$recipientId", Transfer(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        val response = responseAs[TransferRejected]
        response.donorId shouldBe donorId
        response.recipientId shouldBe recipientId
        response.amount shouldBe amount
        response.msg shouldBe TransferService.SAME_IDS_ERROR
        response.currentBalance shouldBe None
      }
    }

    "Post `/account/$did/transfer/$rid with BadRequest 400, when amount negative" in {
      val amount = -100d
      val donorId = "13"
      val recipientId = "13"

      Post(s"/account/$donorId/transfer/$recipientId", Transfer(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        val response = responseAs[TransferRejected]
        response.donorId shouldBe donorId
        response.recipientId shouldBe recipientId
        response.amount shouldBe amount
        response.msg shouldBe TransferService.POSITIVE_AMOUNT
        response.currentBalance shouldBe None
      }
    }

    "Post `/account/$did/transfer/$rid with BadRequest 400, when amount 0d" in {
      val amount = 0d
      val donorId = "13"
      val recipientId = "13"

      Post(s"/account/$donorId/transfer/$recipientId", Transfer(amount)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        val response = responseAs[TransferRejected]
        response.donorId shouldBe donorId
        response.recipientId shouldBe recipientId
        response.amount shouldBe amount
        response.msg shouldBe TransferService.POSITIVE_AMOUNT
        response.currentBalance shouldBe None
      }
    }


  }

}
