package edu.buffalo.cse.cse486586.groupmessenger1;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private String[] portNumbers={"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    public Uri mUri=null;
    static int sequenceNo=-1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
       mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);
       TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
             ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {



            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                tv.append(msg + "\t\n");
                tv.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket client=null;
            BufferedReader in=null;
            while(true) {
                try {
                    client = serverSocket.accept();
                    if (client.isConnected()) {
                        Log.d("server", "resumed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Exception occurred in accepting Connection");
                }
                //http://stackoverflow.com/questions/16608878/read-data-from-a-java-socket-Taken the code to read from a socket.
                if (client != null) try {
                    in = new BufferedReader(
                            new InputStreamReader(client.getInputStream()));
                } catch (IOException e) {
                    Log.e(TAG,"Exception occurred in creating Inputstream");
                    e.printStackTrace();
                }
                if (in != null) {
                    String strings = null;
                    try {
                        strings = in.readLine();
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG,"Exception occurred in reading Inputstream");
                    }

                    Log.d(TAG, strings);
                    publishProgress(strings);
                }
            }

        }

        protected void onProgressUpdate(String...strings) {

          /*   * The following code displays what is received in doInBackground().*/
            //The string received from the server and the sequence number are sent to the content provider fo creating the file.
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            sequenceNo++;
            ContentValues values = new ContentValues();
            values.put(KEY_FIELD, String.valueOf(sequenceNo));
            values.put(VALUE_FIELD, strReceived);
            new GroupMessengerProvider().insert(mUri,values);
        }
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {
        // I used the PrintStream code from :http://www.tutorialspoint.com/javaexamples/net_singleuser.htm
        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String msgToSend = msgs[0];
                //As there is Multicast we iterate over all the ports including the one from which the message is sent.
                for(String s:portNumbers) {
                    Log.d(TAG, msgToSend);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(msgToSend);
                        ps.flush();
                        ps.close();
                    }
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}
