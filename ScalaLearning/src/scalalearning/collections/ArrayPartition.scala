package scalalearning.collections

/**
 * @author Karthik
 */
object ArrayPartition {
  val people = Array(12, 20, 30 ,682,237, 2 ,1, 89 , 96)
  val (minor, major) = people partition (_ < 18)
  
}