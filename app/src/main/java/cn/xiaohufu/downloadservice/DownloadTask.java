package cn.xiaohufu.downloadservice;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

//String 传给后台任务的泛型参数
//Integer 表示进度的泛型参数
//Integer 反馈执行结果的泛型参数
public class DownloadTask extends AsyncTask<String,Integer,Integer> {
    //下载状态常量定义
    public static final int TYPE_SUCCESS=0;
    public static final int TYPE_FAILED=1;
    public static final int TYPE_PAUSED=2;
    public static final int TYPE_CANCELED=3;

    private DownloadListener listener;

    private boolean isCanceled=false;
    private boolean isPaused=false;
    private int lastProgress;

    //构造器要求传入DownloadListener实例，我们并不是直接使用接口调方法
    public DownloadTask(DownloadListener listener){
        this.listener=listener;
    }
    public void pauseDownload(){
        isPaused=true;
    }
    public void cancelDownload(){
        isCanceled=true;
    }

    //后台具体下载逻辑
    @Override
    //String... params表示支持多个字符串参数
    protected Integer doInBackground(String... params) {
        InputStream is=null;
        //RandomAccessFile支持随机读写，本程序主要随机读，以支持断点续传
        //它还可以支持随机写，实现往文件中指定位置插入字节流
        RandomAccessFile savedFile=null;
        File file=null;
        try{
            long downloadLength=0;//记录已下载文件长度
            String downloadUrl=params[0];
            //string.substring(int)返回对应参数下标到结束符的子串
            //string.lastIndexOf('ch')返回最后一个该字符的下标
            String fileName=downloadUrl.substring(downloadUrl.lastIndexOf('/'));
            //下载存放目录
            String dir = Environment.getExternalStoragePublicDirectory
                    (Environment.DIRECTORY_DOWNLOADS).getPath();
            file=new File(dir+fileName);
            if(file.exists()){
                downloadLength=file.length();
            }
            //获取需下载内容长度
            long contentLength=getContentLength(downloadUrl);
            if(contentLength==0){
                return TYPE_FAILED;
            }else if(contentLength==downloadLength){
                //已下载长度等于需下载总长度，说明下载完成
                return TYPE_SUCCESS;
            }
            //方法还没return说明没下载完
            OkHttpClient client = new OkHttpClient();

            //我们需要从断点处下载，文件不存在的情况也包含在此
            //RANGE 第二个参数是字符串"bytes=xxx-xxx"xxx是数字,后面可省略
            Request request=new Request.Builder().
                    addHeader("RANGE","bytes="+downloadLength+"-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if(response!=null){
                is=response.body().byteStream();
                savedFile=new RandomAccessFile(file,"rw");
                savedFile.seek(downloadLength);//跳过已下载的字节
                byte[] b=new byte[1024];
                int total=0;
                int len=0;
                //每次读取1024个字节直至读完
                while((len=is.read(b))!=-1){
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        return TYPE_PAUSED;
                    }else{
                        total+=len;
                        savedFile.write(b,0,len);
                        //计算已下载的百分比
                        int progress=(int)((total+downloadLength)*100/contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if(is!=null){
                    is.close();
                }
                if(savedFile!=null){
                    savedFile.close();
                }
                if(isCanceled&&file!=null){
                    //如果中途取消下载，直接删除文件
                    file.delete();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }

        }
        return TYPE_FAILED;
    }

    //更新下载进度
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress=values[0];
        if(progress>lastProgress){
            listener.onProgress(progress);
            lastProgress=progress;
        }
    }

    //通知最终下载结果


    @Override
    protected void onPostExecute(Integer integer) {
        switch (integer){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            default:
                break;
        }
    }

    private long getContentLength(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request=new Request.Builder().url(downloadUrl).build();
        Response response = client.newCall(request).execute();
        if(response!=null&&response.isSuccessful()){
            long contentLength= response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }
}
