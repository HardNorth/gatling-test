import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicLong

import io.gatling.core.Predef.Simulation

/**
  * Created by delgr on 19.03.2017.
  */
class ReportPortalLoadTest extends Simulation{

  object LogEntryGenerator extends Iterator[String]
  {
    private val uniquieId: AtomicLong = new AtomicLong()

    override def hasNext: Boolean = true

    override def next(): String =
    {
      val sdf: SimpleDateFormat = new SimpleDateFormat("")
      null
    }
  }

}
