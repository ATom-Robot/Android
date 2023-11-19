package xyz.atombot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.EditText;
import android.widget.ImageView;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import xyz.atombot.fangxiang.FangxiangView;

public class MainActivity extends Activity implements View.OnClickListener,
        FangxiangView.OnJoystickChangeListener {

    private static final String TAG = "MainActivity::";

    private Handler http_handler;
    private ImageView imageView;
    private Button stream_btn, tcp_btn;
    private EditText ipddress_edit, tcpddress_edit;
    private FangxiangView ContolFangxiang;

    private final int OPENSTREAM = 1;
    private boolean Data_Choice = true;
    private Bitmap bitmap;

    public byte[] Dataframe = {    //数据帧  下列参数值初始值为1000
            /* Throttle ： 遥控油门
               Roll     ： 横滚角
               Pitch    ： 俯仰角
               Yaw      ： 航向角初始值为*/
            (byte) 0xAA, (byte) 0xBB, (byte) 0x01, (byte) 0x08, (byte) 0x03,
            (byte) 0xE8, (byte) 0x05, (byte) 0xDC, (byte) 0x05, (byte) 0xDC,
            (byte) 0x05, (byte) 0xDC, (byte) 0x0D,
    };

    private final Handler tcp_handler = new Handler(Looper.getMainLooper());
    private final TcpClient client = new TcpClient() {
        @Override
        public void onConnect(SocketTransceiver transceiver) {
            System.out.println("tcp已连接");
        }

        @Override
        public void onConnectFailed() {
            tcp_handler.post(new Runnable() {
                @Override
                public void run() {
                    System.out.println("tcp连接失败");
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
                    System.out.println(hexString);
                }
            });
        }

        @Override
        public void onDisconnect(SocketTransceiver transceiver) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stream_btn = findViewById(R.id.stream_btn);
        tcp_btn = findViewById(R.id.tcp_btn);
        ipddress_edit = findViewById(R.id.ipaddress);
        tcpddress_edit = findViewById(R.id.tcp_addr);
        imageView = findViewById(R.id.img);
        ContolFangxiang = findViewById(R.id.Viewfangxiang);
        ContolFangxiang.setOnJoystickChangeListener(this);
        stream_btn.setOnClickListener(this);
        tcp_btn.setOnClickListener(this);

        HandlerThread handlerThread = new HandlerThread("http");
        handlerThread.start();
        http_handler = new HttpHandler(handlerThread.getLooper());
    }

    @Override
    public void onStart() {
        super.onStart();
        ContolFangxiang.invalidate();
    }

    @Override
    public void onPause() {
        super.onPause();
        ContolFangxiang.invalidate();
    }

    @Override
    public void onStop() {
        client.disconnect();
        super.onStop();
    }

    @Override
    protected void onResume() {
        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.onResume();
//        init_OPenCV();
    }

    private void init_OPenCV() {
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(getApplicationContext(), "OPenCV 初始化失败!!", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.stream_btn:
                http_handler.sendEmptyMessage(OPENSTREAM);
                break;
            case R.id.tcp_btn:
                tcp_connect();
                break;
            default:
                break;
        }
    }

    /**
     * 设置IP和端口地址,连接或断开
     */
    private void tcp_connect() {
        if (client.isConnected()) {
            // 断开连接
            client.disconnect();
        } else {
            try {
                String hostIP = ipddress_edit.getText().toString();
                int port = Integer.parseInt(tcpddress_edit.getText().toString());
                client.connect(hostIP, port);
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
    private void tcp_sendStr(byte send_data[]) {
        if (!client.isConnected())
            return;
        try {
            client.getTransceiver().send(send_data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setOnTouchListener(double xValue, double yValue, boolean temp) {
        this.Data_Choice = temp;
    }

    @Override
    public void setOnMovedListener(final double xValue, final double yValue, boolean temp) {
        this.Data_Choice = temp;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                pack_data(xValue, yValue, false);
                tcp_sendStr(Dataframe);
            }
        });
        thread.start();
    }

    @Override
    public void setOnReleaseListener(final double xValue, final double yValue, boolean temp) {
        this.Data_Choice = temp;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                pack_data(xValue, yValue, false);
                tcp_sendStr(Dataframe);
            }
        });
        thread.start();
    }

    @SuppressLint("SetTextI18n")
    private void pack_data(double xValue, double yValue, boolean joystickReleased) {
        if (Data_Choice) {
            int Yaw = (int) ((xValue + 1) * 500 + 1000);
            int GasPeValue = (int) ((yValue + 1) * 500 + 1000);
            Dataframe[4] = (byte) ((GasPeValue & 0xff00) / 256);//右移八位，高位在前
            Dataframe[5] = (byte) (GasPeValue & 0x00ff);
            Dataframe[10] = (byte) ((Yaw & 0xff00) / 256);      //右移八位，高位在前
            Dataframe[11] = (byte) (Yaw & 0x00ff);
        } else {
            int Roll = (int) ((xValue + 1) * 500 + 1000);
            int Pitch = (int) ((yValue + 1) * 500 + 1000);
            Dataframe[6] = (byte) ((Roll & 0xff00) / 256); //右移八位，高位在前
            Dataframe[7] = (byte) (Roll & 0x00ff);
            Dataframe[8] = (byte) ((Pitch & 0xff00) / 256);//右移八位，高位在前
            Dataframe[9] = (byte) (Pitch & 0x00ff);
        }
    }

    private class HttpHandler extends Handler {
        public HttpHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OPENSTREAM:
                    String text = ipddress_edit.getText().toString();
                    if (text.length() != 0)
                        show_video_stream(text);
                    else
                        Toast.makeText(getApplicationContext(), "IP 地址不能为空", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
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
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
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
}