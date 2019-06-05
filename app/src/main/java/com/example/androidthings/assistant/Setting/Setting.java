package com.example.androidthings.assistant.Setting;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.androidthings.assistant.R;
import com.example.androidthings.assistant.wifi.WifiMenu;

import java.util.List;


/**
 * Created by i_hfuhsu on 2017/9/19.
 */

public class Setting extends Activity {
    String TAG=Setting.class.getName();
    private List<ResolveInfo> apps;
    GridView gridView;
    AppsAdapter appsAdapter;
    TextView TextView1;


    private float touchX;
    private float touchY;

    public static final int addCount=2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting);

        apps=loadApps();
        Log.d(TAG," app: size "+apps.size());
        for(ResolveInfo app:apps){
            Log.d(TAG,"app:"+app.activityInfo.packageName);
            if(app.activityInfo.packageName.isEmpty()){
                Log.d(TAG,"remove app");
                apps.remove(app);
            }
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        TextView1 = (TextView)findViewById(R.id.metrics);
        TextView1.setText( "SCREEN SIZE:"+metrics.widthPixels+" X "+metrics.heightPixels);
        TextView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(),"App creat by Lyon & Ellis & Kevin!",Toast.LENGTH_LONG).show();
            }
        });

        TextView TextView2 = (TextView)findViewById(R.id.ipAddress);
        TextView2.setText( "" + getLocalIpAddress(this).replace("\n",", "));

        gridView = (GridView)findViewById(R.id.apps_list);
        if(apps!=null) {
            Log.e(TAG,"Apps size:"+apps.size());
            appsAdapter = new AppsAdapter(Setting.this,apps);
            gridView.setAdapter(appsAdapter);
        }
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                if(i>=appsAdapter.getCount())
                    return;

                if(i==1){
                    Intent intent = new Intent(Setting.this, WifiMenu.class);
                    startActivity(intent);
                }else if(i==0) {
                    finish();
                }
                else{
                    Intent intent = new Intent();
                    ResolveInfo resolveInfo = apps.get(i-addCount);
                    intent.setClassName(resolveInfo.activityInfo.applicationInfo.packageName,
                            resolveInfo.activityInfo.name);
                    startActivity(intent);
                }
            }
        });



    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");
        TextView TextView2 = (TextView)findViewById(R.id.ipAddress);
        TextView2.setText( "" + getLocalIpAddress(this).replace("\n",", "));

        apps=loadApps();
        appsAdapter.notifyDataSetChanged();

    }

    private List<ResolveInfo> loadApps() {
        List<ResolveInfo> apps;
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        new ImageView(Setting.this);

        apps = getPackageManager().queryIntentActivities(mainIntent, 0);

        return apps;
    }

    @SuppressLint("WifiManagerLeak")
    public String getLocalIpAddress(Context context) {

        String ip =  "no connect wifi!";
        WifiManager wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        ip=String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));

        Log.i(TAG, "***** IP="+ ip);


        return "Wifi:"+ip+"\n ("+wifiInf.getSSID().toString()+") connected\n"+wifiInf.getBSSID();
    }


















    @Override
    // 利用 MotionEvent 處理觸控程序
    public boolean onTouchEvent(MotionEvent event) {
        Log.d("kevin","onTouchEvent");
        touchX = event.getX();       // 觸控的 X 軸位置
        touchY = event.getY() - 50;  // 觸控的 Y 軸位置

        // 判斷觸控動作
        switch( event.getAction() ) {

            case MotionEvent.ACTION_DOWN:  // 按下
                Log.d("kevin","MotionEvent.ACTION_DOWN");
                // 設定 TextView 內容, 大小, 位置
                TextView1.setText("X: " + touchX + ", Y: " + touchY + ", 按下");
				/*
				txtSpeechInput.setLayoutParams( new AbsoluteLayout.LayoutParams( tvWidth
						, tvHeight
						, (int)touchX
						, (int)touchY
				));
				*/
                break;

            case MotionEvent.ACTION_MOVE:  // 拖曳移動
                Log.d("kevin","MotionEvent.ACTION_MOVE");
                // 設定 TextView 內容, 大小, 位置
                TextView1.setText("X: " + touchX + ", Y: " + touchY + ", 拖曳移動");
				/*
				txtSpeechInput.setLayoutParams( new AbsoluteLayout.LayoutParams( tvWidth
						, tvHeight
						, (int)touchX
						, (int)touchY
				));
				*/
                break;

            case MotionEvent.ACTION_UP:  // 放開
                Log.d("kevin","MotionEvent.ACTION_UP");
                // 設定 TextView 內容
                TextView1.setText("X: " + touchX + ", Y: " + touchY + ", 放開");
                break;
        }

        // TODO Auto-generated method stub
        return super.onTouchEvent(event);
    }
}
