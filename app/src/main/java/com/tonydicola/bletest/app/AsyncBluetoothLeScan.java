package com.tonydicola.bletest.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AsyncBluetoothLeScan implements BluetoothAdapter.LeScanCallback {

    // Store result of scan for returning in a promise.
    public class ScanResult {
        public final BluetoothDevice device;
        public final int rssi;
        public final byte[] bytes;

        public ScanResult(BluetoothDevice device, int rssi, byte[] bytes) {
            this.device = device;
            this.rssi = rssi;
            this.bytes = bytes;
        }

        // UUID filtering in android 4.3 and 4.4 is broken.  See:
        //  http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation
        // This is a useful workaround to manually parse advertisement data.
        public List<UUID> parseUUIDs() {
            List<UUID> uuids = new ArrayList<UUID>();

            int offset = 0;
            while (offset < (bytes.length - 2)) {
                int len = bytes[offset++];
                if (len == 0)
                    break;

                int type = bytes[offset++];
                switch (type) {
                    case 0x02: // Partial list of 16-bit UUIDs
                    case 0x03: // Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = bytes[offset++];
                            uuid16 += (bytes[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    case 0x06:// Partial list of 128-bit UUIDs
                    case 0x07:// Complete list of 128-bit UUIDs
                        // Loop through the advertised 128-bit UUID's.
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                ByteBuffer buffer = ByteBuffer.wrap(bytes, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                                long mostSignificantBit = buffer.getLong();
                                long leastSignificantBit = buffer.getLong();
                                uuids.add(new UUID(leastSignificantBit, mostSignificantBit));
                            } catch (IndexOutOfBoundsException e) {
                                // Defensive programming.
                                //Log.e(LOG_TAG, e.toString());
                                continue;
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 15;
                                len -= 16;
                            }
                        }
                        break;
                    default:
                        offset += (len - 1);
                        break;
                }
            }
            return uuids;
        }
    }

    private BluetoothAdapter adapter;
    private DeferredObject<Void, Void, ScanResult> scan;
    private UUID[] filter;

    public AsyncBluetoothLeScan(BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    // Start scanning for the specified UUIDs (or null for no filtering).  The returned promise will
    // notify of discovered devices through its progress notification.
    public Promise<Void, Void, ScanResult> start(UUID[] uuid) {
        if (scan != null && scan.isPending()) {
            scan.resolve(null);
        }
        scan = new DeferredObject<Void, Void, ScanResult>();
        filter = uuid;
        if (!adapter.startLeScan(uuid, this)) {
            scan.reject(null);
        }
        return scan.promise();
    }

    // Helpful overrides for no filter or a single filter.

    public Promise<Void, Void, ScanResult> start() {
        return start((UUID[])null);
    }

    public Promise<Void, Void, ScanResult> start(UUID uuid) {
        return start(new UUID[] { uuid });
    }

    // Stop the in progress scan.
    public void stop() {
        adapter.stopLeScan(this);
        if (scan != null && scan.isPending()) {
            scan.resolve(null);
        }
    }

    @Override
    public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
        // Notify deferred if scan is in progress.
        if (scan != null && scan.isPending()) {
            ScanResult result = new ScanResult(bluetoothDevice, i, bytes);
            if (filter == null) {
                // No filtering, notify of the new result.
                scan.notify(result);
            }
            else {
                // Manually filter service UUIDs if filtering is enabled (workaround for bug in 4.3/4.4)
                List<UUID> serviceUUIDs = result.parseUUIDs();
                for (UUID uuid : filter) {
                    if (serviceUUIDs.contains(uuid)) {
                        scan.notify(result);
                        return;
                    }
                }
            }
        }
    }
}
