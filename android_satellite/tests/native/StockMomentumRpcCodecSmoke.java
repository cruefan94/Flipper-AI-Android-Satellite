package com.flipperai.satellite;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
public final class StockMomentumRpcCodecSmoke {
  static void ok(boolean value,String why){if(!value)throw new AssertionError(why);}
  static void reject(Runnable task){try{task.run();throw new AssertionError("should reject");}catch(IllegalArgumentException correct){}}
  static void varint(ByteArrayOutputStream o,long v){while(true){int b=(int)(v&127);v>>>=7;if(v==0){o.write(b);return;}o.write(b|128);}}
  static byte[] field(int tag,byte[] content){ByteArrayOutputStream b=new ByteArrayOutputStream();varint(b,(tag<<3)|2);varint(b,content.length);b.write(content,0,content.length);return b.toByteArray();}
  static byte[] frame(byte[] payload){ByteArrayOutputStream b=new ByteArrayOutputStream();varint(b,payload.length);b.write(payload,0,payload.length);return b.toByteArray();}
  static byte[] incoming(int field,long id,byte[] body){ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(8);varint(b,id);byte[] f=field(field,body);b.write(f,0,f.length);return frame(b.toByteArray());}
  static void run(){
    byte[] start=StockMomentumRpcCodec.appStart(7);
    ok(start.length>25,"app start frame present");
    ok(new String(start,StandardCharsets.ISO_8859_1).contains(StockMomentumRpcCodec.FAP_PATH),"path exact");
    ok(new String(start,StandardCharsets.ISO_8859_1).endsWith("RPC"),"launch RPC args");
    byte[] send=StockMomentumRpcCodec.appData(11,"AI1|HELLO_ACK|1|android-v1\n");
    ok(send.length>30,"app data frame present");
    byte[] fromFlipper=incoming(65,0,field(1,"AI1|HELLO|1|flipper-v1\n".getBytes(StandardCharsets.UTF_8)));
    StockMomentumRpcCodec.StreamDecoder dec=new StockMomentumRpcCodec.StreamDecoder();
    int count=0;
    for(byte part:fromFlipper){List<StockMomentumRpcCodec.Message> msgs=dec.feed(new byte[]{part});for(var msg:msgs){count++;ok(msg.kind==StockMomentumRpcCodec.Kind.APP_DATA,"app data kind");ok(msg.commandId==0,"unsolicited id");ok(StockMomentumRpcCodec.strictUtf8(msg.appData).equals("AI1|HELLO|1|flipper-v1\n"),"hello payload");}}
    ok(count==1,"fragment assembly once");
    byte[] started=incoming(58,0,new byte[]{8,1});
    ok(dec.feed(started).get(0).appState==1,"flipper app started state");
    byte[] closed=incoming(58,0,new byte[]{8,0});
    ok(dec.feed(closed).get(0).appState==0,"flipper app closed state");
    byte[] emptyAck=incoming(4,7,new byte[0]);
    ok(dec.feed(emptyAck).get(0).commandId==7,"start ack correlation");
    reject(()->StockMomentumRpcCodec.appData(1,"AI1|ASK|1|bad\nline\n"));
    reject(()->StockMomentumRpcCodec.appData(0,"AI1|ASK|1|ok\n"));
    reject(()->new StockMomentumRpcCodec.StreamDecoder().feed(new byte[]{(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0x7f}));
    byte[] badData=incoming(65,0,field(1,new byte[]{'A','I','1','|',(byte)0xc3,40,'\n',0}));
    reject(()->new StockMomentumRpcCodec.StreamDecoder().feed(badData));
    dec.feed(Arrays.copyOfRange(fromFlipper,0,5));
    dec.reset(); // no cross-device stale fragments after disconnect
    ok(dec.feed(fromFlipper).size()==1,"reconnect clears partial RPC frame");
    byte[] pair=new byte[started.length+closed.length];
    System.arraycopy(started,0,pair,0,started.length);System.arraycopy(closed,0,pair,started.length,closed.length);
    ok(dec.feed(pair).size()==2,"multiple notifications in single chunk");
    System.out.println("Stock Momentum RPC codec: 16 checks passed");
  }
  public static void main(String[] args){run();}
}
