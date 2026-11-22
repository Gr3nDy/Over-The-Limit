package otl.client;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One WebSocket connection over TLS, opened by hand because Java 8 has no client.
 *
 * <p>The HTTP upgrade is written and its response parsed here, then the same buffered stream is
 * handed to {@link WsFrames}. Writes are serialised on a lock because a ping is answered from the
 * reader thread while board updates leave the writer thread.
 */
final class WsSocket implements Closeable {
    private static final String GUID="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    final DataInputStream in;
    private final SSLSocket socket;
    private final OutputStream out;
    private final Object writeLock=new Object();

    private WsSocket(SSLSocket socket,DataInputStream in,OutputStream out){
        this.socket=socket;this.in=in;this.out=out;
    }

    static WsSocket open(URL url)throws IOException{
        // https and wss are the same thing here: TLS on 443. Plaintext is refused outright.
        if(!"https".equalsIgnoreCase(url.getProtocol()))
            throw new IOException("Only https:// or wss:// is supported, got "+url.getProtocol());
        String host=url.getHost();
        int port=url.getPort()<0?443:url.getPort();
        StringBuilder path=new StringBuilder(url.getPath()==null||url.getPath().isEmpty()?"/":url.getPath());
        if(url.getQuery()!=null)path.append('?').append(url.getQuery());

        SSLSocket socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket();
        try{
            socket.setSoTimeout(90000);
            socket.setTcpNoDelay(true);
            SSLParameters parameters=socket.getSSLParameters();
            // Certificate and host name are checked, so a proxy or a hijacked DNS cannot sit in.
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            try{
                parameters.setServerNames(Collections.singletonList(new SNIHostName(host)));
            }catch(IllegalArgumentException localName){/* an IP address needs no SNI */}
            socket.setSSLParameters(parameters);
            socket.connect(new InetSocketAddress(host,port),10000);
            socket.startHandshake();

            BufferedInputStream input=new BufferedInputStream(socket.getInputStream());
            OutputStream output=socket.getOutputStream();
            byte[] nonce=new byte[16];
            new Random().nextBytes(nonce);
            String key=Base64.getEncoder().encodeToString(nonce);
            String request="GET "+path+" HTTP/1.1\r\n"
                +"Host: "+host+"\r\n"
                +"Upgrade: websocket\r\n"
                +"Connection: Upgrade\r\n"
                +"Sec-WebSocket-Key: "+key+"\r\n"
                +"Sec-WebSocket-Version: 13\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.UTF_8));
            output.flush();

            String status=readLine(input);
            if(status==null||!status.contains(" 101"))
                throw new IOException("WebSocket upgrade refused: "+status);
            Map<String,String> headers=new HashMap<>();
            for(String line;(line=readLine(input))!=null&&!line.isEmpty();){
                int colon=line.indexOf(':');
                if(colon>0)headers.put(line.substring(0,colon).trim().toLowerCase(Locale.ROOT),
                    line.substring(colon+1).trim());
            }
            String accept=headers.get("sec-websocket-accept");
            if(accept!=null&&!accept.equals(expectedAccept(key)))
                throw new IOException("WebSocket accept header did not match the key");
            return new WsSocket(socket,new DataInputStream(input),output);
        }catch(IOException|RuntimeException failure){
            try{socket.close();}catch(IOException ignored){}
            throw failure;
        }
    }

    /** One whole frame; the lock keeps a pong and a board update from interleaving. */
    void write(byte[] frame)throws IOException{
        synchronized(writeLock){
            out.write(frame);
            out.flush();
        }
    }

    @Override public void close(){
        try{socket.close();}catch(IOException ignored){}
    }

    private static String expectedAccept(String key)throws IOException{
        try{
            MessageDigest sha1=MessageDigest.getInstance("SHA-1");
            byte[] digest=sha1.digest((key+GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        }catch(java.security.NoSuchAlgorithmException impossible){
            throw new IOException("SHA-1 is unavailable",impossible);
        }
    }

    /** Reads one header line, without its CR; null at end of stream. */
    private static String readLine(BufferedInputStream in)throws IOException{
        StringBuilder line=new StringBuilder();
        int b;
        while((b=in.read())>=0){
            if(b=='\n')break;
            if(b!='\r')line.append((char)b);
        }
        if(b<0&&line.length()==0)return null;
        return line.toString();
    }
}
