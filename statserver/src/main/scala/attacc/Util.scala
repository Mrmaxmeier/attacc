package attacc

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import zio.ZIO

object Util {

  private val formatter = DateTimeFormatter.ofPattern("yyyy-M[M]-d[d]'T'HH:mm:ss[.n]X")
  def parseDateTime(datetime: String): LocalDateTime =
    LocalDateTime.from(formatter.parse(datetime))
  def formatDateTime(datetime: LocalDateTime): String =
    formatter.format(datetime)

  val randomUUID: ZIO[Any, Nothing, UUID] = ZIO.succeed(UUID.randomUUID())

  def require[E, T, A](option: ZIO[E, T, Option[A]], t: => T): ZIO[E, T, A] =
    option.flatMap {
      case Some(value) => ZIO.succeed(value)
      case None        => ZIO.fail(t)
    }

  def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  def hashFlag(flag: String): ZIO[Any, Nothing, String] =
    for {
      md <- ZIO.succeed(MessageDigest.getInstance("MD5"))
      _  <- ZIO.succeed(md.update(flag.getBytes(StandardCharsets.UTF_8)))
    } yield convertBytesToHex(md.digest())

}
