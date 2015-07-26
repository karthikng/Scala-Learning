package scalalearning

/**
 * @author kgundego
 */
class Currying extends App{
  def addition(a:Int)(b:Int) = a + b
  
  println("Currying demo")
  
  // Partially applied function
  var curryFunction = addition(10)_
  
 
  //Applying rest of argument to curry function
  println("Result of addtion of 10 + 20 = ",curryFunction(20))
  
}