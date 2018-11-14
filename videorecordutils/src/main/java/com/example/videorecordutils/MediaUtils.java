package com.example.videorecordutils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PermissionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MediaUtils {
    private static final String TAG = "MediaUtils";
    public static final String VIDEO_TYPE = "videoType";
    public static final String FILE_PATH = "filePath";
    public static final String VIDEO_URL = "video_url";
    public static final int MEDIA_AUDIO = 0;
    public static final int MEDIA_VIDEO = 1;
    private MediaRecorder mMediaRecorder;
    private CamcorderProfile profile;
    private Camera mCamera;
    private TextureView mSurfaceView;
    private File targetDir;
    private String targetName;
    private File targetFile;
    private int previewWidth, previewHeight;
    private int recorderType;
    private boolean isRecording;
    private int cameraPosition = 1;//0代表前置摄像头，1代表后置摄像头

    private static MediaUtils mediaUtils;



    private PermissionUtils permissionUtils;
    static final String[] PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    public interface PermissionCallBack{
        void isGranted();
        void isDenied();
    }


    /*设置权限回调*/
    public void checkCameraPermission(final PermissionCallBack permissionCallBack){
        permissionUtils = PermissionUtils.permission(PERMISSIONS);
        if (!PermissionUtils.isGranted(PERMISSIONS)){
            permissionUtils.request();
            permissionUtils.callback(new PermissionUtils.SimpleCallback() {
                @Override
                public void onGranted() {
                    permissionCallBack.isGranted();
                }

                @Override
                public void onDenied() {
                    permissionCallBack.isDenied();
                }
            });
        }else {
            permissionCallBack.isGranted();
        }
    }


    private MediaUtils() {
    }

    public static MediaUtils getInstance() {
        if (mediaUtils == null) {
            mediaUtils = new MediaUtils();
        }
        return mediaUtils;
    }

    public void setRecorderType(int type) {
        this.recorderType = type;
    }

    public void setTargetDir(File file) {
        this.targetDir = file;
        if (targetDir!=null && !targetDir.exists()){
            targetDir.mkdir();
        }
    }

    public void setTargetName(String name) {
        this.targetName = name;
    }

    public String getTargetFilePath() {
        return targetFile!=null?targetFile.getPath():"";
    }

    public boolean deleteTargetFile() {
        if (targetFile!=null && targetFile.exists()) {
            return targetFile.delete();
        } else {
            return false;
        }
    }

    public void setSurfaceView(TextureView view) {
        if (view == null)
            return;

        this.mSurfaceView = view;
        mSurfaceView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startPreView(surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                release();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void record() {
        if (isRecording) {
            try {
                mMediaRecorder.stop();
            } catch (RuntimeException e) {
                deleteTargetFile();
            }
            releaseMediaRecorder();
            if (mCamera != null)
                mCamera.lock();
            isRecording = false;

        } else {
            startRecordThread();
        }
    }

    private boolean prepareRecord() {
        try {

            mMediaRecorder = new MediaRecorder();
            if (recorderType == MEDIA_VIDEO) {
                try {
                    mCamera.unlock();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mMediaRecorder.setCamera(mCamera);
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mMediaRecorder.setProfile(profile);
                // 实际视屏录制后的方向
                if (cameraPosition == 0) {
                    Log.i("tag_ypf_supportSize", " mMediaRecorder.setOrientationHint(270)");
                    mMediaRecorder.setOrientationHint(270);
                } else {
                    Log.i("tag_ypf_supportSize", " mMediaRecorder.setOrientationHint(90)");
                    mMediaRecorder.setOrientationHint(90);
                }

            } else if (recorderType == MEDIA_AUDIO) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }
            targetFile = new File(targetDir,targetName);
            deleteTargetFile();
            boolean newFile = targetFile.createNewFile();
            if (newFile){
                LogUtils.dTag("tag_ypf","文件创建成功！");
            }else {
                LogUtils.dTag("tag_ypf","文件创建失败！");
                return false;
            }
            mMediaRecorder.setOutputFile(targetFile.getPath());

        } catch (Exception e) {
            e.printStackTrace();
            Log.d("MediaRecorder", "Exception prepareRecord: ");
            releaseMediaRecorder();
            return false;
        }
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d("MediaRecorder", "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d("MediaRecorder", "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    public void stopRecordSave() {
        Log.d("Recorder", "stopRecordSave");
        if (isRecording) {
            isRecording = false;
            try {
                mMediaRecorder.stop();
                Log.d("Recorder", targetFile.getPath());
            } catch (RuntimeException r) {
                Log.d("Recorder", "RuntimeException: stop() is called immediately after start()");
            } finally {
                releaseMediaRecorder();
            }
        }
    }

    public void stopRecordUnSave() {
        Log.d("Recorder", "stopRecordUnSave");
        if (isRecording) {
            isRecording = false;
            try {
                mMediaRecorder.stop();
            } catch (RuntimeException r) {
                Log.d("Recorder", "RuntimeException: stop() is called immediately after start()");
                //不保存直接删掉
                deleteTargetFile();
            } finally {
                releaseMediaRecorder();
            }
            //不保存直接删掉
            deleteTargetFile();
        }
    }

    private void startPreView(SurfaceTexture holder) {
        if (mCamera == null) {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
        if (mCamera != null) {

            mCamera.release();

            if (cameraPosition == 1) { //后置
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            } else if (cameraPosition == 0) {//前置
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }

            mCamera.setDisplayOrientation(90);
            try {
                mCamera.setPreviewTexture(holder);
                Camera.Parameters parameters = mCamera.getParameters();
                List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();

                List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
                Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                        mSupportedPreviewSizes, mSurfaceView.getWidth(), mSurfaceView.getHeight());

                Camera.Size previewSize = parameters.getPreviewSize();
                if (previewSize != null)
                    Log.d("tag_ypf_supportSize", "相机默认的参数  Height: " + previewSize.height + "   width: " + previewSize.width);

                previewWidth = optimalSize.width;
                previewHeight = optimalSize.height;
                Log.d("tag_ypf_supportSize", "给相机设置的参数height: " + previewHeight + "   width: " + previewWidth);
                parameters.setPreviewSize(previewWidth, previewHeight);

                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                // 这里是重点，分辨率和比特率
                // 分辨率越大视频大小越大，比特率越大视频越清晰
                // 清晰度由比特率决定，视频尺寸和像素量由分辨率决定
                // 比特率越高越清晰（前提是分辨率保持不变），分辨率越大视频尺寸越大。
                profile.videoFrameWidth = optimalSize.width;
                profile.videoFrameHeight = optimalSize.height;
                // 这样设置 1080p的视频 大小在5M , 可根据自己需求调节
                profile.videoBitRate = 2 * optimalSize.width * optimalSize.height;
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String mode : focusModes) {
                        if (mode.contains("continuous-video")) { //如果相机支持连续对焦模式，则设置此模式
                            parameters.setFocusMode("continuous-video");
                        }
                    }
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            Log.d("Recorder", "release Recorder");
//            releaseCamera();
        }
    }

    public void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
            Log.d("Recorder", "release Camera");
        }
    }

    private void startRecordThread() {
        if (prepareRecord()) {
            try {
                mMediaRecorder.start();
                isRecording = true;
                Log.d("Recorder", "Start Record");
            } catch (RuntimeException r) {
                releaseMediaRecorder();
                Log.d("Recorder", "RuntimeException: start() is called immediately after stop()");
            }
        }
    }

    //重新开启视频录制功能
    public void reTranscribe() {
        stopMediaPlayer();
        if (targetFile!=null){
            deleteTargetFile();
            targetFile=null;
        }
        if (mSurfaceView != null) {
            SurfaceTexture surfaceTexture = mSurfaceView.getSurfaceTexture();
            startPreView(surfaceTexture);
        }
    }


    /**
     * 设置缩放级别
     */
    private void setZoom(int zoomValue) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.isZoomSupported()) {
                int maxZoom = parameters.getMaxZoom();
                if (maxZoom == 0) {
                    return;
                }
                if (zoomValue > maxZoom) {
                    zoomValue = maxZoom;
                }
                parameters.setZoom(zoomValue);
                mCamera.setParameters(parameters);
            }
        }
    }

    private String getVideoThumb(String path) {
        MediaMetadataRetriever media = new MediaMetadataRetriever();
        media.setDataSource(path);
        return bitmap2File(media.getFrameAtTime());
    }

    private String bitmap2File(Bitmap bitmap) {
        File thumbFile = new File(targetDir,
                targetName);
        if (thumbFile.exists()) thumbFile.delete();
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(thumbFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            return null;
        }
        return thumbFile.getAbsolutePath();
    }

    public void switchCamera() {
        Log.i("tag_ypf_supportSize", "切换摄像头");
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();//得到摄像头的个数

        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
            if (cameraPosition == 1) {
                //现在是后置，变更为前置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    mCamera.stopPreview();//停掉原来摄像头的预览
                    mCamera.release();//释放资源
                    mCamera = null;//取消原来摄像头
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    cameraPosition = 0;
                    startPreView(mSurfaceView.getSurfaceTexture());
                    Log.i("tag_ypf_supportSize", "设置前后摄像头标记： cameraPosition = 0  ");
                    break;
                }
            } else {
                //现在是前置， 变更为后置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    mCamera.stopPreview();//停掉原来摄像头的预览
                    mCamera.release();//释放资源
                    mCamera = null;//取消原来摄像头
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    cameraPosition = 1;
                    startPreView(mSurfaceView.getSurfaceTexture());
                    Log.i("tag_ypf_supportSize", "设置前后摄像头标记： cameraPosition = 1  ");
                    break;
                }
            }
        }
    }

    private MediaPlayer mediaPlayer;

    public void startPlayVideo() {
        try {
            startMediaPlayer(new Surface(mSurfaceView.getSurfaceTexture()), true,null);
        } catch (NullVideoFileException e) {
            e.printStackTrace();
        }
    }

    private void startMediaPlayer(Surface mediaSurface, final boolean isLoop,String filePath) throws NullVideoFileException {

        if (targetFile == null || !targetFile.exists()) {
            throw new NullVideoFileException("null video file");
        }


        if (mediaPlayer != null)
            mediaPlayer.release();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setSurface(mediaSurface);
        try {
            String dataSource;
            if (!TextUtils.isEmpty(filePath)){ //外部是否传入文件路径
                if (new File(filePath).exists()){
                    dataSource = filePath;
                }else {
                    return;
                }
            }else {  //外部路径不传入时，使用记录的目标文件的路径
                dataSource = targetFile.getAbsolutePath();
            }
            mediaPlayer.setDataSource(dataSource);
            if (!mediaPlayer.isPlaying() && !mediaPlayer.isLooping()) {
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mediaPlayer.start();
                        mediaPlayer.setLooping(isLoop);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

    }

    class NullVideoFileException extends Exception {
        public NullVideoFileException(String msg) {
            super(msg);
        }
    }


    public static Bitmap getVideoBitmap(String path) {

        if (!TextUtils.isEmpty(path)){
            try {
                MediaMetadataRetriever media = new MediaMetadataRetriever();

                media.setDataSource(path);

                return media.getFrameAtTime();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }

        return null;

    }

    /*释放资源*/
    public void release(){
        stopMediaPlayer();
        releaseCamera();
        releaseMediaRecorder();
    }

    private VideoRecorderCallBack videoRecorderCallBack;

    public interface VideoRecorderCallBack{
        void recorderSuccessful();
    }

    public VideoRecorderCallBack getVideoRecorderCallBack() {
        return videoRecorderCallBack;
    }

    public void setVideoRecorderCallBack(VideoRecorderCallBack videoRecorderCallBack) {
        this.videoRecorderCallBack = videoRecorderCallBack;
    }

    //开启视频录制
    public void start2VideoRecorder(Context context,VideoRecorderCallBack videoRecorderCallBack){
        this.videoRecorderCallBack = videoRecorderCallBack;
        Intent intent = new Intent();
        intent.setClass(context, VideoRecorderActivity.class);
        intent.putExtra(VIDEO_TYPE,VideoRecorderActivity.VIDEO_RECORD);
        context.startActivity(intent);
    }

    private Bitmap firstFrameBitmap;

    public MediaUtils setFirstFrameBitmap(Bitmap firstFrameBitmap){
        this.firstFrameBitmap = firstFrameBitmap;
        return this;
    }

    public Bitmap getFirstFrameBitmap(){
        return firstFrameBitmap;
    }

    //开启播放视频
    public void start2PlayVideo(Context context,String videoLink,boolean isUrl){
        if (context!=null && !TextUtils.isEmpty(videoLink)){
            Intent intent = new Intent();
            intent.setClass(context, PlayVideoActivity.class);
            if (isUrl){
                intent.putExtra(VIDEO_URL,videoLink);
            }else {
                intent.putExtra(FILE_PATH,videoLink);
            }
            context.startActivity(intent);
        }

    }

}
