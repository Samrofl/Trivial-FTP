/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 *
 * @author sb
 */
public class TFTPUDPServer {
    /**
     * @param args the command line arguments
     */    
    //Define parameters
    private static final String SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_PORT = 12345;
    
    public static void main(String[] args) throws UnknownHostException, SocketException, IOException {
        InetAddress serverIP = InetAddress.getByName(SERVER_IP);
        DatagramSocket masterSocket = new DatagramSocket(DEFAULT_PORT, serverIP);
     
        
        System.out.println("Server Running");
        
        while(true){
            byte[] buffer = new byte[256]; //Create dummy packet to buffer receiver.
            DatagramPacket packet = new DatagramPacket(buffer,256);
            masterSocket.receive(packet);
            System.out.println("Packet Received");
            new TFTPUDPServerThread(packet).start();
        }
    }
}
