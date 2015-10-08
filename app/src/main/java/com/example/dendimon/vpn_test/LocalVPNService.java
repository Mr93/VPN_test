package com.example.dendimon.vpn_test;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Dendimon on 10/8/2015.
 */
public class LocalVPNService extends VpnService {
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2";//only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // intercept everything

    public static final String BROADCAST_VPN_STATE = "com.example.dendimon.vpn.test.VPN_STATE";
    private static boolean isRunning = false;
    private ParcelFileDescriptor vpnInterface = null;
    private PendingIntent pendingIntent; //dung cho service
    private ConcurrentLinkedQueue <Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue <Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue <ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    //?????/
    private Selector udpSelector;
    private Selector tcpSelector;

    @Override
    public void onCreate(){
        super.onCreate();
        isRunning=true;
        setupVPN();
        try
        {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
            deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
            deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
            networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            //???
            executorService = Executors.newFixedThreadPool(5);
            executorService.submit(new UDPInput(networkToDeviceQueue,udpSelector));
            executorService.submit(new UDPOutput(deviceToNetworkUDPQueue,udpSelector,this));
            executorService.submit(new TCPInput(networkToDeviceQueue,tcpSelector));
            executorService.submit(new TCPOutput(deviceToNetworkTCPQueue,networkToDeviceQueue,tcpSelector,this));
            executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                    deviceToNetworkUDPQueue,deviceToNetworkTCPQueue,networkToDeviceQueue));
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
            Log.i(TAG, "Started");
        }
        catch (IOException e)
        {
            //TODO: here and elsewhere, we should explicitly notify the user of any errors
            //and suggest that they stop the service, since we cant do it ourselves
            Log.e(TAG,"Error starting service",e);
            cleanup();
        }

    }

    private void setupVPN(){
        if(vpnInterface == null){
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS,32);
            builder.addRoute(VPN_ROUTE, 0);
            vpnInterface = builder.setSession("Local VPN").setConfigureIntent(pendingIntent).establish();

        }
    }

    @Override
    public int onStartCommand (Intent intent,int flags,int startId){return START_STICKY;}

    public static boolean isIsRunning(){return isRunning;}

    @Override
    public void onDestroy(){
        super.onDestroy();
        isRunning=false;
        executorService.shutdownNow();
        cleanup();
        Log.i(TAG,"Stopped");
    }

    private void cleanup(){
        deviceToNetworkTCPQueue=null;
        deviceToNetworkUDPQueue=null;
        networkToDeviceQueue=null;
        ByteBufferPool.clear();
        closeResources(udpSelector, tcpSelector, vpnInterface);
    }


    //ToDo: move this to a "utils" class for reuse
    private static void closeResources(Closeable...resources){
        for(Closeable resource : resources){
            try{
                resource.close();
            }
            catch (IOException e){
                //ignore
            }
        }
    }

    private static class VPNRunnable implements Runnable{

        private static final String TAG = VPNRunnable.class.getSimpleName();
        private FileDescriptor vpnFireDescriptor;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFireDescriptor, ConcurrentLinkedQueue<Packet>
                           deviceToNetworkUDPQueue,ConcurrentLinkedQueue<Packet>
                           deviceToNetworkTCPQueue, ConcurrentLinkedQueue<ByteBuffer>
                           networkToDeviceQueue){
            this.vpnFireDescriptor = vpnFireDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run(){
            Log.i(TAG,"Started");

            FileChannel vpnInput = new FileInputStream(vpnFireDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFireDescriptor).getChannel();

        }

    }


}
