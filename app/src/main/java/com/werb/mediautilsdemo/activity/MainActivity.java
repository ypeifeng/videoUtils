package com.werb.mediautilsdemo.activity;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.example.videorecordutils.MediaUtils;
import com.werb.mediautilsdemo.R;

public class MainActivity extends AppCompatActivity {

    static final String[] PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private Button video,videoUrl;

    private ImageView imageView;

    private TextureView ttv;

    private String url = "https://img.gulltour.com/video/weibo/20181113/1542100526668578.mp4";

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            switch (what) {
                case 0x100000:
                    Bitmap videoThumb = MediaUtils.getVideoBitmap(MediaUtils.getInstance().getTargetFilePath());
                    if (videoThumb!=null)
                        imageView.setImageBitmap(videoThumb);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //查验权限
        video = (Button) findViewById(R.id.btn_video);
        imageView = (ImageView) findViewById(R.id.img_video);
        ttv = findViewById(R.id.ttv_main_play);
        videoUrl = findViewById(R.id.bt_main_playUrlVideo);
        video.setOnClickListener(videoClick);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaUtils.getInstance().setFirstFrameBitmap(MediaUtils.getVideoBitmap(MediaUtils.getInstance().getTargetFilePath())).start2PlayVideo(MainActivity.this,MediaUtils.getInstance().getTargetFilePath(),false);
//                MediaUtils.getInstance().startPlayVideo(new Surface(ttv.getSurfaceTexture()),false);
            }
        });

        videoUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaUtils.getInstance().start2PlayVideo(MainActivity.this,url,true);
            }
        });
    }

    View.OnClickListener videoClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MediaUtils.getInstance().checkCameraPermission(new MediaUtils.PermissionCallBack() {
                @Override
                public void isGranted() {
                    startVideo();
                }

                @Override
                public void isDenied() {
                    Log.d("tag_ypf_checkPermission","获取录像相关权限失败");
                }
            });
            }
        };


    private void startVideo() {
        MediaUtils.getInstance().start2VideoRecorder(MainActivity.this, new MediaUtils.VideoRecorderCallBack() {
            @Override
            public void recorderSuccessful() {
                Log.d("tag_ypf_checkPermission","录制完成，实现回调");
                handler.sendEmptyMessage(0x100000);
            }
        });
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == 300) {
//            handler.sendEmptyMessage(0x100000);
//        }
//    }


}
