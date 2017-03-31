package com.qlgc.sendsinusoidal2;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private boolean FreqencyVaryFlag = false;
    private boolean FrequencyAddFlag = true;
    private float Frequency  = 440.0f;// generated sinusoidal signal
    final private float StartFrequency  = 400.0f;// generated sinusoidal signal
    final private float EndFrequency  = 1600.0f;// generated sinusoidal signal
    final private float FrequencyAdd = 12.0f*1.0f;
    private String IP_ADDRESS = "10.64.28.51";
    private boolean status = true;

    private TextView textView;
    private EditText myEditTextHz, myEditTextIP;

    private float sampleRate = 48000.0f;
    private int startTimeIndex = 0;
    private int N = (int)(sampleRate*0.04); // Each frame have this many samples
    private int NFrameTimer,NFrameSocket;


    private boolean playMode = true;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private AudioTrack player;
    private int playBufSize = AudioTrack.getMinBufferSize((int)sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat);


    Timer timer;
    TimerTask timerTask;

    //we are going to use a handler to be able to run in our TimerTask
    final Handler handler = new Handler();

    private byte[] buffer = new byte[N*2];
    private float[] piftIndex = new float[N];
    private float[] pIndex = new float[N];
    private float step = 0; //Frequency*N/sampleRate*Math.PI*2;
    private short[] shortBuffer = new short[N];

    private short[] shortBufferPlay1 = new short[N];
    private short[] shortBufferPlay2 = new short[N];
    private int playFlag=1; // which buffer to play


    private PowerManager.WakeLock w1;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = (Button) findViewById(R.id.finishButton);
        btn.setEnabled(false);


        textView = (TextView) findViewById(R.id.textView);
        myEditTextHz = (EditText) findViewById(R.id.SignalFrequency);
        myEditTextIP = (EditText) findViewById(R.id.ipAddress);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        w1 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
    }

    @Override
    protected void onPause(){
        super.onPause();
        w1.release();
    }

    @Override
    protected void onResume(){
        super.onResume();
        w1.acquire();
    }

    /**
     * Called when the user clicks the Start button
     */
    public void startRecording(View view) {
        // Do something in response
        textView.setText("Start ......");

        status = true;
        NFrameTimer = 0;
        NFrameSocket = 0;
        startTimer();
        startStreaming();

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setEnabled(false);
        EditText EditTextField = (EditText) findViewById(R.id.ipAddress);
        EditTextField.setEnabled(false);
        Button finishButton = (Button) findViewById(R.id.finishButton);
        finishButton.setEnabled(true);

        w1.acquire();

    }

    public void finishRecording(View view) {
        // Do something in response
        textView.setText("Finish ......");

        status = false;


        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (playMode) {
            player.flush();
            player.stop();
            player.release();
            Log.d("VS","Player released");
        }

//        socket.close();
//        Log.d("VS","Socket released");

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setEnabled(true);
        EditText EditTextField = (EditText) findViewById(R.id.ipAddress);
        EditTextField.setEnabled(true);
        Button finishButton = (Button) findViewById(R.id.finishButton);
        finishButton.setEnabled(false);

        w1.release();
    }

    /*Exit the Apps*/
    public void exitApp(View view) {

//        wakeLock.release();
        finish();
        System.exit(0);
    }


    public void startStreaming() {


        for(int i=0;i<N;i++){
            piftIndex[i] = Frequency*(i+1)/sampleRate*((float) Math.PI)*2;
        }
        Thread udpSendThread = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    Thread.sleep(1000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }

                try {
                    // create new UDP socket
                    final DatagramSocket socket  = new DatagramSocket();// <-- create an unbound socket first


                    Log.d("UDP", "Socket Created");

                    // First get the Frequency and IP address from the text field 10.64.8.78
                    handler.post(new Runnable(){
                        public void run() {
                            String tmp = myEditTextHz.getText().toString();
                            Pattern p = Pattern.compile("[0-9]*\\.?[0-9]+");
                            Matcher m = p.matcher(tmp);
                            if (m.find()) {
                                Frequency = Float.parseFloat(m.group());
                                FreqencyVaryFlag = false;
                                step = 0; // piftIndex[N-1]; // x in sin(x) for the last element in the previous frame
                                for(int i=0;i<N;i++){
                                    piftIndex[i] = Frequency*(i+1)/sampleRate*((float) Math.PI)*2;
                                }
                                Toast.makeText(getApplicationContext(), "The frequency is: " + Float.toString(Frequency), Toast.LENGTH_LONG).show();
                            } else {
                                FreqencyVaryFlag = true;
                                Frequency = StartFrequency;
                                Toast.makeText(getApplicationContext(), "The frequency is varying from " + Float.toString(StartFrequency)+ " to " + Float.toString(EndFrequency), Toast.LENGTH_LONG).show();
                            }
//                            IP_ADDRESS = myEditTextIP.getText().toString();

                            // TODO add some grammar check to the IP_ADDRESS
                            Log.w("UDP","IP address " + IP_ADDRESS);
                        }
                    });

                    // get server name

                    final InetAddress serverAddr = InetAddress.getByName(IP_ADDRESS);
                    Log.w("UDP", "Connecting "+IP_ADDRESS);

                    while (status) {
                        while(NFrameSocket<NFrameTimer){
                            NFrameSocket = NFrameTimer;
                            //step = pIndex[N-1];

                            if (FreqencyVaryFlag) {
                                if (FrequencyAddFlag) {
                                    Frequency += FrequencyAdd;
                                } else {
                                    Frequency -= FrequencyAdd;
                                }
                                if (Frequency > EndFrequency) {
                                    FrequencyAddFlag = false;
                                }
                                if (Frequency < StartFrequency) {
                                    FrequencyAddFlag = true;
                                }

                                for (int i = 0; i < N; i++) {
                                    piftIndex[i] = (float) Frequency * (i + 1) / sampleRate * ((float) Math.PI) * 2;
                                }

                            }

                            for(int i=0;i<N;i++){
                                pIndex[i] = step+piftIndex[i];
                                double tmp = 0.05*Math.sin(pIndex[i]);
                                shortBuffer[i] = (short)(tmp*32768);
                            }

                            step = pIndex[N-1]; // the last element :)
                            if ( step> (float) (1000000*2*Math.PI) ){
                                step -= (float) (1000000*2*Math.PI);
                            }


                            if (playFlag==1) {
                                System.arraycopy(shortBuffer, 0, shortBufferPlay2, 0, N);
                                playFlag=2;
                            } else {
                                System.arraycopy(shortBuffer, 0, shortBufferPlay1, 0, N);
                                playFlag=1;
                            }

                            // We did some changes here
                            // The first one will be the Frame Number
                            shortBuffer[0]= (short) NFrameSocket;

                            // short to byte
                            byte byte1, byte2;
                            for (int i=0;i<N;i++) {
                                byte1 = (byte) (shortBuffer[i]&0xFF); // the low byte
                                byte2 = (byte) ((shortBuffer[i]>>8)&0xFF); // the high byte
                                buffer[i*2] = byte1;
                                buffer[i*2+1] = byte2;
                            }


                            // create a UDP packet with data and its destination ip & port
                            DatagramPacket packet = new DatagramPacket(buffer, 2*N, serverAddr, 5001);
                            Log.w("UDP", "C: Sending the current frame");

                            try {
                                // send the UDP packet
                                socket.send(packet);
                                //Toast.makeText(getApplicationContext(),"MeawMeaw",Toast.LENGTH_LONG).show();
                                handler.post(new Runnable(){
                                    @Override
                                    public void run() {
                                        //textView.setText(Arrays.toString(EngeryBuffer));
                                        textView.setText(Integer.toString(NFrameSocket));
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.w("UDP", "C: Sending just failed");
                            }

                        }

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    Log.w("UDP", "C: Error", e);
                    //e.printStackTrace();
                }

            }
        });

        // start the streaming thread
        udpSendThread.start();

    }



    public void startTimer() {

        if (playMode) {
            player = new AudioTrack(AudioManager.STREAM_MUSIC,
                    (int) sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    playBufSize,
                    AudioTrack.MODE_STREAM);
            player.setPlaybackRate((int) sampleRate);
            Log.w("VS", "Player initialized");
            player.play();
            //writeBuffer(); // started another thread to buffer data to the player
        }


        //set a new Timer
        timer = new Timer();
        //initialize the TimerTask's job
        timerTask = new TimerTask() {
            public void run() {
                // The following works for only API 23 and above
//                if (playMode) {
//                    if (playFlag == 1) {
//                        player.write(shortBufferPlay1, 0, N, AudioTrack.WRITE_NON_BLOCKING);
//                    } else {
//                        player.write(shortBufferPlay2, 0, N, AudioTrack.WRITE_NON_BLOCKING);
//                    }
//                }

                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {

                        handler.post(new Runnable(){
                            @Override
                            public void run() {
                                //textView.setText(Integer.toString(NFrameTimer));
                            }
                        });

                        NFrameTimer++;

                    }
                });
            }
        };

        //schedule the timer, after the first 1000ms the TimerTask will run every 40ms
        timer.schedule(timerTask, 3000, 40); //

    }



}
