package org.wallet.transfer


object CountCharsOcurrences {

  def apply(text: String): Map[Char, Int] = {

    text.foldLeft(Map.empty[Char, Int]) { (acc, elem) =>
      acc + (elem -> (acc.getOrElse(elem, 0) + 1))
    }
  }
}

case class CountLongString(value: Map[Char, Int] = Map.empty) {

  def apply(text: String): CountLongString = {

    val newIndex = CountCharsOcurrences(text)
    val updated = newIndex.map {
      case (char, i) =>
      char -> (value.getOrElse(char, 0) + i)
    }



    CountLongString(updated)
  }

}
