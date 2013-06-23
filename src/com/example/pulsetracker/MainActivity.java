package com.example.pulsetracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.example.pulsetracker.PulseView.PulseThread;

public class MainActivity extends Activity {

	private PulseThread mPulseThread;
	private PulseView mPulseView;
	
    TextView myLabel;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    
    double data;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
        mPulseView = (PulseView) findViewById(R.id.surfaceView1);
        mPulseThread = mPulseView.getThread();
        
        myLabel = (TextView)findViewById(R.id.textView1);
/*        
        mAcceptThread = new AcceptThread((Context)this, mDataThread);
        mAcceptThread.start();
        */
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	   
	@Override
	protected void onPause() {
		super.onPause();
		mPulseView.getThread().doPause(); // pause game when Activity pauses      
	}
	
	public void startTimer(View view)
	{
		//Chronometer chron = (Chronometer) findViewById(R.id.chronometer1);
		//chron.start();
		mPulseThread.doStart();
		
		try {
			findBT();
			openBT();
		} catch(IOException e) {}
	}

	public void stopTimer(View view)
	{
		//Chronometer chron = (Chronometer) findViewById(R.id.chronometer1);
		//chron.stop();
		mPulseThread.doPause();
		
		try {
			closeBT();
		} catch(IOException e) {}
	}
	
    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            myLabel.setText("No bluetooth adapter available");
        }
        
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }
        
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
            	mmDevice = device;
            	myLabel.setText(device.getName());
            	/*
                if(device.getName().equals("Monica Skoge's MacBook Pro")) 
                {
                    mmDevice = device;
                    myLabel.setText("Right device");
                    break;
                } else {
                	myLabel.setText("Wrong device");
                }
                */
            	break;
            }
        } else {
        	myLabel.setText("No device");
        }
        //myLabel.setText("Bluetooth Device Found");
    }
    
    void openBT() throws IOException
    {
    	if(mmDevice==null) return;

    	UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
    	mmSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(uuid);   
    	
    	if(mmSocket.isConnected()) {
    		myLabel.setText("Already connected!");
    	} else { 
    		mBluetoothAdapter.cancelDiscovery();
    		try {
    			mmSocket.connect();
    		} catch(IOException connectException) {
    			try{
    				mmSocket.close();
    			} catch(IOException closeException) {}
    			return;
    		}
    		//myLabel.setText("Not connected.");
    	}
        
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();
        
        beginListenForData();
        
        myLabel.setText("Bluetooth Opened");
    	
    }
    
    void beginListenForData()
    {
        final Handler handler = new Handler();
        final Handler viewHandler = new Handler(mPulseThread);
        final byte delimiter = 10; //This is the ASCII code for a newline character
        
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {   
            	int tmp = (int)'s';
            	try{
            		mmOutputStream.write(tmp);
            		mmOutputStream.flush();
            	} catch(IOException e) {
            		Log.i("PulseTracker", "Failed to write");
            		return;
            	}

            	SystemClock.sleep(1000);
            	tmp = (int)'s';
            	try{
            		mmOutputStream.write(tmp);
            		mmOutputStream.flush();
            	} catch(IOException e) {
            		Log.i("PulseTracker", "Failed to write");
            		return;
            	}
            	SystemClock.sleep(1000);
            	tmp = (int)'g';
            	try{
            		mmOutputStream.write(tmp);
            	} catch(IOException e) {
            		Log.i("PulseTracker", "Failed to write");
            		return;
            	}
            	int num_read = 0;
            	double totval = 0.;
               while(!Thread.currentThread().isInterrupted() && !stopWorker)
               {
                    try 
                    {
                        int bytesAvailable = mmInputStream.available();                        
                        if(bytesAvailable > 0)
                        {
                        	Log.i("PulseTracker", "Got data");
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                	//Log.i("MyActivity","Received String!");
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    
                                    //Log.i("MyActivity",data);
                                    num_read++;
                                    long lval;
                                	try {
                                		lval = Long.parseLong(data.trim(), 16);
                                	} catch(NumberFormatException e) {
                                		Log.i("Format Exception", data);
                                		lval = 0L;
                                	}
                                	totval += 3.6*(65536.-lval)/(10.*65536.);
                                	if(num_read==10) {
                                		final String fdat = String.valueOf(totval);
                                		
                                		handler.post(new Runnable()
                                		{
                                			public void run()
                                			{
                                				myLabel.setText(fdat);
                                			}
                                		});
                                		
                                		Bundle bnd = new Bundle();
                                		bnd.putDouble("PulseData", totval);
                                		Message m = new Message();
                                		m.setData(bnd);
                                		viewHandler.sendMessage(m);
                                		
                                		totval = 0.;
                                		num_read = 0;
                                	}
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } 
                    catch (IOException ex) 
                    {
                        stopWorker = true;
                    }
               }
            }
        });

        workerThread.start();
    }
    
    void sendData() throws IOException
    {
    }
    
    void closeBT() throws IOException
    {
    	int tmp = (int)'s';
    	try{
    		mmOutputStream.write(tmp);
    		mmOutputStream.flush();
    	} catch(IOException e) {
    		Log.i("PulseTracker", "Failed to write");
    		return;
    	}
    	SystemClock.sleep(1000);
    	tmp = (int)'s';
    	try{
    		mmOutputStream.write(tmp);
    		mmOutputStream.flush();
    	} catch(IOException e) {
    		Log.i("PulseTracker", "Failed to write");
    		return;
    	}
    	
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth Closed");
    }

}
