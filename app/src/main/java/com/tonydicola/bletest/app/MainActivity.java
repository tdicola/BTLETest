package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.jdeferred.DoneCallback;
import org.jdeferred.ProgressCallback;

import betterbluetoothle.services.UART;

public class MainActivity extends Activity implements UART.Callback {

    // Main application state.
    private BTLEApp app;

    // UI elements
    private TextView messages;
    private EditText input;

    // BTLE state
    private BluetoothAdapter adapter;

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);

        adapter = BluetoothAdapter.getDefaultAdapter();

        app = (BTLEApp) getApplication();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        if (app.uartDevice == null) {
            // Not yet connected, search for first device with UART service.
            UART.findFirst(adapter, this, false)
                .done(new DoneCallback<UART>() {
                    @Override
                    public void onDone(UART result) {
                        // Device with UART service was found.
                        writeLine("UART device found.");
                        // Save reference to device and register for callback events.
                        app.uartDevice = result;
                        app.uartDevice.register(MainActivity.this);
                        // Connect to device.
                        result.connect();
                    }
                });
        }
        else {
            // Already connected, just register for callback events.
            app.uartDevice.register(this);
        }
    }

    @Override
    public void connected(UART uart) {
        // Connected to UART.
        writeLine("Connected!");
    }

    @Override
    public void disconnected(UART uart) {
        // Disconnected!
        writeLine("Disconnected!");
        // Remove the device when it disconnects.
        app.uartDevice = null;
    }

    @Override
    public void available(UART uart) {
        // Data is received from the UART.
        writeLine("Received: " + uart.readAllString());
    }

    // OnStop, called right before the activity loses foreground focus.
    @Override
    protected void onStop() {
        super.onStop();
        // Unregister for callbacks.
        if (app.uartDevice != null) {
            app.uartDevice.unregister(this);
        }
    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
        String message = input.getText().toString();
        if (app.uartDevice == null || message == null || message.isEmpty()) {
            // Do nothing if there is no device or message to send.
            return;
        }
        // Send the data.
        app.uartDevice.write(message);
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

    public void secondActivity(View view) {
        // Go to the second activity.
        startActivity(new Intent(this, SecondActivity.class));
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
