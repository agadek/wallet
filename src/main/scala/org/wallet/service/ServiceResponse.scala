package org.wallet.service

/**
  * Created by gadek on 8/31/17.
  */
object ServiceResponse {

  sealed trait Response

  case class Success[T](result: T) extends Response

  case class Fail[T](result: T) extends Response

}
