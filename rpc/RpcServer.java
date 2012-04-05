package rpc;

import identifiers.IPP;
import identifiers.SID;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import rpc.message.RpcMessage;
import rpc.message.RpcMessageCall;
import rpc.message.RpcMessageReply;
import server.SessionTable;
import server.SessionTable.Entry;

public class RpcServer extends Thread {

    private static RpcServer theServer = new RpcServer();
    private DatagramSocket rpcSocket;
    private static int callIDCounter;
    private static IPP ippLocal;

    /**
     * Private Constructor (Singleton Pattern)
     * Use getInstance() to access
     */
    private RpcServer() {
        super("ServerThread");
        try {
            rpcSocket = new DatagramSocket();
            callIDCounter = 10000 * rpcSocket.getLocalPort();
            InetAddress localIP = null;
            while(localIP == null) {
                try {
                    localIP = InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
            ippLocal = new IPP(localIP, rpcSocket.getLocalPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while(true) {
                byte[] inBuf = new byte[RpcMessage.BUFFER_SIZE];
                DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
                rpcSocket.receive(recvPkt);
                InetAddress returnAddr = recvPkt.getAddress();
                int returnPort = recvPkt.getPort();
                RpcMessageCall recvMessage = (RpcMessageCall) RpcMessage.readByteStream(inBuf);
                int operationCode = recvMessage.getOpCode(); // get requested operationCode
                RpcMessage reply = null;
                switch( operationCode ) {
                    case RpcMessage.READ:
                        reply = SessionRead(recvMessage);
                        break;
                    case RpcMessage.WRITE:
                        reply = SessionWrite(recvMessage);
                        break;
                    case RpcMessage.DELETE:
                        reply = SessionDelete(recvMessage);
                        break;
                    case RpcMessage.NOOP:
                        reply = NoOp(recvMessage);
                        break;
                }

                byte[] outBuf = reply.toByteStream();
                // here outBuf should contain the callID and results of the call
                DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
                        returnAddr, returnPort);
                rpcSocket.send(sendPkt);
            }
        } catch(Exception e) {
            //TODO: smart exception handly stuff
            e.printStackTrace();
        }
    }

    public static RpcServer getInstance() {
        return theServer;
    }

    public RpcMessageReply SessionRead(RpcMessageCall call) {
        SID sid = (SID)call.getArguments().get(0);
        int changeCount = (Integer)call.getArguments().get(1);
        SessionTable table = SessionTable.getInstance();

        Entry entry = table.get(sid);
        ArrayList<Object> results = null;
        if(entry.version == changeCount) {
            results = new ArrayList<Object>();
            results.add(entry.message);
            results.add(entry.expiration);
        }
        return new RpcMessageReply(call.getCallID(), results);
    }

    public RpcMessageReply SessionWrite(RpcMessageCall call) {
        SID sid = (SID)call.getArguments().get(0);
        int changeCount = (Integer)call.getArguments().get(1);
        String data = (String)call.getArguments().get(2);
        long discardTime = (Long)call.getArguments().get(3);
        SessionTable table = SessionTable.getInstance();

        table.put(sid, new Entry(changeCount, data, discardTime));
        return new RpcMessageReply(call.getCallID(), new ArrayList());
    }

    public RpcMessageReply SessionDelete(RpcMessageCall call) {
        SID sid = (SID)call.getArguments().get(0);
        int changeCount = (Integer)call.getArguments().get(1);
        SessionTable table = SessionTable.getInstance();
        table.destroySession(sid, changeCount);

        return new RpcMessageReply(call.getCallID(), new ArrayList());
    }

    public RpcMessageReply NoOp(RpcMessageCall call) {
        return new RpcMessageReply(call.getCallID(), new ArrayList());
    }

    /**
     * Gets a unique callID
     * @return
     */
    public int callID() {
        //getInstance() ensures the server is started first
        return getInstance().callIDCounter++;
    }

    /**
     * Gets a the port the UDP server is running on
     * @return
     */
    public int getPort() {
      //getInstance() ensures the server is started first
        return getInstance().rpcSocket.getLocalPort();
    }

    /**
     * gets the local IPP
     * @return
     */
    public IPP getIPPLocal() {
      //getInstance() ensures the server is started first
        return getInstance().ippLocal;
    }
}
