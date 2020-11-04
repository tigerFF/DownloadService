package cn.xiaohufu.downloadservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;

public class DownloadService extends Service {
    public DownloadService() {
    }
    private DownloadTask downloadTask=null;
    private String downloadUrl;
    //匿名内部类，并且实例化
    private DownloadListener listener=new DownloadListener() {
        @Override
        public void onProgress(int progress) {
            getNotificationManager().notify(1,getNotification("Downloading...",progress));
        }

        @Override
        public void onSuccess() {

            //startForeground(1, notification);开启前台服务，
            // 通知栏中该通知也会变为不会随着点击或者滑动而删除。
            // 除非该service结束停止，这个通知也会随之被删除
            //上面的notify的改进


            //stopForeground(false);
            //这个是结束前台的服务，通知栏中该通知会随着点击或者滑动而删除。
            // 参数是：是否删除之前发送的通知，true：删除。
            // false：不删除 （用手滑动或者点击通知会被删除）
            //是build.setAutocancle(true)或manager.cancel(1)的改进

            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Success",-1));
            Toast.makeText(DownloadService.this,"Download Success",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailed() {
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Failed",-1));
            Toast.makeText(DownloadService.this,"Download Failed",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            Toast.makeText(DownloadService.this,"Download Paused",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            stopForeground(true);
            Toast.makeText(DownloadService.this,"Download Canceled",Toast.LENGTH_SHORT).show();
        }
    };
    //获得通知管理器
    private NotificationManager getNotificationManager(){
        return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }
    //获得通知
    private Notification getNotification(String title,int progress){
        //通知通过这个意图构建出自己的延时意图
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,intent,0);
        Notification notification = new NotificationCompat
                .Builder(this, "default")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .setContentIntent(pi)
                .setContentTitle(title)
                .setContentText(progress>0?(progress+"%"):"100%")
                .setProgress(100,progress>0?progress:100,false)
                .build();
        return notification;
    }
    private DownloadBinder mBinder = new DownloadBinder();
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return  mBinder;
    }

    class DownloadBinder extends Binder {
        public void startDownload(String url){
            if(downloadTask==null){
                downloadUrl=url;
                downloadTask=new DownloadTask(listener);
                downloadTask.execute(downloadUrl);
                startForeground(1,getNotification("Downloading...",0));
                Toast.makeText(DownloadService.this,"Downloading...",
                        Toast.LENGTH_SHORT).show();
            }
        }
        public void pauseDownload(){
            if(downloadTask!=null){
                downloadTask.pauseDownload();
            }
        }
        public void cancelDownload(){
            if(downloadTask!=null){
                downloadTask.cancelDownload();
            }
            if(downloadUrl!=null){
                //取消下载需将文件删除，并关闭通知
                String fileName=downloadUrl.substring(downloadUrl.lastIndexOf('/'));
                //下载存放目录
                String dir = Environment.getExternalStoragePublicDirectory
                        (Environment.DIRECTORY_DOWNLOADS).getPath();
                File file=new File(dir+fileName);
                if(file.exists()){
                    file.delete();
                }
                getNotificationManager().cancel(1);
                stopForeground(true);
                Toast.makeText(DownloadService.this,
                        "DownloadCanceled",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
