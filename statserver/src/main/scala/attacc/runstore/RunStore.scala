package attacc.runstore


trait RunStore extends Serializable {

  val runStore: RunStore.Service[Any]

}

object RunStore extends Serializable {
  trait Service[R] extends Serializable {

  }
}
