package org.wallet.service

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.WalletTestInfra
import org.wallet.service.AccountService.Success
import org.wallet.service.TransferService.{Incorrect, InsufficientFunds, Transferred}

import scala.concurrent.Await
import scala.concurrent.duration._


class TransferServiceSpec extends TestKit(ActorSystem("AccountServiceSpec")) with FunSpecLike with Matchers with ImplicitSender with WalletTestInfra {

  implicit val timeout = Timeout(1 seconds)
  override val hashingPollSize: Int = 10

  describe("Transfer Service should ") {
    it("transfer money between two accounts") {
      //given
      val did = "testId1"
      val rid = "testId2"
      val depositAmount = 100d
      val transferAmount = 10d

      //when
      val Success(100d, afterDepositBalance) = Await.result(accountsService.deposit(did, depositAmount), timeout.duration) //+100d
      val Transferred(_, "testId1", "testId2", transferredAmount, 90d) = Await.result(transferService.transfer(did, rid, transferAmount), timeout.duration) //-10d

      //expected
      transferAmount shouldBe transferredAmount
    }

    it("transfer reject when insufficient funds") {
      //given
      val did = "testId3"
      val rid = "testId4"
      val transferAmount = 10d

      //when
      val InsufficientFunds(_, did2, rid2, 10d, 0d) = Await.result(transferService.transfer(did, rid, transferAmount), timeout.duration) //-10d

      //expected
      did shouldBe did2
      rid shouldBe rid2
    }

    it("transfer reject when same ids") {
      //given
      val did = "testId5"
      val rid = "testId5"
      val transferAmount = 10d

      //when
      val Incorrect(_, did2, rid2, 10d, msg) = Await.result(transferService.transfer(did, rid, transferAmount), timeout.duration) //-10d

      //expected
      did shouldBe did2
      rid shouldBe rid2
      msg shouldBe TransferService.SAME_IDS_ERROR
    }

    it("transfer reject when amount 0d") {
      //given
      val did = "testId5"
      val rid = "testId5"
      val transferAmount = 0d

      //when
      val Incorrect(_, did2, rid2, 0d, msg) = Await.result(transferService.transfer(did, rid, transferAmount), timeout.duration) //-10d

      //expected
      did shouldBe did2
      rid shouldBe rid2
      msg shouldBe TransferService.POSITIVE_AMOUNT
    }

    it("transfer reject when negative amount ") {
      //given
      val did = "testId5"
      val rid = "testId5"
      val transferAmount = -10d

      //when
      val Incorrect(_, did2, rid2, -10d, msg) = Await.result(transferService.transfer(did, rid, transferAmount), timeout.duration) //-10d

      //expected
      did shouldBe did2
      rid shouldBe rid2
      msg shouldBe TransferService.POSITIVE_AMOUNT
    }
  }
}
