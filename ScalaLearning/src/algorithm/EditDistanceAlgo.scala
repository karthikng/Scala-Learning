

/**
 * @author kgundego
 */

object EditDistanceAlgo {
  
  def runAlgo(str1: String, str2: String):Int = {
    println("Running Edit distance algorithm")
    
    val costOfAddition = 1
    val costOfDeletion = 1
    val costOfInsertion = 1
    
    if(str2.length() == 0) {
      return str1.length()
    }
    if(str1.length() == 0) {
      return str2.length()
    }
    
    var matrix = Array.ofDim[Int](str1.length()+1, str2.length()+1)
    
    for(i <- 0 until str1.length()) {
      matrix(i)(0) = i
    }
    
    for(j <- 0 until str2.length()) {
      matrix(0)(j) = j
    }
    
    for(i <- 1 to str1.length()) {
      for(j <- 1 to str2.length()) {
        //When the characters match there is no penalty
        if(str1.charAt(i-1) == str2.charAt(j-1)) {
          matrix(i)(j) = matrix(i-1)(j-1)
          
        } else {
          val del = matrix(i-1)(j) + 1
          val ins = matrix(i)(j-1) + 1
          val rep = matrix(i-1)(j-1) + 1
          matrix(i)(j) = minCost(del, ins, rep)
        }
      }
    }
    
    matrix(str1.length()-1)(str2.length()-1)
  }                                         //> runAlgo: (str1: String, str2: String)Int
  
  def minCost(val1:Int, val2:Int, val3:Int) = {
    var min = if(val1 > val2) val2 else val1
    min = if(val3 < min) val3 else min
    min
  }                                         //> minCost: (val1: Int, val2: Int, val3: Int)Int

  println("Final cost : ",runAlgo("appropriate meaning", "approximate matching"))
                                                  //> Running Edit distance algorithm
                                                  //| (Final cost : ,7)
}