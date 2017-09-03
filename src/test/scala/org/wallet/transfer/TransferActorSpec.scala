package org.wallet.transfer

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.service.AccountService.{Fail, Success}
import org.wallet.transfer.TransferActor.{InsufficientFunds, SyncTransfer, Transferred}
import akka.pattern.ask
import org.wallet.service.AccountService

import scala.concurrent.duration._
import akka.actor._
import akka.pattern.gracefulStop
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TransferActorSpec extends TestKit(ActorSystem("AccountActorSpec")) with FunSpecLike with Matchers with ImplicitSender {

  describe("TransferActor should process atomic transaction") {
    it("transfer happy path") {
      //given
      val amount = 100d
      val tid = "123"
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (tid:String, id: String, change: Double) => Future(Success(change, change)),
        withdraw = (tid:String, id: String, change: Double) => Future(Success(change, 13d))), tid)

      //when
      val command = SyncTransfer(tid, did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(Transferred(tid, did, rid, amount, 13d))
    }

    it("transfer fails because low balance") {
      //given
      val amount = 100d
      val tid = "1234"
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (tid:String, id: String, change: Double) => Future(Success(change, change)),
        withdraw = (tid:String, id: String, change: Double) => Future(Fail(change, 10d, ""))), tid)

      //when
      val command = SyncTransfer(tid, did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds(tid, did, rid, amount, 10d))
    }

    it("recover from journal and go back to processing ") {
      //given
      implicit val timeout = Timeout(5 seconds)

      val amount = 100d
      val tid = "12345"
      val did = "acc3"
      val rid = "acc4"

      val probe = TestProbe()

      val transfer = system.actorOf(TransferActor.props(
        deposit = (tid:String, id: String, change: Double) => Future(Success(change, change)),
        withdraw = (tid:String, id: String, change: Double) => (probe.ref ? (tid,id,change)).mapTo[AccountService.Response] ), tid)

      //when
      val command = SyncTransfer(tid, did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      probe.expectMsg((tid,did,amount))

      val stopped: Future[Boolean] = gracefulStop(transfer, 2 seconds)
      Await.result(stopped, 3 seconds)

      val transfer2 = system.actorOf(TransferActor.props(
        deposit = (tid:String, id: String, change: Double) => Future(Success(change, change)),
        withdraw = (tid:String, id: String, change: Double) => (probe.ref ? (tid,id,change)).mapTo[AccountService.Response] ), tid)

      probe.expectMsg((tid,did,amount))
    }
  }
}