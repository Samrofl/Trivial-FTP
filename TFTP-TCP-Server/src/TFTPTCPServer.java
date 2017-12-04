/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
/**
 *
 * @author sb
 */
public class TFTPTCPServer {
    /**
     * @param args the command line arguments
     */    
    //Define parameters
    private static final String SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_PORT = 12345;
    
    public static void main(String[] args) throws UnknownHostException, SocketException, IOException {
        InetAddress serverIP = InetAddress.getByName(SERVER_IP);
        ServerSocket masterSocket;
        Socket slaveSocket;
        
        masterSocket = new ServerSocket(DEFAULT_PORT, 8, serverIP);
        System.out.println("Server Running");
        
        while(true){
            slaveSocket = masterSocket.accept();
            System.out.println("Creating Thread...");
            new TFTPTCPServerThread(slaveSocket).start();
        }
    }
}
