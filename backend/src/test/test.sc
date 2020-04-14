val m1:Map[String,String] = Map("k1" -> "v1", "k2" -> "vv1")
val m2:Map[String,String] = Map("k1" -> "v2", "k2" -> "vv2")
val m3:Map[String,String] = Map("k1" -> "v3", "k2" -> "vv3")

val listMap1 = List(m1,m2,m3)

listMap1.filter(_.contains("k1") ).map(_("k1") ).mkString(",")