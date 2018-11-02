package misk.client;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.SocketFactory;
import jnr.unixsocket.UnixSocket;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Impersonate TCP-style SocketFactory over UNIX domain sockets.
 *
 * Shamelessly stolen from
 * https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java,
 */
public final class UnixDomainSocketFactory extends SocketFactory {
  private final File path;

  public UnixDomainSocketFactory(File path) {
    this.path = path;
  }

  private Socket createUnixDomainSocket() throws IOException {
    UnixSocketChannel channel = UnixSocketChannel.open();

    return new UnixSocket(channel) {
      private InetSocketAddress inetSocketAddress;

      @Override public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, Integer.valueOf(0));
      }

      @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
        connect(endpoint, Integer.valueOf(timeout));
      }

      @Override public void connect(SocketAddress endpoint, Integer timeout) throws IOException {
        this.inetSocketAddress = (InetSocketAddress) endpoint;
        super.connect(new UnixSocketAddress(path), timeout);
      }

      @Override public InetAddress getInetAddress() {
        return inetSocketAddress.getAddress(); // TODO(jwilson): fake the remote address?
      }
    };
  }

  @Override public Socket createSocket() throws IOException {
    return createUnixDomainSocket();
  }

  @Override public Socket createSocket(String host, int port) throws IOException {
    return createUnixDomainSocket();
  }

  @Override public Socket createSocket(
      String host, int port, InetAddress localHost, int localPort) throws IOException {
    return createUnixDomainSocket();
  }

  @Override public Socket createSocket(InetAddress host, int port) throws IOException {
    return createUnixDomainSocket();
  }

  @Override public Socket createSocket(
      InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return createUnixDomainSocket();
  }

  /**
   * Return the {@link File} representing the path for the socket.
   */
  @VisibleForTesting public File getPath() {
    return path;
  }
}
