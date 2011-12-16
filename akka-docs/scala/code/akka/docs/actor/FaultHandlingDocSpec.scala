package akka.docs.actor

//#testkit
import akka.testkit.{ AkkaSpec, ImplicitSender, EventFilter }
import akka.actor.{ ActorRef, Props, Terminated }

//#testkit
object FaultHandlingDocSpec {
  //#supervisor
  //#child
  import akka.actor.Actor

  //#child
  //#supervisor
  //#supervisor
  class Supervisor extends Actor {
    def receive = {
      case p: Props ⇒ sender ! context.actorOf(p)
    }
  }
  //#supervisor

  //#supervisor2
  class Supervisor2 extends Actor {
    def receive = {
      case p: Props ⇒ sender ! context.actorOf(p)
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
  }
  //#supervisor2

  //#child
  class Child extends Actor {
    var state = 0
    def receive = {
      case ex: Exception ⇒ throw ex
      case x: Int        ⇒ state = x
      case "get"         ⇒ sender ! state
    }
  }
  //#child
}

//#testkit
class FaultHandlingDocSpec extends AkkaSpec with ImplicitSender {
  //#testkit

  import FaultHandlingDocSpec._
  //#testkit

  "A supervisor" must {

    "apply the chosen strategy for its child" in {
      //#testkit
      //#strategy
      import akka.actor.OneForOneStrategy
      import akka.actor.FaultHandlingStrategy._

      val strategy = OneForOneStrategy({
        case _: ArithmeticException      ⇒ Resume
        case _: NullPointerException     ⇒ Restart
        case _: IllegalArgumentException ⇒ Stop
        case _: Exception                ⇒ Escalate
      }: Decider, maxNrOfRetries = Some(10), withinTimeRange = Some(60000))

      //#strategy
      //#create
      val superprops = Props[Supervisor].withFaultHandler(strategy)
      val supervisor = system.actorOf(superprops, "supervisor")

      supervisor ! Props[Child]
      val child = expectMsgType[ActorRef] // retrieve answer from TestKit’s testActor
      //#create
      EventFilter[ArithmeticException](occurrences = 1) intercept {
        //#resume
        child ! 42 // set state to 42
        child ! "get"
        expectMsg(42)

        child ! new ArithmeticException // crash it
        child ! "get"
        expectMsg(42)
        //#resume
      }
      EventFilter[NullPointerException](occurrences = 1) intercept {
        //#restart
        child ! new NullPointerException // crash it harder
        child ! "get"
        expectMsg(0)
        //#restart
      }
      EventFilter[IllegalArgumentException](occurrences = 1) intercept {
        //#stop
        watch(child) // have testActor watch “child”
        child ! new IllegalArgumentException // break it
        expectMsg(Terminated(child))
        child.isTerminated must be(true)
        //#stop
      }
      EventFilter[Exception]("CRASH", occurrences = 4) intercept {
        //#escalate-kill
        supervisor ! Props[Child] // create new child
        val child2 = expectMsgType[ActorRef]

        watch(child2)
        child2 ! "get" // verify it is alive
        expectMsg(0)

        child2 ! new Exception("CRASH") // escalate failure
        expectMsg(Terminated(child2))
        //#escalate-kill
        //#escalate-restart
        val superprops2 = Props[Supervisor2].withFaultHandler(strategy)
        val supervisor2 = system.actorOf(superprops2, "supervisor2")

        supervisor2 ! Props[Child]
        val child3 = expectMsgType[ActorRef]

        child3 ! 23
        child3 ! "get"
        expectMsg(23)

        child3 ! new Exception("CRASH")
        child3 ! "get"
        expectMsg(0)
        //#escalate-restart
      }
      //#testkit
      // code here
    }
  }
}
//#testkit