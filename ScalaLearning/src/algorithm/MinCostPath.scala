package algorithm

/**
 * @author kgundego
 */
object MinCostPath {
  def minCostPath(cost: Array[Array[Int]], m: Int, n:Int) = {
      var tc = Array.ofDim[Int](m, n)
      
      for(i <- 1 to m) {
        tc(i)(0) = tc(i-1)(0) + cost(i)(0)
      }
      
      for(j <- 1 to n) {
        tc(0)(j) = j;
      }
       
      for(i <- 1 to m) {
        for(j <- 1 to n) {
          tc(i)(j) = min(tc(i-1)(j-1), tc(i-1)(j), tc(i)(j-1)) + cost(i)(j)
        }
      }
      
      cost(m)(n)
  }                                               
  
  def min(x:Int, y:Int, z:Int) : Int = {
    var min = x
    if(y < x)  min = y
    if(z < min) min = z
     min
  }                                              
  
  val cost = Array.ofDim[Int](3,3)                
                                                  
  cost(0) = Array(1,2,3)
  cost(1) = Array(4,8,2)
  cost(2) = Array(1,5,3)
  
  println(minCostPath(cost, 2, 2))
  
}