/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sb
 */
public class TFTPTCPServerThread extends Thread{
    
    private static final String SERVER_IP = "127.0.0.1";
    //OP codes
    private static final byte RRQ=1;
    private static final byte WRQ=2;
    private static final byte DATA=3;
    private static final byte ACK=4;
    private static final byte ERROR=5;
    //Define max packet size
    private static final int PACKET_SIZE=516;
    private byte[] initialPacketArray;
    private InetAddress serverIP;
    private int threadPort;
    private final Socket threadSocket;
    private File transferFile;
    private short blockNum;
    private String fileName;
    private DataOutputStream dos;
    private DataInputStream dis;
    
    public TFTPTCPServerThread(Socket socket) throws IOException{
        threadSocket = socket;
        dis = new DataInputStream(threadSocket.getInputStream());
        initialPacketArray = new byte[dis.readInt()];
        dis.read(initialPacketArray);
        System.out.println("Packet Received");
        blockNum=1;
    }
    
    @Override
    public void run(){
        switch(initialPacketArray[1]){
            case 1:
                System.out.println("Read Request Received");
                {
                    try {
                        checkForFile(openReadRequest(initialPacketArray));
                    } catch (IOException ex) {
                        Logger.getLogger(TFTPTCPServerThread.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                break;
            case 2:
                System.out.println("Write Request Received");
                openWriteRequest(initialPacketArray);
                ByteArrayOutputStream outputStream;
                try {
                    outputStream = receiveFile();
                    if (outputStream != null){
                    writeFile(outputStream,fileName);
                }
                } catch (IOException ex) {
                    Logger.getLogger(TFTPTCPServerThread.class.getName()).log(Level.SEVERE, null, ex);
                }
                break;
        }
    }
    
        private String openReadRequest(byte[] request){
        int i=2;
        while(request[i]!=0){
            //System.out.println(packet.getData()[i]);
            i++;
        }
        String s = new String(request, 2, i-2);
        System.out.println("Checking for file: " + s);
        return s;
    }
    
    private void openWriteRequest(byte[] request){
        int i=2;
        //Prints the name of the file entered.
        while(request[i]!=0){
            //System.out.println(packet.getData()[i]);
            i++;
        }
        String s = new String(request, 2, i-2);
        fileName=s;
        System.out.println(s);
    }
    
    private void checkForFile(String s) throws IOException{
        File f = new File(s);
        //If file exists send file.
        if(f.exists()){
            System.out.println(System.getProperty("user.dir") + "/" + s + " exists");
            transferFile = f;
            sendFile();
        }
        else {
            System.out.println(System.getProperty("user.dir") + "/" + s + " does not exist");
            createErrorByteArray();
            byte[] errorArray=createErrorByteArray();
            dos = new DataOutputStream(threadSocket.getOutputStream()); //Assign outputstream
            dos.writeInt(errorArray.length);
            dos.write(errorArray,0,errorArray.length); //Write error packet to stream.            
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
            bufferArray = new byte[PACKET_SIZE];
        }
        else {
            packetFileArray = new byte[available];
            fileInputStream.read(packetFileArray,0,available);
            bufferArray = new byte[4+available];
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
        dos = new DataOutputStream(threadSocket.getOutputStream()); //Assign outputstream
        dos.writeInt(bufferArray.length);
        dos.write(bufferArray,0,bufferArray.length); //Write error packet to stream.
       //If the ack is appropriate for the block number, send the file.
        if (blockNum==65535){
            blockNum=1;
            } else {
            blockNum++;
        }
        //Will not keep calling the method if the end of file has been achieved.
        if (fileInputStream.available()!=0){
            sendFile();
        }
        else{
            System.out.println("Transfer complete!");
        }
    }
    
    private ByteArrayOutputStream receiveFile() throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] incomingArray = null;
        do {
            //Receive packet from server
            dis = new DataInputStream(threadSocket.getInputStream());
            incomingArray = new byte[dis.readInt()];
            dis.read(incomingArray);
            //If the inbound packet is data, write the data to a ByteArrayOutputStream object.
            if (incomingArray[1]==DATA){
                dos = new DataOutputStream(outputStream);
                dos.write(incomingArray, 4, incomingArray.length-4);
            }
        }while(!isLastPacket(incomingArray));
        return outputStream;
    }
    
    private void writeFile(ByteArrayOutputStream outputStream, String rrqFilename) throws FileNotFoundException {
        try {
            OutputStream out = new FileOutputStream(rrqFilename);
            outputStream.writeTo(out);
            System.out.println("Transfer Complete!");
        } catch (IOException ex) {
            Logger.getLogger(TFTPTCPServerThread.class.getName()).log(Level.SEVERE, null, ex);
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
    
    private byte[] shortToBlock(short i){
        byte[] blockArray = new byte[2];
        blockArray[0] = (byte)(i & 0xff);
        blockArray[1] = (byte)((i >> 8) & 0xff);
        return blockArray;
    }
    
    private boolean isLastPacket(byte[] array){
        return array.length < 516;
    }    
}