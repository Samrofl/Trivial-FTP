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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 *
 * @author sb
 */
public class TFTPTCPClient {
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
    Socket clientSocket;
    byte[] rrqArray;
    byte[] wrqArray;
    private String fileName;
    private File transferFile;
    private short blockNum;
    private DataOutputStream dos;
    private DataInputStream dis;
    
    
    public static void main(String[] args) throws UnknownHostException, SocketException, IOException {
        TFTPTCPClient client = new TFTPTCPClient();
        client.menuInput();                
    }
    
    private TFTPTCPClient(){
        blockNum=1;
    }
    
    private void menuInput() throws UnknownHostException, SocketException, IOException{
        System.out.println("OPTIONS\n=======\n1. Read a file \n2. Write a file");
        Scanner reader = new Scanner(System.in);
        int input = reader.nextInt();
        switch(input){
            case 1: System.out.println("Enter file name to read:");
                createSocket(); //Estabish server connection.
                //Scan user input
                Scanner rrqReader = new Scanner(System.in);
                String rrqFilename = rrqReader.nextLine();
                rrqArray = createRequest(RRQ,rrqFilename, MODE); //Create packet with filename based on user input
                dos = new DataOutputStream(clientSocket.getOutputStream()); //Assign outputstream
                dos.writeInt(rrqArray.length);
                dos.write(rrqArray,0,rrqArray.length); //Write request packet to stream.
                ByteArrayOutputStream outputStream = receiveFile();
                if (outputStream != null){
                    writeFile(outputStream,rrqFilename);
                }
                break;
            case 2: System.out.println("Enter file name to write:");
                createSocket(); //Establish server connection.
                //Scan user input
                Scanner wrqReader = new Scanner(System.in);
                checkForFile(wrqReader.nextLine());
                wrqArray = createRequest(WRQ,fileName, MODE); //Create packet with filename based on user input
                dos = new DataOutputStream(clientSocket.getOutputStream()); //Assign outputstream
                dos.writeInt(wrqArray.length);
                dos.write(wrqArray,0,wrqArray.length); //Write request packet to stream.
                sendFile();
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
    
    //Generate a random number for the port
    //Assigns the InetAddress object of the server IP address
    //Creates the datagram socket for the client.
    private void createSocket() throws UnknownHostException, SocketException, IOException{
        clientPort = 12345;
        serverIP = InetAddress.getByName(SERVER_IP);
        clientSocket = new Socket(serverIP, clientPort);
    }
    
    private ByteArrayOutputStream receiveFile() throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] incomingArray = null;
        do {
            //Receive packet from server
            dis = new DataInputStream(clientSocket.getInputStream());
            incomingArray = new byte[dis.readInt()];
            dis.read(incomingArray);
            if (incomingArray[1]==ERROR){
                errorReport(incomingArray);
                return null;
            }
            //If the inbound packet is data, write the data to a ByteArrayOutputStream object.
            else if (incomingArray[1]==DATA){
                dos = new DataOutputStream(outputStream);
                dos.write(incomingArray, 4, incomingArray.length-4);
            }
        }while(!isLastPacket(incomingArray));
        return outputStream;
    }
    
    private void errorReport(byte[] error){
        int i=4;
        //Prints the error message of the packet.
        while(error[i]!=0){
            i++;
        }
        String s = new String(error, 4, i-4);
        System.out.println(s);
    }
    
    private boolean isLastPacket(byte[] array){
        return array.length < 516;
    }
    
    private void writeFile(ByteArrayOutputStream outputStream, String rrqFilename) throws FileNotFoundException {
        try {
            OutputStream out = new FileOutputStream(rrqFilename);
            outputStream.writeTo(out);
            System.out.println("Transfer Complete!");
        } catch (IOException ex) {
            Logger.getLogger(TFTPTCPClient.class.getName()).log(Level.SEVERE, null, ex);
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
        dos = new DataOutputStream(clientSocket.getOutputStream()); //Assign outputstream
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
    
    private byte[] shortToBlock(short i){
        byte[] blockArray = new byte[2];
        blockArray[0] = (byte)(i & 0xff);
        blockArray[1] = (byte)((i >> 8) & 0xff);
        return blockArray;
    }
}