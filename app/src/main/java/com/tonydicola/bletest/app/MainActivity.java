package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.jdeferred.DoneCallback;
import org.jdeferred.DonePipe;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;

import java.nio.charset.Charset;
import java.util.UUID;

public class MainActivity extends Activity {

    // UUIDs for UAT service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UI elements
    private TextView messages;
    private EditText input;

    // Bluetooth LE state
    private AsyncBluetoothGatt gatt;
    private AsyncBluetoothLeScan scanner;
    private AndroidDeferredManager dm;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);

        // Initialize the deferred manager to the default Android manager which will run callbacks
        // on the main UI thread by default.
        dm = new AndroidDeferredManager();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Kick off scan for devices.
        scanForDevice();
    }

    // Scan for devices with the UART service and connect to it.
    private void scanForDevice() {
        scanner = new AsyncBluetoothLeScan(BluetoothAdapter.getDefaultAdapter());
        dm.when(scanner.start(UART_UUID))
            // Progress callback will be called for every device that is found.
            .progress(new ProgressCallback<AsyncBluetoothLeScan.ScanResult>() {
                @Override
                public void onProgress(AsyncBluetoothLeScan.ScanResult progress) {
                    // Found a device, stop the scan and connect to the device.
                    writeLine("Found hardware: " + progress.device.getAddress());
                    scanner.stop();
                    connectToDevice(progress.device);
                }
            })
            // Done callback is called when the scan is stopped.
            .done(new DoneCallback<Void>() {
                @Override
                public void onDone(Void result) {
                    writeLine("Finished with scan!");
                }
            })
            // Fail callback is called if the scan fails for some reason.
            .fail(new FailCallback<Void>() {
                @Override
                public void onFail(Void result) {
                    writeLine("Failed to scan for devices!");
                }
            });
    }


    // Connect to the device, discover services, and setup the UART connection.
    private void connectToDevice(BluetoothDevice device) {
        gatt = new AsyncBluetoothGatt(device, getApplicationContext(), false);
        dm.when(gatt.connect())
            // Fail callback is called if the device connection fails for some reason.
            .fail(new FailCallback<Integer>() {
                @Override
                public void onFail(Integer result) {
                    writeLine("Connection attempt failed with status: " + result);
                }
            })
            // Once connected, start service discovery and switch to its promise for completion.
            .then(new DonePipe<Void, Void, Integer, Void>() {
                @Override
                public Promise<Void, Integer, Void> pipeDone(Void result) {
                    writeLine("Connected!");
                    return gatt.discoverServices();
                }
            })
            // Fail callback called when service discovery fails.
            .fail(new FailCallback<Integer>() {
                @Override
                public void onFail(Integer result) {
                    writeLine("Failed to discover services!");
                }
            })
            // Done callback is called when service discovery succeeds.
            .done(new DoneCallback<Void>() {
                @Override
                public void onDone(Void result) {
                    writeLine("Discovered services!");
                    setupUART();
                }
            });
        // Print a message if the device is disconnected.
        dm.when(gatt.disconnected())
            .done(new DoneCallback<Void>() {
                @Override
                public void onDone(Void result) {
                    writeLine("Disconnected!");
                    // Remove the tx characteristic so messages can't be sent.
                    tx = null;
                }
            });
    }

    // Setup the UI to send and receive messages.
    private void setupUART() {
        // Save reference to RX and TX characteristics.
        tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
        rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
        // Enable notifications for RX characteristic updates.
        dm.when(gatt.setCharacteristicNotification(rx, true))
            // Progress callback is called when the characteristic is updated.
            .progress(new ProgressCallback<BluetoothGattCharacteristic>() {
                @Override
                public void onProgress(BluetoothGattCharacteristic progress) {
                    // Display the received message.
                    writeLine("Received: " + progress.getStringValue(0));
                }
            })
            // Fail callback is called when notification enable fails.
            .fail(new FailCallback<Void>() {
                @Override
                public void onFail(Void result) {
                    writeLine("Failed to enable notifications for RX characteristic updates!");
                }
            })
            // Update the RX characteristic's client descriptor after notifications are enabled.
            .then(new DonePipe<Void, BluetoothGattDescriptor, Integer, Void>() {
                @Override
                public Promise<BluetoothGattDescriptor, Integer, Void> pipeDone(Void result) {
                    BluetoothGattDescriptor client = rx.getDescriptor(CLIENT_UUID);
                    client.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    return gatt.writeDescriptor(client);
                }
            })
            // Fail callback is called if the client descriptor update fails.
            .fail(new FailCallback<Integer>() {
                @Override
                public void onFail(Integer result) {
                    writeLine("Failed to update RX client descriptor!");
                }
            })
            // Done callback is called when the client descriptor is updated.
            .done(new DoneCallback<BluetoothGattDescriptor>() {
                @Override
                public void onDone(BluetoothGattDescriptor result) {
                    // Woo hoo, we are connected and finished!
                    writeLine("Ready!");
                }
            });
    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();
        if (scanner != null) {
            scanner.stop();
        }
        tx = null;
    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
        final String message = input.getText().toString();
        if (tx == null || message == null || message.isEmpty()) {
            // Do nothing if there is no device or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(message.getBytes(Charset.forName("UTF-8")));
        dm.when(gatt.writeCharacteristic(tx))
            .fail(new FailCallback<Integer>() {
                @Override
                public void onFail(Integer result) {
                    writeLine("Couldn't write TX characteristic!");
                }
            })
            .done(new DoneCallback<BluetoothGattCharacteristic>() {
                @Override
                public void onDone(BluetoothGattCharacteristic result) {
                    writeLine("Sent: " + message);
                }
            });
    }

    // Write some text to the messages text view.
    private void writeLine(CharSequence text) {
        messages.append(text);
        messages.append("\n");
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
