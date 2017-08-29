package org.wallet.account

import akka.actor.{Actor, ActorLogging, Props}


class AccountActor() extends Actor with ActorLogging {

  val id = self.path.name


  override def receive: Receive = normal(Account(id, 0d))

  def normal(state: Account): Receive = {
    case command: Account.Command =>
      log.debug("account {} got command {}", id, command)
      val event = state.process(command)
      val newstate = state.apply(event)
      context.become(normal(newstate))
      log.debug("account {} published response {}", id, event)
      sender() ! event
  }
}

object AccountActor{
  def props() = Props(new AccountActor())
}
