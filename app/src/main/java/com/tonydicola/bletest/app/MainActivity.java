package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.jdeferred.DoneCallback;
import org.jdeferred.ProgressCallback;

import betterbluetoothle.services.UART;

public class MainActivity extends Activity {

    // UI elements
    private TextView messages;
    private EditText input;

    // BTLE state
    private BluetoothAdapter adapter;
    private UART uart;

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);

        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Search for first device with UART service.
        UART.findFirst(adapter, this, false)
            .done(new DoneCallback<UART>() {
                @Override
                public void onDone(UART result) {
                    // Device with UART service was found.
                    writeLine("UART device found.");
                    connect(result);
                }
            });
    }

    // Connect to the specified UART device.
    private void connect(final UART device) {
        device.connect();
        device.whenConnected().done(new DoneCallback<Void>() {
            @Override
            public void onDone(Void result) {
                // Connected to UART.
                writeLine("Connected!");
                uart = device;
            }
        });
        device.whenDisconnected().done(new DoneCallback<Void>() {
            @Override
            public void onDone(Void result) {
                // Disconnected!
                writeLine("Disconnected!");
                uart = null;
            }
        });
        device.whenAvailable().progress(new ProgressCallback<Void>() {
            @Override
            public void onProgress(Void progress) {
                // Data is received from the UART.
                writeLine("Received: " + uart.readAllString());
            }
        });

    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();
        // Disconnect and remove the UART.
        if (uart != null) {
            uart.disconnect();
            uart = null;
        }
    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
        String message = input.getText().toString();
        if (uart == null || message == null || message.isEmpty()) {
            // Do nothing if there is no device or message to send.
            return;
        }
        // Send the data.
        uart.write(message);
    }

    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.append(text);
                messages.append("\n");
            }
        });
    }

    // Boilerplate code from the activity creation:

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
