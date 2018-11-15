package com.example.videorecordutils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PathUtils;
import com.example.videorecordutils.widget.SendView;
import com.example.videorecordutils.widget.VideoProgressBar;

import java.io.File;
import java.util.UUID;

public class VideoRecorderActivity extends AppCompatActivity {

    public static final int CAMERA_RESULT_CODE = 65535;

    private MediaUtils mediaUtils;
    private boolean isCancel;
    private VideoProgressBar progressBar;
    private int mProgress;
    private TextView btnInfo, btn;
    private SendView send;
    private RelativeLayout recordLayout, switchLayout;

    private RelativeLayout viewRoot;

    private TextureView surfaceRoot;

    public static final int VIDEO_RECORD = 0x100000;
    public static final int VIDEO_PLAY = 0x100001;

    private int viewType = VIDEO_RECORD;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.BlackTheme);
        BarUtils.setStatusBarVisibility(getWindow(),false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        initView();

        Intent intent = getIntent();
        if (intent!=null){
            viewType = intent.getIntExtra(MediaUtils.VIDEO_TYPE,-1);
        }

        //初始化播放控制器
        mediaUtils = MediaUtils.getInstance();
        mediaUtils.setRecorderType(MediaUtils.MEDIA_VIDEO);

        if (viewType == VIDEO_RECORD){//录制模式
            viewRoot.setVisibility(View.VISIBLE);
            //获取录制文件路径
            String externalMoviesPath = PathUtils.getExternalAppMoviesPath();
            File fileDir = new File(externalMoviesPath);
            mediaUtils.setTargetDir(fileDir);
            mediaUtils.setTargetName(UUID.randomUUID() + ".mp4");
            //设置录制输出
            mediaUtils.setSurfaceView(surfaceRoot);

            initListener();

        }

    }

    private void initListener() {
        btn.setOnTouchListener(btnTouch);

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //发送和重录按钮
        send.backLayout.setOnClickListener(backClick);
        send.selectLayout.setOnClickListener(selectClick);
        switchLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaUtils.switchCamera();
            }
        });

        //录制按钮
        progressBar.setOnProgressEndListener(listener);
        progressBar.setCancel(true);
    }

    private void initView() {
        viewRoot = findViewById(R.id.rl_videoRecorder_viewRoot);
        send = findViewById(R.id.view_send);
        btnInfo =findViewById(R.id.tv_info);
        btn =findViewById(R.id.main_press_control);
        recordLayout =findViewById(R.id.record_layout);
        switchLayout =findViewById(R.id.btn_switch);
        progressBar =  findViewById(R.id.main_progress_bar);
        surfaceRoot = findViewById(R.id.ttv_video_surfaceViewRoot);
    }

    @Override
    protected void onResume() {
        super.onResume();
        progressBar.setCancel(true);
    }

    View.OnTouchListener btnTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            boolean ret = false;
            float downY = 0;
            int action = event.getAction();

            int i = v.getId();
            if (i == R.id.main_press_control) {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        mediaUtils.record();
                        startView();
                        ret = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!isCancel) {
                            if (MediaUtils.getInstance().isRecording()){
                                if (mProgress < 10) {
                                    //时间太短不保存
                                    mediaUtils.stopRecordUnSave();
                                    Toast.makeText(VideoRecorderActivity.this, "时间太短", Toast.LENGTH_SHORT).show();
                                    stopView(false);
                                    break;
                                } else {
                                    //停止录制
                                    mediaUtils.stopRecordSave();
                                    stopView(true);
                                    mediaUtils.releaseCamera();
                                    mediaUtils.startPlayVideo();
                                }
                            }
                        } else {
                            //现在是取消状态,不保存
                            mediaUtils.stopRecordUnSave();
                            Toast.makeText(VideoRecorderActivity.this, "取消保存", Toast.LENGTH_SHORT).show();
                            stopView(false);
                        }
                        ret = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float currentY = event.getY();
                        isCancel = downY - currentY > 10;
                        moveView();
                        break;
                }
            }
            return ret;
        }
    };

    VideoProgressBar.OnProgressEndListener listener = new VideoProgressBar.OnProgressEndListener() {
        @Override
        public void onProgressEndListener() {
            progressBar.setCancel(true);
            mediaUtils.stopRecordSave();
            stopView(true);
            mediaUtils.releaseCamera();
            mediaUtils.startPlayVideo();
        }
    };

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    progressBar.setProgress(mProgress);
                    if (mediaUtils.isRecording()) {
                        mProgress = mProgress + 1;
                        sendMessageDelayed(handler.obtainMessage(0), 100);
                    }else {
                        LogUtils.i("录制停止");
                    }
                    break;
            }
        }
    };

    private void startView() {
        startAnim();
        mProgress = 0;
        handler.removeMessages(0);
        handler.sendMessage(handler.obtainMessage(0));
    }

    private void moveView() {
        if (isCancel) {
            btnInfo.setText("松手取消");
        } else {
            btnInfo.setText("上滑取消");
        }
    }

    private void stopView(boolean isSave) {
        stopAnim();
        progressBar.setCancel(true);
        mProgress = 0;
        handler.removeMessages(0);
        btnInfo.setText("双击放大");
        if (isSave) {
            recordLayout.setVisibility(View.GONE);
            send.startAnim();
        }
    }

    private void startAnim() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(btn, "scaleX", 1, 0.5f),
                ObjectAnimator.ofFloat(btn, "scaleY", 1, 0.5f),
                ObjectAnimator.ofFloat(progressBar, "scaleX", 1, 1.3f),
                ObjectAnimator.ofFloat(progressBar, "scaleY", 1, 1.3f)
        );
        set.setDuration(250).start();
    }

    private void stopAnim() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(btn, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(btn, "scaleY", 0.5f, 1f),
                ObjectAnimator.ofFloat(progressBar, "scaleX", 1.3f, 1f),
                ObjectAnimator.ofFloat(progressBar, "scaleY", 1.3f, 1f)
        );
        set.setDuration(250).start();
    }

    private View.OnClickListener backClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            send.stopAnim();
            recordLayout.setVisibility(View.VISIBLE);
            mediaUtils.deleteTargetFile();
            mediaUtils.reTranscribe();
        }
    };

    private View.OnClickListener selectClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //释放播放资源
            MediaUtils.VideoRecorderCallBack videoRecorderCallBack = MediaUtils.getInstance().getVideoRecorderCallBack();
            if (videoRecorderCallBack!=null){
                videoRecorderCallBack.recorderSuccessful();
            }
            MediaUtils.getInstance().release();
            finish();
        }
    };
}
