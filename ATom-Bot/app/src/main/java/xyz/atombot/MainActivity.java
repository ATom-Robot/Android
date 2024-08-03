package xyz.atombot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import xyz.atombot.fangxiang.FangxiangView;

public class MainActivity extends Activity implements View.OnClickListener,
        FangxiangView.OnJoystickChangeListener {

    private static final String TAG = "MainActivity::";
    private String robot_ipaddress = "";
    private String robot_tcpport = "";
    private boolean stream_flag = false;
    private boolean connected_flag = false;

    public static Handler http_handler;
    private ImageView imageView;
    private SeekBar joint_seekbar;
    private Button setting_btn;
    private TextView throttle_text;

    private FangxiangView ContolFangxiang;

    private int seekbar_value = 0;
    private int OPENSTREAM = 1;
    private Bitmap bitmap;

    // http 请求体
    HttpURLConnection httpURLConnection = null;

    public byte[] Dataframe1 = {    //数据帧
            /* Throttle ： 油门
               Yaw      ： 航向角*/
            (byte) 0xAA, (byte) 0xBB,
            (byte) 0x01, (byte) 0x08,
            (byte) 0x03, (byte) 0xE8,
            (byte) 0x04, (byte) 0xF8,
            (byte) 0x0D
    };

    public byte[] Dataframe2 = {    //数据帧
            /* Joint ： 角度*/
            (byte) 0xAA, (byte) 0xBC,
            (byte) 0x01, (byte) 0x08,
            (byte) 0x0D
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.camera_img);
        setting_btn = findViewById(R.id.setting_btn);
        ContolFangxiang = findViewById(R.id.Viewfangxiang);
        joint_seekbar = findViewById(R.id.mSeekBar);
        throttle_text = findViewById(R.id.throttle_text);
        ContolFangxiang.setOnJoystickChangeListener(this);
        joint_seekbar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        setting_btn.setOnClickListener(this);

        HandlerThread handlerThread = new HandlerThread("http");
        handlerThread.start();
        http_handler = new HttpHandler(handlerThread.getLooper());
    }

    @Override
    protected void onResume() {
        // 设置横屏
//        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
//        }
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        ContolFangxiang.invalidate();

        Bundle bundle = getIntent().getExtras();
        connected_flag = bundle.getBoolean("connected");
        stream_flag = bundle.getBoolean("open_stream");
        robot_ipaddress = bundle.getString("ipaddress");
        robot_tcpport = bundle.getString("tcp_port");
        if (stream_flag) {
            http_handler.sendEmptyMessage(OPENSTREAM);
            stream_flag = false;
        }
        // 连接TCP服务器
        tcp_connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        ContolFangxiang.invalidate();
    }

    @Override
    public void onStop() {
        super.onStop();
        // 关闭连接
        client.disconnect();

        if (httpURLConnection == null)
            return;
        try {
            if (httpURLConnection.getResponseCode() != -1) {
                httpURLConnection.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void init_OPenCV() {
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(getApplicationContext(), "OPenCV 初始化失败!!", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.setting_btn) {
            Intent intent = new Intent(MainActivity.this, SettingScreen.class);
            intent.putExtra("connected", connected_flag);
            startActivity(intent);
        }
    }

    private final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            seekbar_value = i;
            Dataframe2[3] = (byte) (seekbar_value);  //右移八位，高位在前
            tcp_sendStr(Dataframe2);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    public void setOnTouchListener(double xValue, double yValue, boolean temp) {
        pack_data(xValue, yValue, false);
        throttle_text.setText("x:" + (int) ((xValue + 1) * 100) + "   " + "y:" + (int) ((yValue + 1) * 100));
//        tcp_sendStr(Dataframe1);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void setOnMovedListener(final double xValue, final double yValue, boolean temp) {
        pack_data(xValue, yValue, false);
        throttle_text.setText("x:" + (int) ((xValue + 1) * 100) + "   " + "y:" + (int) ((yValue + 1) * 100));
        // 达到触发条件，执行发送操作
        tcp_sendStr(Dataframe1);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void setOnReleaseListener(final double xValue, final double yValue, boolean temp) {
        pack_data(xValue, yValue, false);
        throttle_text.setText("x:" + (int) ((xValue + 1) * 100) + "   " + "y:" + (int) ((yValue + 1) * 100));
        tcp_sendStr(Dataframe1);
    }

    @SuppressLint("SetTextI18n")
    private void pack_data(double xValue, double yValue, boolean joystickReleased) {
        int Yaw = (int) ((xValue + 1) * 100);
        int Throttle = (int) ((yValue + 1) * 100);

//        System.out.println("Throttle:" + Throttle + "  " + "Yaw:" + Yaw);

        Dataframe1[2] = (byte) ((Throttle & 0xff00) / 256);  //右移八位，高位在前
        Dataframe1[3] = (byte) (Throttle & 0x00ff);
        Dataframe1[4] = (byte) ((-Yaw & 0xff00) / 256);  //右移八位，高位在前
        Dataframe1[5] = (byte) (Yaw & 0x00ff);
    }

    private class HttpHandler extends Handler {
        public HttpHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == OPENSTREAM) {
                if (!robot_ipaddress.equals("")) {
                    show_video_stream(robot_ipaddress);
                } else
                    Toast.makeText(getApplicationContext(), "IP 地址不能为空", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //动态申请权限
    public static boolean isGrantExternalRW(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
            return false;
        }
        return true;
    }

    private void show_video_stream(String ipaddress) {
        String downloadUrl = "http://" + ipaddress + "/stream";
        @SuppressLint("SdCardPath") String savePath = "/sdcard/pic.jpg";

        File file = new File(savePath);
        if (file.exists()) {
            file.delete();
        }

        if (!isGrantExternalRW(this)) {
            return;
        }

        BufferedInputStream bufferedInputStream = null;
        FileOutputStream outputStream = null;
        try {
            URL url = new URL(downloadUrl);
            try {
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.setConnectTimeout(1000 * 5);
                httpURLConnection.setReadTimeout(1000 * 5);
                httpURLConnection.setDoInput(true);
                httpURLConnection.connect();

                if (httpURLConnection.getResponseCode() == 200) {
                    InputStream in = httpURLConnection.getInputStream();

                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader bufferedReader = new BufferedReader(isr);

                    String line;
                    int len;
                    byte[] buffer;

                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.contains("Content-Type:")) {
                            line = bufferedReader.readLine();
                            len = Integer.parseInt(line.split(":")[1].trim());
                            bufferedInputStream = new BufferedInputStream(in);
                            buffer = new byte[len];

                            int t = 0;
                            while (t < len) {
                                t += bufferedInputStream.read(buffer, t, len - t);
                            }

                            bytesToImageFile(buffer, "0A.jpg");
                            bitmap = BitmapFactory.decodeFile("sdcard/0A.jpg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bufferedInputStream != null) {
                    bufferedInputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void bytesToImageFile(byte[] bytes, String fileName) {
        try {
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes, 0, bytes.length);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final Handler tcp_handler = new Handler(Looper.getMainLooper());
    public final TcpClient client = new TcpClient() {
        @Override
        public void onConnect(SocketTransceiver transceiver) {
            Looper.prepare();
            Toast.makeText(getApplicationContext(), "tcp已连接", Toast.LENGTH_SHORT).show();
            Looper.loop();
        }

        @Override
        public void onConnectFailed() {
            tcp_handler.post(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    Toast.makeText(getApplicationContext(), "tcp连接失败", Toast.LENGTH_SHORT).show();
                    Looper.loop();
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
    private void tcp_connect() {
        if (client.isConnected()) {
            // 断开连接
            client.disconnect();
        } else {
            try {
                int port = Integer.parseInt(robot_tcpport);
                client.connect(robot_ipaddress, port);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口错误", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    /**
     * 发送数据
     *
     * @param send_data
     */
    private void tcp_sendStr(final byte send_data[]) {
        if (!client.isConnected())
            return;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(400);
                    client.getTransceiver().send(send_data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}