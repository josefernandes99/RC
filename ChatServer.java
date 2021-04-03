import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class User {
  private String nick;
  private String state;
  private String room;

  private String buffer;

  User(String nick){
    this.nick = nick;
    this.state = "init";
    this.room = "";
    this.buffer = "";
  }

  public String getNick() {
     return nick;
 }

 public void setNick(String nick) {
     this.nick = nick;
 }

 public String getState() {
     return state;
 }

 public void setState(String state) {
     this.state = state;
 }

 public String getRoom() {
     return room;
 }

 public void setRoom(String room) {
     this.room = room;
 }

 public String getBuffer() {
     return buffer;
 }

 public void setBuffer(String buffer) {
     this.buffer = buffer;
 }

}

public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  // Map socket channel to users
  static private HashMap<SocketChannel, User> users = new HashMap<>();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      // Linked List to store sockets that are connected
      LinkedList<SocketChannel> sockets = new LinkedList<>();

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Put user in hash users
            User newUser = new User("newUser_"+users.size());
            users.put(sc, newUser);

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ, newUser );

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {


              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();


              boolean ok = processInput( sc, users.get(sc) );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  static private boolean messageRoom( SocketChannel sc, String message, String room ) throws IOException{
    buffer.clear();
    buffer.put(message.getBytes());
    buffer.flip();

    if(buffer.limit()==0){
      return false;
    }

    for( SocketChannel other_sc: users.keySet()){

      if( room.equals(users.get( other_sc ).getRoom() ) ){
        while (buffer.hasRemaining()) {
          other_sc.write(buffer);
        }
      }
      buffer.rewind();
    }
    return true;
  }

  // Send a message back to a user
  static private boolean messageUser( SocketChannel sc, String message) throws IOException {
    buffer.clear();
    buffer.put(message.getBytes());
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    //Send the message to the user
    while (buffer.hasRemaining()) {
      sc.write(buffer);
    }

    return true;
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc, User user ) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();


    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString();
    String command;
    String argument; // para o nick e join
    String[] message_split = message.split(" ");


    if(message_split[0].charAt(0)=='/'){
      if(message_split[0].length()==1){
        return true;
      }
      else{
        if(message_split[0].equals("/nick")){
          if(message_split.length != 2 ){
            messageUser(sc, "ERROR\n");
            return true;
          }
          else{
            command = message_split[0];
            argument = message_split[1];
            return processCommand( sc, user , command , argument , null);
          }
        }
        else if(message_split[0].strip().equals("/leave")){
          if(message_split.length != 1 ){
            messageUser(sc, "ERROR\n");
            return true;
          }
          else{

            command = message_split[0].strip();
            return processCommand( sc, user , command , null , null);
          }
        }
        else if(message_split[0].strip().equals("/bye")){
          if(message_split.length != 1 ){
            messageUser(sc, "ERROR\n");
            return true;
          }
          else{
            command = message_split[0].strip();
            return processCommand( sc, user , command , null , null);
          }
        }
        else if(message_split[0].equals("/join")){
          if(message_split.length != 2 ){
            messageUser(sc, "ERROR\n");
            return true;
          }
          else{
            command = message_split[0];
            argument = message_split[1];
            return processCommand( sc, user , command , argument , null);
          }
        }
        else if(message_split[0].equals("/priv")){
          if(message_split.length < 3){
            messageUser(sc, "ERROR\n");
            return true;
          }
          else{
            command = message_split[0];
            argument = message_split[1];
            message = message.substring(command.length() + argument.length() + 2);
            return processCommand( sc, user , command , argument , message);
          }
        }
        else if(message_split[0].charAt(1)=='/'){
          if(user.getState().equals("inside")) return messageRoom( sc, "MESSAGE "+user.getNick().trim()+" "+message.substring(1), user.getRoom());
          else return messageUser( sc, "ERROR\n");
        }
        else{
          messageUser( sc, "Not command: ERROR\n");
          return true;
        }
      }
    }
    else{
      if(user.getState().equals("inside")) return messageRoom( sc, "MESSAGE "+user.getNick().trim()+" "+message, user.getRoom());
      else return messageUser( sc, "ERROR\n");
    }


  }

  static private boolean processCommand( SocketChannel sc, User user, String command, String argument, String message ) throws IOException {

    String old_room = user.getRoom();
    String old_nick = user.getNick();

    switch(command){

      case "/nick":
      boolean nick_available = true;
      //System.out.println("Nick command");
      for(User i: users.values()){
        String nick_try = i.getNick();
        if(nick_try.equals(argument)) nick_available = false;
      }

      if(!nick_available){
        messageUser(sc, "ERROR \n");
        break;
      }
      else{
        messageUser( sc, "OK\n");
        user.setNick(argument);
        if(user.getState().equals("init")){
          user.setState("outside");
        }
        else if(user.getState().equals("inside")){
          messageRoom( sc, "NEWNICK "+old_nick.trim()+" "+argument, user.getRoom());
        }
        break;
      }

      case "/join":
      //System.out.println("Join command");
      if(user.getState().equals("init")){
        messageUser( sc, "ERROR\n");
        break;
      }
      else {
        if(user.getState().equals("outside")){
          messageRoom( sc, "JOINED "+user.getNick(), argument );
          user.setState("inside");
          user.setRoom(argument);
          messageUser( sc, "Entrou na sala OK\n");
          break;
        }
        else {
          messageRoom( sc, "LEFT "+user.getNick(), old_room );
          messageRoom( sc, "JOINED "+user.getNick(), argument );
          user.setRoom(argument);
          user.setState("inside");
          messageUser(sc, "Mudou de sala OK\n");
          break;
        }
      }

      case "/leave":
      //System.out.println("Leave command");
      if(user.getState().equals("init")){
        messageUser( sc, "ERROR\n");
        break;
      }
      else {
        if(user.getState().equals("outside")){
          messageUser( sc, "ERROR\n");
          break;
        }
        else {
          user.setRoom("");
          user.setState("outside");
          messageRoom( sc, "LEFT "+user.getNick(), old_room );
          messageUser(sc, "Saiu da sala OK\n");
          break;
        }
      }
      case "/bye":
      //System.out.println("Bye command");
      if(user.getState().equals("inside")){
        messageRoom( sc, "LEFT "+user.getNick(), old_room);
        users.remove( sc );
        messageUser( sc, "BYE\n");
        return false;
      }
      else{
      	users.remove( sc );
        messageUser( sc, "BYE\n");
        return false;
      }
      
      case "/priv":
      boolean user_exists = false;
      for( SocketChannel sc_receiver: users.keySet()){
        if( argument.equals(users.get( sc_receiver ).getNick().trim() ) && !argument.equals( user.getNick().trim() ) ){
          user_exists = true;
          messageUser( sc_receiver , "PRIVATE "+user.getNick().trim()+" "+message);
          messageUser( sc , "PRIVATE "+user.getNick().trim()+" "+message);
          return true;
        }
      }
      messageUser( sc, "ERROR\n" );
      break;

    }
    return true;
  }


}
