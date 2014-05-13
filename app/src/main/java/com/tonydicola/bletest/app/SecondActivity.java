package com.tonydicola.bletest.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import betterbluetoothle.services.UART;


public class SecondActivity extends Activity implements UART.Callback {

    // Main application state.
    private BTLEApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        app = (BTLEApp) getApplication();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Register for callback events if connected to device.
        if (app.uartDevice != null) {
            app.uartDevice.register(this);
        }
    }

    @Override
    public void connected(UART uart) {
        // Do nothing if connected event fires.
    }

    @Override
    public void disconnected(UART uart) {
        // Remove the device when it disconnects.
        app.uartDevice = null;
    }

    @Override
    public void available(UART uart) {
        // Clear buffer if data available event fires.
        uart.readAll();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister for callbacks.
        if (app.uartDevice != null) {
            app.uartDevice.unregister(this);
        }
    }

    public void disconnect(View view) {
        if (app.uartDevice != null) {
            app.uartDevice.disconnect();
        }
    }

    public void sendFoo(View view) {
        if (app.uartDevice != null) {
            app.uartDevice.write("foo");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.second, menu);
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
