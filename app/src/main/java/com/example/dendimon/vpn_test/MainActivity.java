package com.example.dendimon.vpn_test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 0x0f;
    private boolean waitingForVPNStart;

    // ????
    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(LocalVPNService.BROADCAST_VPN_STATE.equals(intent.getAction())){
                if(intent.getBooleanExtra("running",false))waitingForVPNStart=false;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final Button vpnButton = (Button)findViewById(R.id.vpn);
        vpnButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick (View v){
                startVPN();
            }
        });
        waitingForVPNStart = false;

        // ???
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    private  void startVPN(){

        // ?????
        Intent vpnIntent = VpnService.prepare(this);
        if(vpnIntent != null)
            startActivityForResult(vpnIntent,  VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    @Override
    protected void onActivityResult (int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK){
            waitingForVPNStart = true;
            startService(new Intent(this,LocalVPNService.class));
            enableButton (false);
        }
    }


    //?????????
    @Override
    protected void onResume(){
        super.onResume();
        enableButton(!waitingForVPNStart && !LocalVPNService.isRunning());
    }

    private void enableButton(boolean enable){
        final Button vpnButton = (Button) findViewById(R.id.vpn);
        if(enable){
            vpnButton.setEnabled(true );
            vpnButton.setText("Start VPN");
        }
        else {
            vpnButton.setEnabled(false);
            vpnButton.setText("Stop from Notification Bar");
        }
    }





    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
