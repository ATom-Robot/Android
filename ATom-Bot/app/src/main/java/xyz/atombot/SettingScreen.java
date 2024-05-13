package xyz.atombot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class SettingScreen extends Activity implements View.OnClickListener {
    private Button connected_btn, back_btn;
    private Switch stream_sw;
    public EditText ipddress_edit, tcp_port_edit;

    private boolean connect_flag = false;

    SharedPreferences sharedPreferences_switch = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_screen);

        connected_btn = findViewById(R.id.connect_btn);
        connected_btn.setOnClickListener(this);

        back_btn = findViewById(R.id.back_btn);
        back_btn.setOnClickListener(this);

        ipddress_edit = findViewById(R.id.ipaddress);
        tcp_port_edit = findViewById(R.id.tcp_port);

        sharedPreferences_switch = getSharedPreferences("switch_state", Context.MODE_PRIVATE);

        stream_sw = findViewById(R.id.stream_switch);
        stream_sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                // 保存选择状态
                SharedPreferences.Editor editor = sharedPreferences_switch.edit();
                editor.putBoolean("switch_state", stream_sw.isChecked());
                editor.apply();
            }
        });

        boolean buttonState = sharedPreferences_switch.getBoolean("switch_state", false);
        stream_sw.setChecked(buttonState);

        Intent intent = getIntent();
        if (intent.hasExtra("connected")) {
            Bundle bundle = intent.getExtras();
            connect_flag = bundle.getBoolean("connected");
        }
        if (connect_flag)
            connected_btn.setBackgroundColor(Color.rgb(153,204,102));
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back_btn:
                if (connect_flag) {
                    Intent intent = new Intent(SettingScreen.this, MainActivity.class);
                    intent.putExtra("open_stream", stream_sw.isChecked());
                    intent.putExtra("ipaddress", ipddress_edit.getText().toString());
                    intent.putExtra("tcp_port", tcp_port_edit.getText().toString());
                    intent.putExtra("connected", connect_flag);
                    startActivity(intent);
                } else {
                    Toast.makeText(getApplicationContext(), "连接失败，请检查IP地址", Toast.LENGTH_SHORT).show();
                    connect_flag = false;
                }
                break;
            case R.id.connect_btn:
                new Thread() {
                    public void run() {
                        try {
                            if (ping(ipddress_edit.getText().toString())) {
                                connect_flag = true;
                                connected_btn.setBackgroundColor(Color.rgb(153,204,102));
                            } else {
                                connect_flag = false;
                                connected_btn.setBackgroundColor(Color.rgb(204, 51, 51));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
                break;
            default:
                break;
        }
    }

    public static boolean ping(String ipAddress) throws Exception {
        int timeOut = 1000;
        System.out.println(ipAddress);
        return InetAddress.getByName(ipAddress).isReachable(timeOut);
    }
}