package com.tonydicola.bletest.app;

import android.app.Application;

import betterbluetoothle.services.UART;

/**
 * Created by tony on 5/7/14.
 */
public class BTLEApp extends Application {


    // Global state can be stored in the application instance.
    public UART uartDevice;

}
