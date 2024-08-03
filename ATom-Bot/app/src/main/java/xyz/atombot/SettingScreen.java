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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class SettingScreen extends Activity implements View.OnClickListener {
    private Button connected_btn, back_btn, reset_btn;
    private Button prev_btn, next_btn;
    private Switch stream_sw;
    public EditText ipddress_edit, tcp_port_edit;

    private boolean connect_flag = false;
    private boolean setting_flag = false;

    SharedPreferences sharedPreferences_switch = null;
    SharedPreferences sharedPreferences_ipdaddress = null;

    private byte[] Dataframe = {    //数据帧
            (byte) 0xAA, (byte) 0XAB,
            (byte) 0x00
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_screen);

        connected_btn = findViewById(R.id.connect_btn);
        connected_btn.setOnClickListener(this);

        back_btn = findViewById(R.id.back_btn);
        back_btn.setOnClickListener(this);

        reset_btn = findViewById(R.id.reset_btn);
        reset_btn.setOnClickListener(this);

        prev_btn = findViewById(R.id.set_prev_btn);
        prev_btn.setOnClickListener(this);

        next_btn = findViewById(R.id.set_next_btn);
        next_btn.setOnClickListener(this);

        ipddress_edit = findViewById(R.id.ipaddress);
        tcp_port_edit = findViewById(R.id.tcp_port);

        sharedPreferences_switch = getSharedPreferences("switch_state", Context.MODE_PRIVATE);
        sharedPreferences_ipdaddress = getSharedPreferences("ipddress", Context.MODE_PRIVATE);

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
            connected_btn.setBackgroundColor(Color.rgb(153, 204, 102));

        String ipddress = sharedPreferences_ipdaddress.getString("ipddress", "192.168.0.1");
        ipddress_edit.setText(ipddress);
    }

    public void onStop() {
        super.onStop();
        // 关闭连接
        client.disconnect();
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
                            int port = Integer.parseInt(tcp_port_edit.getText().toString());
                            tcp_connect(ipddress_edit.getText().toString(), port);
                            Thread.sleep(500);
                            if (client.isConnected()) {
                                connect_flag = true;
                                connected_btn.setBackgroundColor(Color.rgb(153, 204, 102));
                                connected_btn.setText("设备已连接");
                                // 连接成功则写入参数
                                SharedPreferences.Editor editor = sharedPreferences_ipdaddress.edit();
                                editor.putString("ipddress", ipddress_edit.getText().toString());
                                editor.apply();
                            } else {
                                connect_flag = false;
                                connected_btn.setBackgroundColor(Color.rgb(204, 51, 51));
                                connected_btn.setText("设备断开连接");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
                break;
            case R.id.reset_btn:
                if (!client.isConnected()) {
                    Toast.makeText(getApplicationContext(), "未连接，请先连接设备", Toast.LENGTH_SHORT).show();
                    break;
                }
                Dataframe[2] = (byte) (0x01);
                tcp_sendStr(Dataframe);
                setting_flag = true;
                prev_btn.setVisibility(View.VISIBLE);
                next_btn.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), "设备重启后将进入初始模式", Toast.LENGTH_SHORT).show();
                break;
            case R.id.set_prev_btn:
                Dataframe[2] = (byte) (0x02);
                tcp_sendStr(Dataframe);
                break;
            case R.id.set_next_btn:
                Dataframe[2] = (byte) (0x03);
                tcp_sendStr(Dataframe);
                break;
            default:
                break;
        }
    }

    public final Handler tcp_handler = new Handler(Looper.getMainLooper());
    public final TcpClient client = new TcpClient() {
        @Override
        public void onConnect(SocketTransceiver transceiver) {
        }

        @Override
        public void onConnectFailed() {
            tcp_handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "tcp连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onReceive(SocketTransceiver transceiver, final byte[] s) {
            tcp_handler.post(new Runnable() {
                @Override
                public void run() {
                    StringBuilder hexBuilder = new StringBuilder();
                    for (byte b : s) {
                        hexBuilder.append(String.format("%02X ", b));
                    }
                    String hexString = hexBuilder.toString().trim();
                    // 打印十六进制字符串
//                    System.out.println(hexString);
                }
            });
        }

        @Override
        public void onDisconnect(SocketTransceiver transceiver) {
            Looper.prepare();
            Looper.loop();
        }
    };

    /**
     * 设置IP和端口地址,连接或断开
     */
    private void tcp_connect(String ipaddress, int port) {
        if (client.isConnected()) {
            // 断开连接
            client.disconnect();
        } else {
            try {
                client.connect(ipaddress, port);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口错误", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void tcp_sendStr(final byte send_data[]) {
        if (!client.isConnected())
            return;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(50);
                    client.getTransceiver().send(send_data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}