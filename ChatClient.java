import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    static private final ByteBuffer outBuffer = ByteBuffer.allocate( 16384 );
    static private final ByteBuffer inBuffer = ByteBuffer.allocate( 16384 );
    static private SocketChannel sc = null;

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();


    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

       Thread receiver = new Thread();
       receiver.run();

       InetSocketAddress isa = new InetSocketAddress(server, port);
       sc = SocketChannel.open(isa);


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        message = message + "\n";
        outBuffer.clear();
        outBuffer.put( message.getBytes() );
        outBuffer.flip();

        while (outBuffer.hasRemaining()) {
               sc.write(outBuffer);
           }

    }



    // Método principal do objecto
    public void run() throws IOException {
        while(true){
          inBuffer.clear();
          sc.read( inBuffer );
          inBuffer.flip();

          String message = decoder.decode(inBuffer).toString();
          String[] message_split = message.split(" ");

          if (message_split[0].equals("JOINED") && message_split.length == 2) {
              printMessage(message_split[1].trim() + " juntou-se a sala.\n");
            }
          else if (message_split[0].equals("LEFT") && message_split.length == 2) {
              printMessage(message_split[1].trim() + " saiu da sala.\n");
            }
          else if (message_split[0].equals("NEWNICK") && message_split.length == 3) {
              printMessage(message_split[1] + " mudou o nome para " + message_split[2]);
            }
          else if (message_split[0].equals("MESSAGE") && message_split.length > 2) {
              printMessage(message_split[1] + ": " + message.substring(message_split[0].length()+message_split[1].length()+2));
            }
          else if (message_split[0].equals("PRIVATE") && message_split.length > 2) {
              printMessage("Privada "+message_split[1] + ": " + message.substring(message_split[0].length()+message_split[1].length()+2));
              }
          else {
              printMessage(message);
            }
        }
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
