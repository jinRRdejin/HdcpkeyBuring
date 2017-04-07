package android_serialport_api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import com.mstar.android.tv.TvCommonManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ExecuteCmd {
	
	private final int MSG_SEND_DATA = 0x20;
    private final int MSG_START_WRITE_6M60_KEY = 0x21;
    private final int MSG_SEND_KEY1_MD5_DATA = 0x22;
    private final int MSG_START_GET_6M60_INFO = 0x23;

    public final int STATUS_RUN = 1;
    public final int STATUS_ERROR_NO_FILE = 2;
    public final int STATUS_ERROR_TIME_OUT = 3;
    public final int STATUS_SUCCESS = 4;
    public final int STATUS_ERROR_SAVE_MD5_TIME_OUT = 5;
    public static final String TAG = "FMainAvtivity";
    private ProgressDialog mProgressDialog;
    
    byte[] mBuffer;
    byte[] nTempData;
    byte[] outMosaicMonitorValue;
    
    public int hdcp1UpdateStatus = 0;
    public int hdcp2UpdateStatus = 0;
    private long last_send_time = 0;
    private File hdcp1KeyFile = null;
    public boolean passFlag = true;
    public int percent = 0;
    public int totalBlock = 0;
	
	protected OutputStream mOutputStream;
    private boolean isFinishDataSend = true;
    SendingThread mSendingThread;
    private MainActivity mainActivity;
    private SharedPreferences.Editor editor;
    public static boolean runcmdenable = true;
    public boolean bIsStartSaveMd5 = false;
    public boolean bIsFinishAllKeyWrite = false;
    private Handler mDtvUiEventHandler;

    private TvCommonManager mTvCommonManager;

    public ExecuteCmd(OutputStream os, SerialPort sp, MainActivity rt, SharedPreferences.Editor ed) {
        mOutputStream = os;
        mainActivity = rt;
        editor = ed;
        runcmdenable = true;
        init(mainActivity);
    }
    
    @SuppressLint("HandlerLeak")
    protected Handler handler = new Handler() {

        public void handleMessage(Message msg) {
             if(msg.what == MSG_SEND_DATA){
               int sLen = nTempData.length;
               mBuffer = new byte[sLen];
               for (int i = 0; i < sLen; i++) {
                   mBuffer[i] = nTempData[i];
               }
               isFinishDataSend = false;
               mSendingThread = new SendingThread();
               mSendingThread.start();
            } else if(msg.what == MSG_START_WRITE_6M60_KEY){
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        last_send_time = System.currentTimeMillis();
                        passFlag = false;
                        getUartTestResult();
                        while(!passFlag)
                        {
                            long currTime = System.currentTimeMillis();
                            if(currTime-last_send_time>3000)
                            {
                                hdcp1UpdateStatus = STATUS_ERROR_TIME_OUT;
                                return;
                            }
                        }
                        write6M60HdcpKey1();
                    }
                }).start();
             }  else if(msg.what == MSG_START_GET_6M60_INFO){
                 new Thread(new Runnable() {
                     @Override
                     public void run() {
                         last_send_time = System.currentTimeMillis();
                         passFlag = false;
                         getUartTestResult();
                         while(!passFlag)
                         {
                             long currTime = System.currentTimeMillis();
                             if(currTime-last_send_time>1000)
                             {
                                 //hdcp1UpdateStatus = STATUS_ERROR_TIME_OUT;
                                 return;
                             }
                         }
                         query6m60SoftwareVersion();
                     }
                 }).start();
              }
        };
    
    };
    
    public void write6M60HdcpKey1(){

        String md5_key1 = null;
        File hdcpKeyFile = null;
        percent = 0;
        Log.d(TAG, "write6M60HdcpKey-- start");

        String directoryName = checkFileIsExist("/mnt/usb", "6M60_HDCP_Key1");
        if (directoryName != null && !directoryName.equals(""))
        {
            Log.d(TAG, "hdcp dir found");
            File[] files = new File(directoryName).listFiles();
            Pattern pattern = Pattern.compile("^hdcp1_key.*\\.bin$");
            for (int i = 0; i < files.length; i++) {
                if (pattern.matcher(files[i].getName()).matches()) {
                    hdcpKeyFile = files[i];
                    Log.i(TAG, "hdcpKeyFile=" + hdcpKeyFile.getName());
                    break;
                }
            }
            if (hdcpKeyFile != null && hdcpKeyFile.exists())  {
                hdcp1KeyFile = hdcpKeyFile;
                InputStream in;
                byte keybytes[] = null;
                //md5_key1 = MD5_code.getFileMD5(hdcpKeyFile);
                try {
                    md5_key1 = getFileMD5String(hdcpKeyFile);
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                try {
                    in = new FileInputStream(hdcpKeyFile);
                    Log.d(TAG, "hdcpKeyFile.length:"+(int)hdcpKeyFile.length());
                    keybytes = new byte[(int) getFileSize(hdcpKeyFile)];
                    in.read(keybytes);
                    in.close();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if((keybytes.length==0)||keybytes.length>800)
                {
                    Log.e(TAG, "write6M60HdcpKey-- Hdcpkey1 File Error!");
                    hdcp1UpdateStatus = STATUS_ERROR_NO_FILE;
                    close6M60FactoryMode();
                    return;
                }
                totalBlock = (keybytes.length)/16 + (keybytes.length)%16;
                Log.d(TAG, "totalBlock = "+totalBlock);
                int offset = 0;
                int block = 0;
                byte[] data = new byte[16];
                int dataLength = 0;
                while(offset < keybytes.length)
                {
                    long currentTime = System.currentTimeMillis();
                    if((block > 0) && (currentTime - last_send_time > 3000))
                    {
                        Log.e(TAG, "write6M60HdcpKey-- Time out! Please check connection!");
                        hdcp1UpdateStatus = STATUS_ERROR_TIME_OUT;
                        close6M60FactoryMode();
                        return;
                    }
                    if(passFlag == true)
                    {
                    	 Log.i(TAG, "444444444444444");
                        for(int i=0;i<16;i++)
                        {
                            data[i] = keybytes[offset];
                            Log.i(TAG,"offset = "+offset+" ,i= "+i+" ,data[i] = "+data[i]);
                            dataLength = i+1;
                            if(offset == (keybytes.length - 1))
                            {
                                //last one data cmd, send now
                                passFlag = false;
                                block++;
                                sendWrite6M60HdcpCmd(data, dataLength, block);
                                Log.i(TAG, "55555555555555555555");
                                long currentTime1 = System.currentTimeMillis();
                                boolean bIslastCmd = false;
                                while(currentTime1 - last_send_time < 3000)
                                {
                                    if((passFlag == true)&&(bIslastCmd == false))
                                    {
                                        if(md5_key1 != null)
                                        {
                                            block = (block+1) | 0x80;//Set finish flag.
                                            passFlag = false;
                                            bIslastCmd = true;
                                            sendMd5Cmd(md5_key1, block);
                                        }
                                        Log.i(TAG,"Last one!");
                                    }else if((passFlag == true)&&(bIslastCmd == true))
                                    {
                                        hdcp1UpdateStatus = STATUS_SUCCESS;
                                        Log.i(TAG,"Write HDCP1 STATUS_SUCCESS!!!");
                                        write6M60HdcpKey2();
                                        return;
                                    }
                                    currentTime1 = System.currentTimeMillis();
                                }
                                if(currentTime1 - last_send_time >= 3000)
                                {
                                    Log.e(TAG, "write6M60HdcpKey1-Last- Time out! Please check connection!");
                                    hdcp1UpdateStatus = STATUS_ERROR_TIME_OUT;
                                }
                                return;
                            }
                            offset++;
                            Log.e(TAG, "offset =" + offset );
                        }
                        passFlag = false;
                        block++;
                        sendWrite6M60HdcpCmd(data,dataLength,block);
                    }
                }
            }else{
                Log.e(TAG, "write6M60HdcpKey-- Hdcpkey1 File Error!");
                hdcp1UpdateStatus = STATUS_ERROR_NO_FILE;
                close6M60FactoryMode();
                return;
            }
        }else{
            Log.e(TAG, "write6M60HdcpKey-- Hdcpkey1 File Error!");
            hdcp1UpdateStatus = STATUS_ERROR_NO_FILE;
            close6M60FactoryMode();
            return;
        }
    
    }
	
    
    public void write6M60HdcpKey2(){

        String md5_key2 = null;
        File hdcp2KeyFile = null;

        Log.d(TAG, "write6M60HdcpKey2-- start");
        hdcp2UpdateStatus = STATUS_RUN;
        String directoryName = checkFileIsExist("/mnt/usb", "6M60_HDCP_Key2");
        if (directoryName != null && !directoryName.equals(""))
        {
            Log.d(TAG, "hdcp dir found");
            File[] files = new File(directoryName).listFiles();
            Pattern pattern = Pattern.compile("^hdcp2_key.*\\.bin$");
            for (int i = 0; i < files.length; i++) {
                if (pattern.matcher(files[i].getName()).matches()) {
                    hdcp2KeyFile = files[i];
                    Log.i(TAG, "hdcpKeyFile=" + hdcp2KeyFile.getName());
                    break;
                }
            }
            if (hdcp2KeyFile != null && hdcp2KeyFile.exists())  {
                InputStream in;
                byte keybytes[] = null;
                //md5_key2 = MD5_code.getFileMD5(hdcp2KeyFile);
                try {
                    md5_key2 = getFileMD5String(hdcp2KeyFile);
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                try {
                    in = new FileInputStream(hdcp2KeyFile);
                    Log.d(TAG, "hdcpKeyFile.length:"+(int)hdcp2KeyFile.length());
                    keybytes = new byte[(int) getFileSize(hdcp2KeyFile)];
                    in.read(keybytes);
                    in.close();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if((keybytes.length==0)||((keybytes.length<500)&&(keybytes.length>1500)))
                {
                    Log.e(TAG, "write6M60HdcpKey2-- Hdcpkey2 File Error!");
                    hdcp2UpdateStatus = STATUS_ERROR_NO_FILE;
                    close6M60FactoryMode();
                    return;
                }
                totalBlock = (keybytes.length)/16 + (keybytes.length)%16;
                Log.d(TAG, "totalBlock = "+totalBlock);
                int offset = 0;
                int block = 0;
                byte[] data = new byte[16];
                int dataLength = 0;
                while(offset < keybytes.length)
                {
                    long currentTime = System.currentTimeMillis();
                    if((block > 0) && (currentTime - last_send_time > 1000))
                    {
                        Log.e(TAG, "write6M60HdcpKey2-- Time out! Please check connection!");
                        hdcp2UpdateStatus = STATUS_ERROR_TIME_OUT;
                        close6M60FactoryMode();
                        return;
                    }
                    if(passFlag == true)
                    {
                        for(int i=0;i<16;i++)
                        {
                            data[i] = keybytes[offset];
                            Log.i(TAG,"offset = "+offset+" ,i= "+i+" ,data[i] = "+data[i]);
                            dataLength = i+1;
                            if(offset == (keybytes.length - 1))
                            {
                                //last one data cmd, send now
                                passFlag = false;
                                block++;
                                sendWrite6M60HdcpCmd(data, dataLength, block);

                                long currentTime1 = System.currentTimeMillis();
                                boolean bIslastCmd = false;
                                while(currentTime1 - last_send_time < 3000)
                                {
                                    if((passFlag == true)&&(bIslastCmd == false))
                                    {
                                        if(md5_key2 != null)
                                        {
                                            block = (block+1) | 0x80;//Set finish flag.
                                            passFlag = false;
                                            bIslastCmd = true;
                                            sendMd5Cmd(md5_key2, block);
                                        }
                                        Log.i(TAG,"Last one!");
                                    }else if((passFlag == true)&&(bIslastCmd == true)&&(!bIsStartSaveMd5))
                                    {
                                        passFlag = false;
                                        bIsStartSaveMd5 = true;
                                        save6M60HdcpMd5Cmd();
                                    }else if((passFlag == true)&&(bIsStartSaveMd5 == true))
                                    {
                                        hdcp2UpdateStatus = STATUS_SUCCESS;
                                        Log.i(TAG,"Save MD5 Success!!!");
                                        bIsFinishAllKeyWrite = true;
                                        bIsStartSaveMd5 = false;
                                        close6M60FactoryMode();

                                        hdcp2KeyFile.delete();
                                        hdcp1KeyFile.delete();
                                        Log.d(TAG, "delete files from USB");
                                        mTvCommonManager.setTvosCommonCommand("startuart2test");//For 938 model.
                                        return;
                                    }
                                    currentTime1 = System.currentTimeMillis();
                                }
                                if(currentTime1 - last_send_time >= 3000)
                                {
                                    Log.e(TAG, "write6M60HdcpKey2-Last- Time out! Please check connection!");
                                    hdcp2UpdateStatus = STATUS_ERROR_TIME_OUT;
                                    if(bIsStartSaveMd5)
                                    {
                                        hdcp2UpdateStatus = STATUS_ERROR_SAVE_MD5_TIME_OUT;
                                    }
                                }
                                return;
                            }
                            offset++;
                        }
                        passFlag = false;
                        block++;
                        sendWrite6M60HdcpCmd(data,dataLength,block);
                    }
                }
            }else{
                Log.e(TAG, "write6M60HdcpKey-- Hdcpkey2 File Error!");
                hdcp2UpdateStatus = STATUS_ERROR_NO_FILE;
                close6M60FactoryMode();
                return;
            }
        }else{
            Log.e(TAG, "write6M60HdcpKey-- Hdcpkey2 File Error!");
            hdcp2UpdateStatus = STATUS_ERROR_NO_FILE;
            close6M60FactoryMode();
            return;
        }
		
	}
	
	public void query6m60SoftwareVersion(){
        Log.d("FMainAvtivity","query6m60SoftwareVersion start");
        boolean bRes = false;
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA065703";
        crc = CRC16.getCRC(str);
        Log.i("FMainAvtivity","crc = " + crc + "str = " + str);
        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0x57;
        b[3] = (byte) 0x03;
        b[4] = (byte) 0x13;
        b[5] = (byte) 0xF6;
        sendTclData(b);
    }
	
	public void get6M60SoftwareInfo(){
        mTvCommonManager.setTvosCommonCommand("starthdcpwrite");
        handler.sendEmptyMessageDelayed(MSG_START_GET_6M60_INFO,100);
    }

	 public void write6M60HdcpKeyChangeUart(){
	        hdcp1UpdateStatus = STATUS_RUN;
	        percent = 0;
	        mTvCommonManager.setTvosCommonCommand("starthdcpwrite");
	        handler.sendEmptyMessageDelayed(MSG_START_WRITE_6M60_KEY,100);
	    }
	
	public void changeUartTo6M60(){
        Log.i(TAG,"changeUartTo6M60...");
        mTvCommonManager.setTvosCommonCommand("starthdcpwrite");
        getUartTestResult();
    }
    public void sendTclCmdOK(){
        //AB 05 0A DF 4E
        byte[] b= new byte[5];
        b[0] = (byte)0xAB;
        b[1] = (byte)0x05;
        b[2] = (byte)0x0A;
        b[3] = (byte)0xDF;
        b[4] = (byte)0x4E;
        sendTclData(b);
    }
    public void sendTclCmdFail(){
        //AB 05 0E 9F CA
        byte[] b= new byte[5];
        b[0] = (byte)0xAB;
        b[1] = (byte)0x05;
        b[2] = (byte)0x0E;
        b[3] = (byte)0x9F;
        b[4] = (byte)0xCA;
        sendTclData(b);
    }
    public void query6m60HdcpKey1MD5(){
        Log.d("FMainAvtivity","query6m60HdcpKey1MD5 start");
        boolean bRes = false;
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA06EE00";
        crc = CRC16.getCRC(str);
        Log.i("FMainAvtivity","crc = " + crc + "str = " + str);
        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0xEE;
        b[3] = (byte) 0x00;
        b[4] = (byte) 0x87;
        b[5] = (byte) 0x00;
        sendTclData(b);
    }
    public void query6m60HdcpKey2MD5(){
        Log.d("FMainAvtivity","query6m60HdcpKey2MD5 start");
        boolean bRes = false;
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA06E700";
        crc = CRC16.getCRC(str);
        Log.i("FMainAvtivity","crc = " + crc + "str = " + str);
        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0xE7;
        b[3] = (byte) 0x00;
        b[4] = (byte) 0x3D;
        b[5] = (byte) 0x98;
        sendTclData(b);
    }
	
	public void save6M60HdcpMd5Cmd(){
        last_send_time = System.currentTimeMillis();
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA069657";
        crc = CRC16.getCRC(str);

        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0x96;
        b[3] = (byte) 0x57;
        b[4] = crc_[0];
        b[5] = crc_[1];
        for(int j = 0; j < 6; j++){
            Log.i(TAG,"b["+j+"] = "+b[j]);
        }
        sendTclData(b);
    }

	
	public void sendMd5Cmd(String md5, int mblock){
        String crc = null;
        byte[] crc_ = null;
        byte[] b = null;
        byte[] md5byte = hexString2Bytes(md5);
        int size = md5byte.length + 7;
        last_send_time = System.currentTimeMillis();
        String sizeStr = Integer.toHexString(size).toUpperCase();
        if (sizeStr.length() == 1) {
            sizeStr = '0' + sizeStr;
        }

        String blockStr = Integer.toHexString(mblock).toUpperCase();
        if (blockStr.length() == 1) {
            blockStr = '0' + blockStr;
        }

        String str = "AA" + sizeStr + "9652" + blockStr + md5;
        crc = CRC16.getCRC(str);
        crc_ = hexString2Bytes(crc);

        b = new byte[size];
        b[0] = (byte) 0xAA;
        b[1] = (byte)size;
        b[2] = (byte) 0x96;
        b[3] = (byte) 0x52;
        b[4] = (byte)mblock;
        for(int i = 0; i < md5byte.length; i++){
            b[5 + i] = md5byte[i];
        }
        b[size - 2] = crc_[0];
        b[size - 1] = crc_[1];
        for(int j = 0; j < size; j++){
            Log.i(TAG,"b["+j+"] = "+b[j]);
        }
        sendTclData(b);
    }

	
	 public void sendWrite6M60HdcpCmd (byte[] mdata, int mlength, int mblock)
	    {
	        Log.d(TAG, "sendWriteHdcpCmd : >> mblock = "+mblock+" ,mdata.length:"+mdata.length+" ,mlength = "+mlength);
	        //double nPercent = ((double)mblock)/((double)totalBlock);
	        float nPercent=(float)mblock/totalBlock;
	        percent = (int)(nPercent*100);
	        Log.d(TAG, "nPercent : "+nPercent+"  ,percent = "+percent);
	        last_send_time = System.currentTimeMillis();
	        String crc = null;
	        byte[] crc_ = null;
	        byte[] b = null;
	        String temp_str = Bytes2HexString(mdata);
	        String mdata_str = temp_str.substring(0,mlength*2);
	        Log.i(TAG,">>> mdata_str ="+mdata_str+"  temp_str ="+temp_str);
	        int size = mlength+7;
	        String sizeStr = Integer.toHexString(size).toUpperCase();
	        if (sizeStr.length() == 1) {
	            sizeStr = '0' + sizeStr;
	        }

	        String blockStr = Integer.toHexString(mblock).toUpperCase();
	        if (blockStr.length() == 1) {
	            blockStr = '0' + blockStr;
	        }
	        String str = "AA" + sizeStr + "9652" + blockStr + mdata_str;
	        Log.i(TAG,">>> str = "+str);
	        crc = CRC16.getCRC(str);
	        crc_ = hexString2Bytes(crc);

	        b = new byte[size];
	        b[0] = (byte) 0xAA;
	        b[1] = (byte)size;
	        b[2] = (byte) 0x96;
	        b[3] = (byte) 0x52;
	        b[4] = (byte)mblock;

	        for(int i = 0; i < mlength; i++){
	            b[5 + i] = mdata[i];
	        }
	        b[size - 2] = crc_[0];
	        b[size - 1] = crc_[1];
	        for(int j = 0; j < size; j++){
	            Log.i(TAG,"b["+j+"] = "+b[j]);
	        }
	        sendTclData(b);
	    }
	 public String Bytes2HexString(byte[] b) {
	        String ret = "";
	        for (int i = 0; i < b.length; i++) {
	            String hex = Integer.toHexString(b[i] & 0xFF);
	            if (hex.length() == 1) {
	                hex = '0' + hex;
	            }
	            ret += hex.toUpperCase();
	        }
	        return ret;
	    }
	
	 private long getFileSize(File file) {
	        long size = 0;
	        if (file.exists()) {
	            try {
	                FileInputStream fis = null;
	                fis = new FileInputStream(file);
	                size = fis.available();
	            } catch (FileNotFoundException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	            } catch (IOException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	            }
	        } else {
	            Log.e(TAG, "file not exsit!");
	        }
	        Log.i(TAG, "file size = " + size);
	        return size;
	    }
	
	public String getFileMD5String(File file) throws IOException {
	        MessageDigest messagedigest;
	        try {
	            messagedigest = MessageDigest.getInstance("MD5");

	            FileInputStream in = new FileInputStream(file);
	            FileChannel ch = in.getChannel();
	            MappedByteBuffer byteBuffer = ch.map(FileChannel.MapMode.READ_ONLY,
	                    0, file.length());
	            messagedigest.update(byteBuffer);
	            return bufferToHex(messagedigest.digest(), 0, messagedigest.digest().length);//bufferToHex(messagedigest.digest());
	        } catch (NoSuchAlgorithmException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	        return null;
	    }
	 
	private String bufferToHex(byte bytes[], int m, int n) {
	        StringBuffer stringbuffer = new StringBuffer(2 * n);
	        int k = m + n;
	        for (int l = m; l < k; l++) {
	            appendHexPair(bytes[l], stringbuffer);
	        }
	        return stringbuffer.toString();
	    }
   
	private void appendHexPair(byte bt, StringBuffer stringbuffer) {
       char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
       char c0 = hexDigits[(bt & 0xf0) >> 4];
       char c1 = hexDigits[bt & 0xf];
       stringbuffer.append(c0);
       stringbuffer.append(c1);
   }
   
	private String checkFileIsExist(String path, String fileName) {
        File usbfile = new File(path);
        File[] flist = usbfile.listFiles();
        String filePath = "";

        for (int i = 0; i < flist.length; i++) {
            if (flist[i].isDirectory() && flist[i].canRead()) {
                String sdname = flist[i].getName();
                String checkfilename = path + "/" + sdname + "/" + fileName;
                File file = new File(checkfilename);
                if (file.exists() && file.canRead()) {
                    filePath = checkfilename;
                    break;
                }
            }
        }
        Log.d(TAG, "checkFileIsExist filePath == " + filePath);
        return filePath;
    }
	
	public void close6M60FactoryMode(){
        Log.d("FMainAvtivity","close6M60FactoryMode start");
        boolean bRes = false;
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA061000";
        crc = CRC16.getCRC(str);
        Log.i("FMainAvtivity","crc = " + crc + "str = " + str);
        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0x10;
        b[3] = (byte) 0x00;
        b[4] = (byte) 0xB7;
        b[5] = (byte) 0xCE;
        sendTclData(b);
    }
	
	public void getUartTestResult(){
        Log.d("FMainAvtivity","getUartTestResult start");
        boolean bRes = false;
        byte[] b = null;
        byte[] crc_ = null;
        String str = new String();
        String crc = new String();
        str = "AA061001";
        crc = CRC16.getCRC(str);
        Log.i("FMainAvtivity","crc = " + crc + "str = " + str);
        crc_ = hexString2Bytes(crc);
        b = new byte[6];
        b[0] = (byte) 0xAA;
        b[1] = (byte) 0x06;
        b[2] = (byte) 0x10;
        b[3] = (byte) 0x01;
        b[4] = (byte) 0xA7;
        b[5] = (byte) 0xEF;
        sendTclData(b);   //发送指令
        Log.i("FMainAvtivity","b= " + Bytes2HexString(b) );
    }
	 public  byte[] hexString2Bytes(String src) {
	        int l = src.length() / 2;
	        byte[] ret = new byte[l];
	        for (int i = 0; i < l; i++) {
	            ret[i] = (byte) Integer
	                    .valueOf(src.substring(i * 2, i * 2 + 2), 16).byteValue();
	        }
	        return ret;
	    }
	 public void sendTclData(byte[] senddata) {
	        if(isFinishDataSend)
	        {
	            int sLen = senddata.length;
	            mBuffer = new byte[sLen];
	            
	            for (int i = 0; i < sLen; i++) {
	                mBuffer[i] = senddata[i];
	            }
	            Log.i("FMainAvtivity","mBuffer= " + Bytes2HexString(mBuffer) );
	            isFinishDataSend = false;
	            mSendingThread = new SendingThread();
	            Log.i("FMainAvtivity", "wwwwwwwwwwwwwwwww ");//写入的动作
	            mSendingThread.start();
	        } else {
	            nTempData = senddata;
	            handler.sendEmptyMessageDelayed(MSG_SEND_DATA,30);
	            Log.i("FMainAvtivity", "delay to send message !!!! 30ms ");
	        }
	    }
	 
	 public void sendReturnCmd(int size, String cmd, String data) {
	        byte[] b = null;
	        byte[] cmd_ = null;
	        byte[] data_ = null;
	        byte[] crc_ = null;
	        String str = new String();
	        String crc = new String();
	        if (size == 5) {
	            str = "AB05" + cmd;
	            crc = CRC16.getCRC(str);
	            Log.i("FMainAvtivity","size = 5 crc = " + crc);
	            crc_ = hexString2Bytes(crc);
	            cmd_ = hexString2Bytes(cmd);
	            b = new byte[5];
	            b[0] = (byte) 0xAB;
	            b[1] = (byte) 0x05;
	            b[2] = cmd_[0];
	            b[3] = crc_[0];
	            b[4] = crc_[1];
	            sendTclData(b);
	        }else if(size == 6){
	            str = "AB06" + cmd + data;
	            crc = CRC16.getCRC(str);
	            Log.i("FMainAvtivity","size = 6 crc = " + crc + "cmd = " + cmd + "str = " + str);
	            cmd_ = hexString2Bytes(cmd);
	            data_ = hexString2Bytes(data);
	            crc_ = hexString2Bytes(crc);
	            b = new byte[6];
	            b[0] = (byte) 0xAB;
	            b[1] = (byte) 0x06;
	            b[2] = cmd_[0];
	            b[3] = data_[0];
	            b[4] = crc_[0];
	            b[5] = crc_[1];
	            sendTclData(b);
	        }else if(size == 7){
	            str = "AB07" + cmd + data;
	            crc = CRC16.getCRC(str);
	            Log.i("FMainAvtivity","size = 7 crc = " + crc + "cmd = " + cmd + "str = " + str + "data = " + data);
	            cmd_ = hexString2Bytes(cmd);
	            data_ = hexString2Bytes(data);
	            crc_ = hexString2Bytes(crc);
	            b = new byte[7];
	            b[0] = (byte) 0xAB;
	            b[1] = (byte) 0x07;
	            b[2] = cmd_[0];
	            b[3] = data_[0];
	            b[4] = data_[1];
	            b[5] = crc_[0];
	            b[6] = crc_[1];
	            sendTclData(b);
	        }else if(size == 8){
	            str = "AB08" + cmd + data;
	            crc = CRC16.getCRC(str);
	            Log.i("FMainAvtivity","size = 8 crc = " + crc + "cmd = " + cmd + "str = " + str + "data = " + data);
	            cmd_ = hexString2Bytes(cmd);
	            data_ = hexString2Bytes(data);
	            crc_ = hexString2Bytes(crc);
	            b = new byte[8];
	            b[0] = (byte) 0xAB;
	            b[1] = (byte) 0x08;
	            b[2] = cmd_[0];
	            b[3] = data_[0];
	            b[4] = data_[1];
	            b[5] = data_[2];
	            b[6] = crc_[0];
	            b[7] = crc_[1];
	            sendTclData(b);
	        }
	    }
	 


private class SendingThread extends Thread {
    @Override
    public void run() {
        try {
            if (mOutputStream != null) {
            	Log.i("FMainAvtivity","mBuffer2= " + mBuffer );
                Log.i("FMainAvtivity", "SendingThread  mBuffer.length = "+mBuffer.length);
                mOutputStream.write(mBuffer);
                mOutputStream.flush();
                Log.i("FMainAvtivity", "SendingThread --flush-->>> ");
                isFinishDataSend = true;
            } else {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
  }

@SuppressLint("HandlerLeak")
public void init(Activity rt) {
    mTvCommonManager = TvCommonManager.getInstance();
    mDtvUiEventHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!runcmdenable) {}
        }
    };

    mProgressDialog = new ProgressDialog(mainActivity);
    mProgressDialog.setIndeterminate(false);
    mProgressDialog.setCancelable(false);
   }

}
