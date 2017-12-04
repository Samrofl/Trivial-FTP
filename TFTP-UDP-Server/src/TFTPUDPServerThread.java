/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sb
 */
public class TFTPUDPServerThread extends Thread{
    
    private static final String SERVER_IP = "127.0.0.1";
    //OP codes
    private static final byte RRQ=1;
    private static final byte WRQ=2;
    private static final byte DATA=3;
    private static final byte ACK=4;
    private static final byte ERROR=5;
    //Define max packet size
    private static final int PACKET_SIZE=516;
    private final DatagramPacket initialPacket;
    private byte[] initialPacketArray;
    private InetAddress serverIP;
    private final int clientPort;
    private int threadPort;
    private final DatagramSocket threadSocket;
    private File transferFile;
    private short blockNum;
    private String fileName;
    
    public TFTPUDPServerThread(DatagramPacket packet) throws UnknownHostException, SocketException{
        initialPacket=packet;
        clientPort = initialPacket.getPort();
        serverIP=InetAddress.getByName(SERVER_IP);
        threadSocket = createSocket();
        blockNum=1;
    }
    
    @Override
    public void run(){
        try {
            switch(initialPacket.getData()[1]){
                case 1:
                    System.out.println("Read Request Received");
                    checkForFile(openReadRequest(initialPacket));
                    break;
                case 2:
                    System.out.println("Write Request Received");
                    openWriteRequest(initialPacket);
                    createAckByteArray((short)0);
                    //Create the acknowledgement packet to send back to the client.
                    DatagramPacket ackPacket = new DatagramPacket(createAckByteArray((short)0), createAckByteArray((short)0).length,serverIP,clientPort);
                    threadSocket.send(ackPacket);
                    ByteArrayOutputStream outputStream = receiveFile();
                    writeFile(outputStream,fileName);
                    break;
            }
        } catch (IOException ex) {
            Logger.getLogger(TFTPUDPServerThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private String openReadRequest(DatagramPacket packet){
        int i=2;
        while(packet.getData()[i]!=0){
            //System.out.println(packet.getData()[i]);
            i++;
        }
        String s = new String(packet.getData(), 2, i-2);
        System.out.println("Checking for file: " + s);
        return s;
    }
    
    private void openWriteRequest(DatagramPacket packet){
        int i=2;
        //Prints the name of the file entered.
        while(packet.getData()[i]!=0){
            //System.out.println(packet.getData()[i]);
            i++;
        }
        String s = new String(packet.getData(), 2, i-2);
        fileName=s;
        System.out.println(s);
    }
    
    //Method to create the ack packet
    public byte[] createAckByteArray(short i){
        byte[] ackArray = new byte[4];
        byte zero = 0;
        ackArray[0] = zero;
        ackArray[1] = ACK;
        ackArray[2] = (byte)(i & 0xff);
        ackArray[3] = (byte)((i >> 8) & 0xff);
        return ackArray;
    }
    
    //Generate a random number for the port
    //Assigns the InetAddress object of the server IP address
    //Creates the datagram socket for the client.
    private DatagramSocket createSocket() throws UnknownHostException, SocketException{
        Random r = new Random();
        int min = 1025;
        int max = 65535;
        int result = r.nextInt(max-min) + min;
        threadPort = result;
        serverIP = InetAddress.getByName(SERVER_IP);
        return new DatagramSocket(threadPort, serverIP);
    }
    
    //Check to see if file exists
    private void checkForFile(String s) throws IOException{
        File f = new File(s);
        //If file exists send file.
        if(f.exists()){
            System.out.println(System.getProperty("user.dir") + "/" + s + " exists");
            transferFile = f;
            sendFile();
        }
        //If file doesn't exist, send error packet to the client.
        else {
            System.out.println(System.getProperty("user.dir") + "/" + s + " does not exist");
            createErrorByteArray();
            DatagramPacket errorPacket = new DatagramPacket(createErrorByteArray(), createErrorByteArray().length,serverIP,clientPort);
            threadSocket.send(errorPacket);
        }
    }
    
    private void sendFile() throws IOException{
        FileInputStream fileInputStream = null;
        ByteArrayInputStream bis = null;
        byte[] packetFileArray = null;
        byte[] bufferArray = null;
        boolean delivered=false;
        int available;
        fileInputStream = new FileInputStream(transferFile);
        //Define the offset and skip it to make sure bytes aren't sent multiple times.
        int offset = (blockNum-1)*512;
        fileInputStream.skip(offset);
        available = fileInputStream.available();
        //If the remaining file size is larger than the packet size, treat it accordingly.
        if (available > 512){
            packetFileArray = new byte[512];//buffer an array to fill with the file bytes for this specific packet.
            fileInputStream.read(packetFileArray,0,512);
            bufferArray = new byte[PACKET_SIZE]; //Create a buffer array
        }
        else {
            packetFileArray = new byte[available];
            fileInputStream.read(packetFileArray,0,available);
            bufferArray = new byte[4+available]; //Create a buffer array
        }
        bis = new ByteArrayInputStream(packetFileArray);
        //Fill array with op code and block number (as bytes).
        bufferArray[0]=0;
        bufferArray[1]=3;
        bufferArray[2]=shortToBlock(blockNum)[0];
        bufferArray[3]=shortToBlock(blockNum)[1];
        if (available > 512){
            bis.read(bufferArray,4,512);
        }
        else {
            bis.read(bufferArray,4,available);
        }
        //System.out.println(Arrays.toString(bufferArray));
        //Create packet from the byte array & send it.
        DatagramPacket filePacket = new DatagramPacket(bufferArray,bufferArray.length, serverIP, clientPort);
        threadSocket.send(filePacket);
        //Buffering the ack array for receipt.
        byte[] bufferAck = new byte [4];
        DatagramPacket ackPacket = new DatagramPacket(bufferAck,4);
        //Set a timer for timeout (10s).
        threadSocket.setSoTimeout(10000);
        while (delivered==false){
            try{
                threadSocket.receive(ackPacket);
                delivered=true;
            }
            //If file not received, timeout and resend packet
            catch(SocketTimeoutException e){
                System.out.println("Timeout: " + e);
                sendFile();
            }
        }
       //If the ack is appropriate for the block number, send the file.
        if(blockToShort(ackPacket.getData()) == blockNum){
            if (blockNum==65535){
                blockNum=0;
            } else {
                blockNum++;
            }
        }
        //Will not keep calling the method if the end of file has been achieved.
        if (fileInputStream.available()!=0){
            sendFile();
        }
        else{
            System.out.println("Transfer complete!");
        }
    }
    
    private byte[] createErrorByteArray(){
        int i=0;
        byte zero = 0;
        String errMsg = "Error: File not found";
        int packetLength = 4 + errMsg.length() + 1;
        byte errCode = 1;
        byte[] errorByteArray = new byte[packetLength];
        errorByteArray[i]=zero;
        i++;
        errorByteArray[i]=ERROR;
        i++;
        errorByteArray[i]=zero;
        i++;
        errorByteArray[i]=errCode;
        i++;
        for (int j=0;j<errMsg.length();j++){
            errorByteArray[i]=(byte)errMsg.charAt(j);
            i++;
        }
        errorByteArray[i]=zero;
        return errorByteArray;
    }
    
    private ByteArrayOutputStream receiveFile() throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DatagramPacket inboundPacket = null;
        do {            
            //Buffer incoming packet
            byte[] bufferByteArray = new byte[PACKET_SIZE];
            inboundPacket = new DatagramPacket(bufferByteArray, bufferByteArray.length, serverIP, threadSocket.getLocalPort());
            
            //Receive packet from server
            threadSocket.receive(inboundPacket);
            
            if (inboundPacket.getData()[1]==ERROR){
                errorReport(inboundPacket);
            }
            //If the inbound packet is data, write the data to a ByteArrayOutputStream object.
            else if (inboundPacket.getData()[1]==DATA){
                byte[] ackBlock = {inboundPacket.getData()[2], inboundPacket.getData()[3]};
                DataOutputStream d = new DataOutputStream(outputStream);
                d.write(inboundPacket.getData(), 4, inboundPacket.getLength()-4);
                //Send ack back to the server
                sendAck(ackBlock);
            }
        }while(!isLastPacket(inboundPacket));
        return outputStream;
    }
    
    private void writeFile(ByteArrayOutputStream outputStream, String rrqFilename) throws FileNotFoundException {
        try {
            OutputStream out = new FileOutputStream(rrqFilename);
            outputStream.writeTo(out);
        } catch (IOException ex) {
            Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void errorReport(DatagramPacket packet){
        int i=4;
        //Prints the error message of the packet.
        while(packet.getData()[i]!=0){
            i++;
        }
        String s = new String(packet.getData(), 4, i-2);
        System.out.println(s);
    }
    
    private void sendAck(byte[] blockNum) throws IOException{
        byte[] ackByteArray = {0, ACK, blockNum[0], blockNum[1]};
        DatagramPacket ack = new DatagramPacket(ackByteArray, ackByteArray.length, serverIP, clientPort);
        threadSocket.send(ack);
    }
    
    private byte[] shortToBlock(short i){
        byte[] blockArray = new byte[2];
        blockArray[0] = (byte)(i & 0xff);
        blockArray[1] = (byte)((i >> 8) & 0xff);
        return blockArray;
    }
    
    private short blockToShort(byte[] b){
        byte[] bytes = new byte[2];
        bytes[0]=b[2];
        bytes[1]=b[3];
        short[] shorts = new short[1];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        short num = shorts[0];
        return num;
    }
    
    private boolean isLastPacket(DatagramPacket packet){
        if (packet.getLength() < 516){
            return true;
        }
        else {
            return false;
        }
    }
}
