package collins

import java.io.File

import play.api.db.evolutions.OfflineEvolutions

object DbUtil extends App {
  Console.println("Running evolutions...")
  if (args.length != 1) {
    sys.error("Pass the directory containing your db folder")
  } else {
    val dir = args(0)
    OfflineEvolutions.applyScript(new File(dir), this.getClass.getClassLoader, "collins")
  }

}
