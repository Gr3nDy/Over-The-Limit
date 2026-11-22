package otl.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * The slice of WebSocket the mod needs: write one masked text frame, read one whole message.
 *
 * <p>Java 8 has no WebSocket client and the game runs on it, so Supabase Realtime is spoken over a
 * plain socket through this codec. Client frames must be masked and server frames must not be, and
 * a fragmented message is assembled here. Pings are returned to the caller instead of being
 * answered, because the socket has a single writer thread and two writers would interleave frames.
 */
final class WsFrames {
    static final int CONTINUATION=0x0,TEXT=0x1,BINARY=0x2,CLOSE=0x8,PING=0x9,PONG=0xA;
    /** Anything bigger is not a board update: refuse it instead of allocating the buffer. */
    static final int MAX_PAYLOAD=64*1024;

    static final class Message {
        final int opcode;
        final byte[] payload;
        Message(int opcode,byte[] payload){this.opcode=opcode;this.payload=payload;}
        String text(){return new String(payload,StandardCharsets.UTF_8);}
    }

    /**
     * One reader per connection: a fragmented message can be interrupted by a ping, so the partly
     * assembled message has to outlive a single call.
     */
    static final class Reader {
        private ByteArrayOutputStream message;
        private int messageOpcode=-1;

        /**
         * Reads one message. Returns null at end of stream; a close frame comes back as a message
         * so the caller can close the socket cleanly. Fragments are joined into one TEXT or BINARY
         * message, so callers never see a CONTINUATION.
         */
        Message read(DataInputStream in)throws IOException{
            while(true){
                int first=in.read();
                if(first<0)return null;
                boolean fin=(first&0x80)!=0;
                int opcode=first&0x0F;
                int second=in.readUnsignedByte();
                boolean masked=(second&0x80)!=0;
                long length=second&0x7F;
                if(length==126)length=in.readUnsignedShort();
                else if(length==127)length=in.readLong();
                if(length<0||length>MAX_PAYLOAD)throw new IOException("WebSocket frame too large: "+length);
                byte[] key=new byte[4];
                if(masked)in.readFully(key);
                byte[] payload=new byte[(int)length];
                in.readFully(payload);
                if(masked)for(int i=0;i<payload.length;i++)payload[i]^=key[i&3];
                switch(opcode){
                    case PING:
                    case PONG:
                        // Control frames never join a message; the partial one is kept for later.
                        return new Message(opcode,payload);
                    case CONTINUATION:
                        if(message==null)throw new IOException("WebSocket continuation without a message");
                        message.write(payload,0,payload.length);
                        if(message.size()>MAX_PAYLOAD)throw new IOException("WebSocket message too large");
                        if(fin)return finish();
                        continue;
                    case TEXT:
                    case BINARY:
                        if(!fin){
                            message=new ByteArrayOutputStream();
                            messageOpcode=opcode;
                            message.write(payload,0,payload.length);
                            continue;
                        }
                        return new Message(opcode,payload);
                    case CLOSE:
                        return new Message(CLOSE,payload);
                    default:
                        throw new IOException("Unsupported WebSocket opcode "+opcode);
                }
            }
        }

        private Message finish(){
            Message done=new Message(messageOpcode,message.toByteArray());
            message=null;
            messageOpcode=-1;
            return done;
        }
    }

    private WsFrames(){}

    /** One client frame: always masked and final, so the server sees a normal message. */
    static byte[] frame(int opcode,byte[] payload,Random mask){
        int length=payload.length;
        ByteArrayOutputStream out=new ByteArrayOutputStream(length+14);
        out.write(0x80|opcode);
        if(length<126){
            out.write(0x80|length);
        }else if(length<=0xFFFF){
            out.write(0x80|126);
            out.write(length>>>8);
            out.write(length);
        }else{
            out.write(0x80|127);
            for(int shift=56;shift>=0;shift-=8)out.write((int)((long)length>>>shift));
        }
        byte[] key=new byte[4];
        mask.nextBytes(key);
        out.write(key,0,4);
        for(int i=0;i<length;i++)out.write(payload[i]^key[i&3]);
        return out.toByteArray();
    }

    static byte[] text(String json,Random mask){
        return frame(TEXT,json.getBytes(StandardCharsets.UTF_8),mask);
    }

    /** The close frame a well-behaved client sends before dropping the socket. */
    static byte[] close(int code,String reason){
        byte[] text=reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload=new byte[2+text.length];
        payload[0]=(byte)(code>>>8);payload[1]=(byte)code;
        System.arraycopy(text,0,payload,2,text.length);
        return frame(CLOSE,payload,new Random());
    }
}
