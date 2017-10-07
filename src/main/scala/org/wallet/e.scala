package org.wallet
  import cats.{Applicative, Monad}
  import cats.data.Writer
  import cats.instances.list._
  import cats.data.OptionT

object e extends App {


  type Logged[A] = Writer[List[String], A]
  // Example method that returns nested monads:

  def parseNumber(str: String): Logged[Option[Int]] =
    util.Try(str.toInt).toOption match {
      case Some(num) => Writer(List(s"Read $str"), Some(num))
      case None      => Writer(List(s"Failed on $str"), None)
    }
  // Example combining multiple calls to parseNumber:
  def addNumbers(
                  a: String,
                  b: String,
                  c: String
                ): Logged[Option[Int]] = {

    val r = 1
    val e2 = parseNumber(a)
    val e = OptionT(parseNumber(a)) //(cats.instances.list.catsKernelStdMonoidForList)
println(e.getClass().getCanonicalName)
    // Transform the incoming stacks to work on them:
    val result = for {
      a <- OptionT(parseNumber(a))
      b <- OptionT(parseNumber(b))
      c <- OptionT(parseNumber(c))
    } yield a + b + c
    // Return the untransformed monad stack:
    result.value
  }
//  // This approach doesn't force OptionT on other users' code:
  val result1 = addNumbers("1", "2", "3")
  println(result1)
//  // result1: Logged[Option[Int]] = WriterT((List(Read 1, Read 2,
//  //Read 3),Some(6)))
  val result2 = addNumbers("1", "a", "3")
  println(result2)
//  // result2: Logged[Option[Int]] = WriterT((List(Read 1, Failed
//  //on a),None))


  //import cats.Semigroup
//  import cats.implicits._
//  import cats._
//  import cats.data.Monad

//  Monoid[Map[String, Int]].combineAll(List(Map("a" → 1, "b" → 2), Map("a" → 3)))
//  Monoid[Map[String, Int]].combineAll(List())
//
//  val l = List(1, 2, 3, 4, 5)
//  l.foldMap(identity)
//  l.foldMap(i ⇒ i.toString)
//
//  import cats.implicits._
//  val option2 = Option(1) |@| Option(2)
//  val option3 = option2 |@| Option.empty[Int]

  //option2.
  import cats._
  import cats.implicits._

  Monad[Option].ifM(Option(true))(Option("truthy"), Option("falsy")) should be(
    Option("truthy")
  )
  Monad[List].ifM(List(true, false, true))(List(1, 2), List(3, 4)) should be(
    List(3, 4)
  )

}
