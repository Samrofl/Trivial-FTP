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
import static java.lang.System.exit;
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
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 *
 * @author sb
 */
public class TFTPUDPClient {
    /**
     * @param args the command line arguments
     */    
    //Define parameters
    private static final String SERVER_IP = "127.0.0.1";
    private static final String MODE = "octet";
    //OP codes
    private static final byte RRQ=1;
    private static final byte WRQ=2;
    private static final byte DATA=3;
    private static final byte ACK=4;
    private static final byte ERROR=5;
    //Define max packet size
    private static final int PACKET_SIZE=516;
    private static int clientPort;
    private static int threadPort;
    InetAddress serverIP;
    DatagramSocket clientSocket;
    DatagramPacket rrqPacket;
    DatagramPacket wrqPacket;
    private String fileName;
    private File transferFile;
    private short blockNum;
    
    
    public static void main(String[] args) throws UnknownHostException, SocketException, IOException {
        TFTPUDPClient client = new TFTPUDPClient();
        client.createSocket();
        client.menuInput();                
    }
    
    private TFTPUDPClient(){
        blockNum=1;
    }
    
    private void menuInput() throws UnknownHostException, SocketException, IOException{
        System.out.println("OPTIONS\n=======\n1. Read a file \n2. Write a file");
        Scanner reader = new Scanner(System.in);
        int input = reader.nextInt();
        switch(input){
            case 1: System.out.println("Enter file name to read:");
                //Scan user input
                Scanner rrqReader = new Scanner(System.in);
                String rrqFilename = rrqReader.nextLine();
                //Create packet with filename based on user input
                rrqPacket = new DatagramPacket(createRequest(RRQ,rrqFilename, MODE), createRequest(RRQ,rrqFilename, MODE).length,serverIP,12345);
                clientSocket.send(rrqPacket); //Send request packet to server
                clientSocket.setSoTimeout(10000); //Start timeout timer.
                //Wait for acknowledgement from server
                ByteArrayOutputStream outputStream = receiveFile();
                if (outputStream != null){
                    writeFile(outputStream,rrqFilename);
                }
                break;
            case 2: System.out.println("Enter file name to write:");
                //Scan user input
                Scanner wrqReader = new Scanner(System.in);
                checkForFile(wrqReader.nextLine());
                //Create packet with filename based on user input
                wrqPacket = new DatagramPacket(createRequest(WRQ,fileName, MODE), createRequest(WRQ, fileName, MODE).length,serverIP,12345);
                clientSocket.send(wrqPacket); //Send request packet to server
                receiveAck(); //Wait for acknowledgement from server
                break;
            default: System.out.println("Invalid Input");
                menuInput();
        }  
    }
    
    
    //Method to create read requests and write requests
    private byte[] createRequest(byte opCode, String filename, String mode){
        byte zero = 0;
        int rqLength = 2 + filename.length() + 1 + mode.length() + 1; //Define length of byte array.
        byte[] rqByteArray = new byte[rqLength];
        int i = 0;
        rqByteArray[i] = zero;
        i++;
        rqByteArray[i] = opCode;
        i++;
        for (int j=0;j<filename.length();j++){
            rqByteArray[i]=(byte) filename.charAt(j);
            i++;
        }
        rqByteArray[i] = zero;
        i++;
        for (int j=0;j<mode.length();j++){
            rqByteArray[i] = (byte) mode.charAt(j);
            i++;
        }
        rqByteArray[i] = zero;
        return rqByteArray;
    }
    
