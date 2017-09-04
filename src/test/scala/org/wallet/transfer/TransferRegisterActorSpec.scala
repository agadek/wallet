package org.wallet.transfer

import akka.actor.ActorSystem
import akka.pattern.gracefulStop
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.transfer.TransferRegisterActor.{RegisterTransaction, ShardConfig, TransactionCompleted, WakeUp}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TransferRegisterActorSpec extends TestKit(ActorSystem("TransferRegisterActorSpec")) with FunSpecLike with Matchers with ImplicitSender {


  describe("Registrator should keep valid list of transfer, and:") {
    it("wake up all after reboot") {

      //given
      val ids = List("1", "2", "3", "4", "5")
      val excludedId = List("3")
      val transferRegistrator = system.actorOf(TransferRegisterActor.props(), "TransferRegisterActorSpec")
      val probe = TestProbe()
      probe.send(transferRegistrator, ShardConfig(probe.ref))


      //when
      ids.foreach(id => probe.send(transferRegistrator, RegisterTransaction(id)))
      excludedId.foreach(id => probe.send(transferRegistrator, TransactionCompleted(id)))

      //todo somehow PoisonPill kill actor faster then previous msgs are processed
      Thread.sleep(500)
      val stopped: Future[Boolean] = gracefulStop(transferRegistrator, 2 seconds)
      Await.result(stopped, 3 seconds)

      val transferRegistrator2 = system.actorOf(TransferRegisterActor.props(), "TransferRegisterActorSpec")
      transferRegistrator2 ! ShardConfig(probe.ref)


      //expected
      val allWakeups = probe.receiveWhile(5 seconds, 1 seconds, 15) { case wup: WakeUp => wup.transferId }
      allWakeups.size shouldBe ids.size - excludedId.size
      allWakeups.toSet shouldBe ids.diff(excludedId).toSet


    }
  }
}
