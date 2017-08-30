package org.wallet.account

import akka.actor.{Actor, ActorLogging, Props}


class AccountActor() extends Actor with ActorLogging {

  val id: String = self.path.name


  override def receive: Receive = normal(Account(id, 0d))

  def normal(state: Account): Receive = {
    case command: Account.Command =>
      log.info("account {} got command {}", id, command)
      val event = state.process(command)
      val newState = state.apply(event)
      context.become(normal(newState))
      log.info("account {} published response {}", id, event)
      sender() ! event
  }
}

object AccountActor{
  def props() = Props(new AccountActor())
}
