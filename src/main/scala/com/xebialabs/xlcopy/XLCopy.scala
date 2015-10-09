package com.xebialabs.xlcopy

import java.io.File
import java.time.{Duration, LocalTime, LocalDate}

import com.xebialabs.overthere._
import com.xebialabs.overthere.local.LocalFile
import com.xebialabs.overthere.ssh.SshConnectionBuilder
import com.xebialabs.xlcopy.OptionParser.{OptionMapBuilder, OptionMap}
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.{DefaultConfig, SSHClient}
import org.slf4j.LoggerFactory

object XLCopy {

  val usage =
    """overthere-copy [options] <source> <destination>
      |  --host | -h <host>                 The host to connect to
      |  --os | -o <operating_system>       One of 'UNIX', 'WINDOWS', 'ZOS' (default: 'UNIX')
      |  --user | -u <user>                 The username to connect as
      |  --password | -p <password>         The password for the user
      |  --buffer-size | -b <buffersize>    The copy buffer size to use (in bytes)
    """.stripMargin

  val Host = 'host
  val OperatingSystem = 'os
  val User = 'user
  val Password = 'password
  val BufferSize = 'bufferSize

  val Source = 'source
  val Destination = 'destination

  val opts: OptionMapBuilder = Map(
    "--host | -h" -> Host,
    "--os | -o" -> OperatingSystem,
    "--password | -p" -> Password,
    "--user | -u" -> User,
    "--buffer-size | -b" -> BufferSize
  )

  val positional: List[Symbol] = List(Source, Destination)

  def main(args: Array[String]) {
    val map = OptionParser.parseOptions(args.toList, positional, opts, Map(), Map(), strict = true)
    OptionParser.validate(map, List(Host, User, Password, Source, Destination)) match {
      case None =>
        println(usage)
        sys.exit(1)
      case _ =>
    }


    val options: ConnectionOptions = new ConnectionOptions()
    options.set(ConnectionOptions.ADDRESS, map(Host))
    options.set(ConnectionOptions.OPERATING_SYSTEM, map.getOrElse(OperatingSystem, OperatingSystemFamily.UNIX))
    options.set(ConnectionOptions.USERNAME, map(User))
    options.set(ConnectionOptions.PASSWORD, map(Password))
    options.set(SshConnectionBuilder.CONNECTION_TYPE, "SFTP")
    if (map.contains(BufferSize)) {
      options.set(ConnectionOptions.REMOTE_COPY_BUFFER_SIZE, map(BufferSize))
    }
    val connection: OverthereConnection = Overthere.getConnection(SshConnectionBuilder.SSH_PROTOCOL, options)
    try {
      val file: OverthereFile = connection.getFile(map(Destination).toString)
      timed("Overthere/SSHJ", LocalFile.from(new File(map(Source).toString)).copyTo(file))
    } finally {
      connection.close()
    }

    val config = new DefaultConfig
    val client = new SSHClient(config)
    client.addHostKeyVerifier(new PromiscuousVerifier)
    client.connect(map(Host).asInstanceOf[String])
    client.authPassword(map(User).asInstanceOf[String], map(Password).asInstanceOf[String].toCharArray)
    val newSFTPClient: SFTPClient = client.newSFTPClient()
    try {
      timed("SSHJ", newSFTPClient.getFileTransfer.upload(map(Source).asInstanceOf[String], map(Destination).asInstanceOf[String]))
    } finally {
      newSFTPClient.close()
      client.close()
    }
  }

  def timed(method: String, f: => Unit): Unit = {
    val start = LocalTime.now()
    try {
      f
    } finally {
      val end = LocalTime.now()
      val between: Duration = java.time.Duration.between(start, end)
      logger.info(s"Copying using $method took ${between.getNano} nano-seconds (${between.getSeconds} seconds)")
    }
  }

  val logger = LoggerFactory.getLogger(XLCopy.getClass)
}
