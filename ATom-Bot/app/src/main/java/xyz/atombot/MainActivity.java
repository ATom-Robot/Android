package xyz.atombot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

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

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity::";

    private HandlerThread handlerThread;
    private Handler handler;
    private ImageView imageView;
    private EditText ipddress_editer;

    private final int OPENSTREAM = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.stream_btn).setOnClickListener(this);
        ipddress_editer = findViewById(R.id.ipaddress);
        imageView = findViewById(R.id.img);

        handlerThread = new HandlerThread("http");
        handlerThread.start();
        handler = new HttpHandler(handlerThread.getLooper());
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.stream_btn:
                handler.sendEmptyMessage(OPENSTREAM);
                break;
            default:
                break;
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
                    String text = ipddress_editer.getText().toString();
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
                    StringBuffer stringBuffer = new StringBuffer();
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
                            final Bitmap bitmap = BitmapFactory.decodeFile("sdcard/0A.jpg");
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