    //Method to be rewritten in small places
    //method will receive files
    private ByteArrayOutputStream receiveFile() throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DatagramPacket inboundPacket = null;
        do {            
            //Buffer incoming packet
            byte[] bufferByteArray = new byte[PACKET_SIZE];
            inboundPacket = new DatagramPacket(bufferByteArray, bufferByteArray.length, serverIP, clientSocket.getLocalPort());
            //Receive packet from server
            clientSocket.receive(inboundPacket);
            if (inboundPacket.getData()[1]==ERROR){
                errorReport(inboundPacket);
                return null;
            }
            //If the inbound packet is data, write the data to a ByteArrayOutputStream object.
            else if (inboundPacket.getData()[1]==DATA){
                byte[] ackBlock = {inboundPacket.getData()[2], inboundPacket.getData()[3]};
                DataOutputStream d = new DataOutputStream(outputStream);
                threadPort = inboundPacket.getPort();
                d.write(inboundPacket.getData(), 4, inboundPacket.getLength()-4);
                //Send ack back to the server
                sendAck(ackBlock);
            }
        }while(!isLastPacket(inboundPacket));
        return outputStream;
    }
    
    private void sendAck(byte[] blockNum) throws IOException{
        byte[] ackByteArray = {0, ACK, blockNum[0], blockNum[1]};
        DatagramPacket ack = new DatagramPacket(ackByteArray, ackByteArray.length, serverIP, threadPort);
        clientSocket.send(ack);
    }
    
    //Generate a random number for the port
    //Assigns the InetAddress object of the server IP address
    //Creates the datagram socket for the client.
    private void createSocket() throws UnknownHostException, SocketException{
        Random r = new Random();
        int min = 1025;
        int max = 65535;
        int result = r.nextInt(max-min) + min;
        clientPort = result;
        serverIP = InetAddress.getByName(SERVER_IP);
        clientSocket = new DatagramSocket(clientPort, serverIP);
    }

    //Waits to receive the acknowledgement.
    private void receiveAck() throws IOException {
        byte[] buffer = new byte[256]; //Create dummy packet to buffer receiver.
        DatagramPacket packet = new DatagramPacket(buffer,256);
        
        while(true){
            clientSocket.receive(packet);
            threadPort = packet.getPort();
            //If packet returned from server is [0,4,0,0] then send the file.
            if (packet.getData()[1] == ACK && packet.getData()[2] == 0 && packet.getData()[3] == 0){
                System.out.println("Ack Packet Received");
                System.out.println("Writing file: " + fileName);
                sendFile();
            }
            //Check for error packet
            else if(packet.getData()[1] == ERROR){
            }
        }
    }
    
    private void sendFile() throws IOException{
        FileInputStream fileInputStream = null;
        ByteArrayInputStream bis = null;
        byte[] packetFileArray = null;
        byte[] bufferArray = null;
        int available;
        boolean delivered=false;
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
        
        //Create packet from the byte array & send it.
        DatagramPacket filePacket = new DatagramPacket(bufferArray,bufferArray.length, serverIP, threadPort);
        clientSocket.send(filePacket);
        //Buffering the ack array for receipt.
        byte[] bufferAck = new byte [4];
        DatagramPacket ackPacket = new DatagramPacket(bufferAck,4);
        //Set a timer for timeout (10s).
        clientSocket.setSoTimeout(1000);
        while (delivered==false){
            try{
                clientSocket.receive(ackPacket);
                delivered=true;
            }
            //If file not received, timeout and resend packet.`
            catch(SocketTimeoutException e){
                System.out.println("Timeout: " + e);
                sendFile();
            }
        }
        //If the ack is appropriate for the block number, send the file.
        if(ackBlockToShort(ackPacket.getData()) == blockNum){
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
            exit(1);
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
    
    private short blockToShort(byte[] b){
        short[] shorts = new short[1];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        short num = shorts[0];
        return num;
    }
    
    private short ackBlockToShort(byte[] b){
        byte[] bytes = new byte[2];
        bytes[0]=b[2];
        bytes[1]=b[3];
        short[] shorts = new short[1];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        short num = shorts[0];
        return num;
    }
    
    private byte[] shortToBlock(short i){
        byte[] blockArray = new byte[2];
        blockArray[0] = (byte)(i & 0xff);
        blockArray[1] = (byte)((i >> 8) & 0xff);
        return blockArray;
    }

    private void writeFile(ByteArrayOutputStream outputStream, String rrqFilename) throws FileNotFoundException {
        try {
            OutputStream out = new FileOutputStream(rrqFilename);
            outputStream.writeTo(out);
            System.out.println("Transfer Complete!");
        } catch (IOException ex) {
            Logger.getLogger(TFTPUDPClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void checkForFile(String s) throws IOException{
        File f = new File(s);
        //Check if file exists
        if(f.exists()){
            System.out.println(System.getProperty("user.dir") + "/" + s + " exists");
            fileName = s;
            transferFile = f;
        }
        //If not client must enter a valid filename.
        else {
            System.out.println(System.getProperty("user.dir") + "/" + s + " does not exist");
            System.out.println("Enter file name to write: ");
            Scanner wrqReader = new Scanner(System.in);
            checkForFile(wrqReader.nextLine());
        }
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