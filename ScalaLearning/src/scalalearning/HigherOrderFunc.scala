

/**
 * @author kgundego
 */
object HigherOrderFunc {
  def main(args: Array[String]): Unit = {
    println(hf(someFunc, 1))
  }
  
  def hf(f:Int => String, v: Int) = f(v)
  
  def someFunc(value: Int) = {
    println("Value of integer passed : ", value)
    "Returned String : "+value
  }
  
  main(Array(""))
}
