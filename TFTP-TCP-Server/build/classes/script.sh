javac *.java
kill -KILL $(lsof -t -i:12345)
java TFTPTCPServer
