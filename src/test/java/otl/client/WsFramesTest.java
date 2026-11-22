package otl.client;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/** The WebSocket codec, which has to be right before any socket is involved. */
public class WsFramesTest {
    private static DataInputStream stream(byte[] bytes){
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    /** A server frame is not masked; only the fin bit and opcode are set here. */
    private static byte[] serverFrame(int opcode,boolean fin,String text){
        byte[] payload=text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write((fin?0x80:0)|opcode);
        out.write(payload.length);
        out.write(payload,0,payload.length);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] part:parts)out.write(part,0,part.length);
        return out.toByteArray();
    }

    /** A client frame must be masked, and unmasking it must give the text back exactly. */
    @Test public void maskedFrameCarriesTheText(){
        String json="{\"event\":\"board\",\"payload\":{\"n\":\"Alex\",\"b\":true}}";
        byte[] frame=WsFrames.text(json,new Random(1));
        assertEquals(0x81,frame[0]&0xFF);
        assertTrue("client frames must be masked",(frame[1]&0x80)!=0);
        int length=frame[1]&0x7F;
        assertEquals(json.getBytes(StandardCharsets.UTF_8).length,length);
        byte[] key=Arrays.copyOfRange(frame,2,6);
        byte[] body=Arrays.copyOfRange(frame,6,frame.length);
        for(int i=0;i<body.length;i++)body[i]^=key[i&3];
        assertEquals(json,new String(body,StandardCharsets.UTF_8));
    }

    /** 125 bytes still fits the short length; 126 and 65536 have to widen the header. */
    @Test public void lengthHeaderWidensAtTheBoundaries(){
        assertEquals(125,WsFrames.text(payload(125),new Random(1))[1]&0x7F);
        assertEquals(126,WsFrames.text(payload(126),new Random(1))[1]&0x7F);
        assertEquals(127,WsFrames.text(payload(65536),new Random(1))[1]&0x7F);
        // The 126 marker is followed by the real 16-bit length, then the 4-byte mask.
        byte[] frame=WsFrames.text(payload(126),new Random(1));
        assertEquals(126,(frame[2]&0xFF)<<8|(frame[3]&0xFF));
    }

    private static String payload(int length){
        char[] chars=new char[length];
        Arrays.fill(chars,'x');
        return new String(chars);
    }

    @Test public void fragmentsAreJoined()throws Exception{
        byte[] bytes=concat(serverFrame(WsFrames.TEXT,false,"{\"a\":"),serverFrame(WsFrames.CONTINUATION,true,"1}"));
        WsFrames.Message message=new WsFrames.Reader().read(stream(bytes));
        assertEquals(WsFrames.TEXT,message.opcode);
        assertEquals("{\"a\":1}",message.text());
    }

    /** A ping can land between fragments; it must be handed over without losing the first half. */
    @Test public void pingBetweenFragmentsKeepsTheMessage()throws Exception{
        WsFrames.Reader reader=new WsFrames.Reader();
        DataInputStream in=stream(concat(
            serverFrame(WsFrames.TEXT,false,"ab"),
            serverFrame(WsFrames.PING,true,"p"),
            serverFrame(WsFrames.CONTINUATION,true,"cd")));
        WsFrames.Message ping=reader.read(in);
        assertEquals(WsFrames.PING,ping.opcode);
        assertEquals("p",ping.text());
        assertEquals("abcd",reader.read(in).text());
    }

    @Test public void closeComesBackAsAMessage()throws Exception{
        WsFrames.Message message=new WsFrames.Reader().read(stream(serverFrame(WsFrames.CLOSE,true,"")));
        assertEquals(WsFrames.CLOSE,message.opcode);
    }

    @Test public void endOfStreamIsNull()throws Exception{
        assertNull(new WsFrames.Reader().read(stream(new byte[0])));
    }

    @Test(expected=IOException.class)
    public void oversizedFrameIsRefused()throws Exception{
        // A 127 length of 0x20000 is past the cap: the buffer is never allocated.
        byte[] header={0x01,0x7F,0,0,0,0,0,2,0,0};
        new WsFrames.Reader().read(stream(header));
    }
}
