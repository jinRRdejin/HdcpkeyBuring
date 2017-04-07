package android_serialport_api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.cultraview.hdcpburing.R;
import com.mstar.android.tvapi.common.TvManager;
import com.mstar.android.tvapi.common.exception.TvCommonException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = "FMainAvtivity";
	protected OutputStream mOutputStream;
	private InputStream mInputStream;
	private ReadThread mReadThread;
	private SerialPort mSerialPort = null;
	public ExecuteCmd exeCmd;
	private Boolean bSync = true;
	private SharedPreferences.Editor editor;
	public Context mContext;

	private LinearLayout ctv_update_tx_hdcp_key_ly;
	private LinearLayout ctv_6m60_software_status_ly;

	private static final int MSG_SHOW_TOAST = 0x0A;
	private static final int MSG_RESET_UART = 0xAB;
	private static final int MSG_CHANGE_UART_DELAY = 0xAC;
	private static final int MSG_UPDATE_STATUS = 0xAD;
	private static final int MSG_KEY_TEST_ENTER_WAIT = 0xAF;
	protected final static int SHOW_WAITING_UI = 0xB0;
	protected final static int SHOW_RESULT_TOAST = 0xB1;
	private static final int MSG_REFRESH_SW_INFO_UI = 0xC0;
	private static final int MSG_RESET_UART_TO_TV = 0xC1;

	private boolean bKeyTestResult = false;
	private boolean bIs6M60Ack = false;

	private final int STATUS_RUN = 1;
	private final int STATUS_ERROR_NO_FILE = 2;
	private final int STATUS_ERROR_TIME_OUT = 3;
	private final int STATUS_SUCCESS = 4;
	private final int STATUS_ERROR_SAVE_MD5_TIME_OUT = 5;

	private TextView ctv_factory_hdcp_update_title;
	private TextView ctv_factory_hdcp_one_update_result;
	private TextView ctv_factory_hdcp_two_update_result;
	private TextView ctv_6m60_software_ver_result;
	private TextView ctv_6m60_hdcp_key_result;

	private String str_6M60_version = null;
	private boolean bIs6M60Key1Ready = false;
	private boolean bIs6M60Key2Ready = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ctv_factory_hdcp_update_title = (TextView) findViewById(R.id.ctv_factory_hdcp_update_title);
		ctv_factory_hdcp_one_update_result = (TextView) findViewById(R.id.ctv_factory_hdcp_one_update_result);
		ctv_factory_hdcp_two_update_result = (TextView) findViewById(R.id.ctv_factory_hdcp_two_update_result);
		ctv_6m60_software_ver_result = (TextView) findViewById(R.id.ctv_sw_status_sw_value);
		ctv_6m60_hdcp_key_result = (TextView) findViewById(R.id.ctv_sw_status_key_value);
		Button burning = (Button) findViewById(R.id.burning);
		Button chakan = (Button) findViewById(R.id.chankan);

		try { 
			setTvosCommonCommand("startuart2test");
			mSerialPort = new SerialPort(new File("/dev/ttyS2"), 115200, 0);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (mSerialPort.mFd == null) {
			Log.e(TAG, "native open returns null");
		}

		mInputStream = mSerialPort.mFileInputStream;
		mOutputStream = mSerialPort.mFileOutputStream;
		exeCmd = new ExecuteCmd(mOutputStream, mSerialPort, this, editor);
		mReadThread = new ReadThread(); // ∑¥¿°
		mReadThread.start();

		burning.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				ctv_factory_hdcp_one_update_result.setText(" ");
				ctv_factory_hdcp_two_update_result.setText(" ");
				myHandler.sendEmptyMessageDelayed(MSG_UPDATE_STATUS, 500);//
				exeCmd.write6M60HdcpKeyChangeUart();// ∑¢÷∏¡Ó
				myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV, 3000); 
				Log.i(TAG, "Start To Write HDCP Key.");

			}
		});
		chakan.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				ctv_6m60_software_ver_result.setText(" ");
				ctv_6m60_hdcp_key_result.setText(" ");
				myHandler.sendEmptyMessageDelayed(MSG_UPDATE_STATUS, 500);
				exeCmd.get6M60SoftwareInfo();
				myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV, 3000);

			}
			
			
		});

	}

	private Handler myHandler = new Handler() {
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			super.handleMessage(msg);
			if (msg.what == MSG_SHOW_TOAST) {
				String getToastStr = (String) msg.obj;
				showMyToast(getToastStr);
			} else if (msg.what == SHOW_WAITING_UI) {
				ctv_update_tx_hdcp_key_ly.setVisibility(View.VISIBLE);
			} else if (msg.what == SHOW_RESULT_TOAST) {
				ctv_update_tx_hdcp_key_ly.setVisibility(View.GONE);
			} else if (msg.what == MSG_RESET_UART) {
				setTvosCommonCommand("startuart2test");
				showMyToast("6M60 UART NG!");
				exeCmd.sendReturnCmd(7, "98", "3000");// return result fail
														// command
			} else if (msg.what == MSG_RESET_UART_TO_TV) {
				setTvosCommonCommand("startuart2test");
			} else if (msg.what == MSG_CHANGE_UART_DELAY) {
				exeCmd.changeUartTo6M60();
			} else if (msg.what == MSG_KEY_TEST_ENTER_WAIT) {
				bKeyTestResult = true;
			} else if (msg.what == MSG_UPDATE_STATUS) {
				Log.d(TAG, "++++++ MSG_UPDATE_STATUS");
				String strkey1 = getResources().getString( //
						R.string.str_update_hdcp_1);
				String strkey2 = getResources().getString(
						R.string.str_update_hdcp_2);
				String strShow = "";
				switch (exeCmd.hdcp1UpdateStatus) {
				case STATUS_RUN:
					strShow = strkey1
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_run) + "   "
							+ exeCmd.percent + "%";
					ctv_factory_hdcp_one_update_result
							.setTextColor(getResources()
									.getColor(R.color.white));
					break;
				case STATUS_ERROR_NO_FILE:
					strShow = strkey1
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_fail_no_file);
					ctv_factory_hdcp_one_update_result
							.setTextColor(getResources().getColor(R.color.red));
					break;
				case STATUS_ERROR_TIME_OUT:
					strShow = strkey1
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_fail_timeout);
					ctv_factory_hdcp_one_update_result
							.setTextColor(getResources().getColor(R.color.red));
					break;
				case STATUS_SUCCESS:
					strShow = strkey1
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_ok);
					ctv_factory_hdcp_one_update_result
							.setTextColor(getResources()
									.getColor(R.color.green));
					break;
				default:
					break;
				}
				ctv_factory_hdcp_one_update_result.setText(strShow);
				switch (exeCmd.hdcp2UpdateStatus) {
				case STATUS_RUN:
					strShow = strkey2
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_run) + "   "
							+ exeCmd.percent + "%";
					ctv_factory_hdcp_two_update_result
							.setTextColor(getResources()
									.getColor(R.color.white));
					break;
				case STATUS_ERROR_NO_FILE:
					strShow = strkey2
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_fail_no_file);
					ctv_factory_hdcp_two_update_result
							.setTextColor(getResources().getColor(R.color.red));
					break;
				case STATUS_ERROR_TIME_OUT:
					strShow = strkey2
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_fail_timeout);
					ctv_factory_hdcp_two_update_result
							.setTextColor(getResources().getColor(R.color.red));
					break;
				case STATUS_ERROR_SAVE_MD5_TIME_OUT:
					strShow = strkey2
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_fail_save_timeout);
					ctv_factory_hdcp_two_update_result
							.setTextColor(getResources().getColor(R.color.red));
					break;
				case STATUS_SUCCESS:
					strShow = strkey2
							+ "  "
							+ getResources().getString(
									R.string.str_update_hdcp_ok);
					ctv_factory_hdcp_two_update_result
							.setTextColor(getResources()
									.getColor(R.color.green));
					break;
				default:
					break;
				}
				if (exeCmd.hdcp1UpdateStatus == STATUS_SUCCESS) {
					ctv_factory_hdcp_two_update_result.setText(strShow);
				}
				if ((exeCmd.hdcp2UpdateStatus == STATUS_SUCCESS)
						&& (exeCmd.passFlag == true)) {

				}
				if ((exeCmd.hdcp1UpdateStatus == STATUS_RUN)
						|| ((exeCmd.hdcp1UpdateStatus != STATUS_RUN) && (exeCmd.hdcp2UpdateStatus == STATUS_RUN))) {
					myHandler.sendEmptyMessageDelayed(MSG_UPDATE_STATUS, 500);
				}
			} else if (msg.what == MSG_REFRESH_SW_INFO_UI) {
				myHandler.removeMessages(MSG_RESET_UART_TO_TV);
				ctv_6m60_software_ver_result.setText(str_6M60_version);			
				if (bIs6M60Key1Ready && bIs6M60Key2Ready) {
					ctv_6m60_hdcp_key_result.setText(getResources().getString(
							R.string.burned_str).toString());
					ctv_6m60_hdcp_key_result.setTextColor(getResources()
							.getColor(R.color.green));
				} else if ((bIs6M60Key1Ready == false)
						&& (bIs6M60Key2Ready == false)) {
					ctv_6m60_hdcp_key_result.setText(getResources().getString(
							R.string.get_error).toString());
					ctv_6m60_hdcp_key_result.setTextColor(getResources()
							.getColor(R.color.red));
				} else if ((bIs6M60Key1Ready == false)
						&& (bIs6M60Key2Ready == true)) {
					ctv_6m60_hdcp_key_result.setText("KEY1 "
							+ getResources().getString(R.string.get_error)
									.toString());
					ctv_6m60_hdcp_key_result.setTextColor(getResources()
							.getColor(R.color.red));
				} else if ((bIs6M60Key1Ready == true)
						&& (bIs6M60Key2Ready == false)) {
					ctv_6m60_hdcp_key_result.setText("KEY2 "
							+ getResources().getString(R.string.get_error)
									.toString());
					ctv_6m60_hdcp_key_result.setTextColor(getResources()
							.getColor(R.color.red));
				}
				exeCmd.close6M60FactoryMode();
				myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV, 1000);
			}
			// else if(msg.what == MSG_REFRESH_AGING_TIME){
			// if(TEST_MODE == WIS_TEST_MODE){
			// tvAgingTimerValue.setText(""+burnInModeGop.getStartedTime());
			// myHandler.sendEmptyMessageDelayed(MSG_REFRESH_AGING_TIME, 1000);
			// }
			// }
		};
	};
	
	 @Override
	    public void onDestroy() {
	        if (mReadThread != null) {
	            mReadThread.interrupt();
	        }
	        //ledBlink.disable();
	        mSerialPort.close();
	        mSerialPort = null;
	        ExecuteCmd.runcmdenable = false;
	        super.onDestroy();
	    }

	private int[] setTvosCommonCommand(String command) {
		try {
			short[] ret = TvManager.getInstance().setTvosCommonCommand(command);
			if (ret.length == 0)
				return null;

			// Convert return value from short[] to int[] because AIDL doesn't
			// support short.
			int[] result = new int[ret.length];
			for (int i = 0; i < ret.length; i++) {
				result[i] = ret[i];
			}
			return result;
		} catch (TvCommonException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void showMyToast(CharSequence text) {
		Toast.makeText(mContext, text.toString(), 3000);
	}

	private int dataHandle(byte[] buffer, int size) {
		Log.d("66666666666", "66666666666666666666");
		Log.d(TAG, "size:" + size);
		for (int z = 0; z < size; z++) {
			Log.d(TAG,
					"dataHandle:buffer[" + z + "]:0x"
							+ Integer.toHexString(buffer[z]));
		}
		try {
			if (size < 4) {
				return 0;
			}
			Log.d(TAG, "CTV dataHandle:buffer[0]:" + buffer[0]);

			if (size >= 5) {
				if ((buffer[size - 5] == -85) && (buffer[size - 4] == 5)
						&& (buffer[size - 3] == 0x0A)
						&& (buffer[size - 2] == -33)
						&& (buffer[size - 1] == 0x4E)) {
					myHandler.removeMessages(MSG_RESET_UART_TO_TV);
					myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV,
							20000);
					bIs6M60Ack = true;
					Log.d(TAG, "Get cmd from 6m60, test ok!");
					size = 5;
					buffer[0] = -85;
					buffer[1] = 5;
					buffer[2] = 0x0A;
					buffer[3] = -33;
					buffer[4] = 0x4E;
					onDataReceived(buffer, size);
				}
				if (size == 14) {
					
					if ((buffer[5] == -85) && (buffer[6] == 9)
							&& (buffer[7] == 0x58))// Get 6M60 software version
					{
						myHandler.removeMessages(MSG_RESET_UART_TO_TV);
						myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV,
								2000);
						byte[] b = new byte[3];
						for (int i = 0; i < 3; i++) {
							b[i] = buffer[9 + i];
							Log.i(TAG,"b[i] = "+b[i]);
						}
						String ret = "";
						for (int i = 0; i < b.length; i++) {
							String hex = Integer.toHexString(b[i] & 0xFF);
							if (hex.length() == 1) {
								hex = '0' + hex;
							}
							if (i != b.length - 1) {
								ret += (hex.toUpperCase() + ".");
							} else {
								ret += hex.toUpperCase();
							}
						}
						Log.d(TAG, "Get 6m60 software version : " + ret);
						str_6M60_version = ret;
						exeCmd.query6m60HdcpKey1MD5();
					}
				} else if (size == 26) {
					myHandler.removeMessages(MSG_RESET_UART_TO_TV);
					myHandler.sendEmptyMessageDelayed(MSG_RESET_UART_TO_TV,
							2000);
					if ((buffer[5] == -85) && (buffer[6] == 0x15)) {
						if ((buffer[7] & 0xFF) == 0xEF)// Get 6M60 hdcp key1
							
														// status
						{
							Log.d(TAG, "44444444444444444444444444 " );
							if((buffer[24] == 0x10)
                                    &&((buffer[25] & 0xFF) == 0xF5))
                            {
                                bIs6M60Key1Ready = false;
                            }else{
                                bIs6M60Key1Ready = true;
                            }
							exeCmd.query6m60HdcpKey2MD5();
						} else if ((buffer[7] & 0xFF) == 0xE8)// Get 6M60 hdcp
																// key2 status
						{
							Log.d(TAG, "5555555555555555555 " );
							if((buffer[24] == 0x62)
                                    &&((buffer[25] & 0xFF) == 0x5B))
                            {
                                bIs6M60Key2Ready = false;
                            }else{
                                bIs6M60Key2Ready = true;
                            }
							Log.d("sendsensend","upgrade UI UI UI ");
							myHandler.sendEmptyMessageDelayed(
									MSG_REFRESH_SW_INFO_UI, 10);
						}
					}
				}
			}
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	private class ReadThread extends Thread {
		@Override
		public void run() {
			super.run();
			int offset = 0;
			byte[] buffer = new byte[128];
			int size;
			while (!isInterrupted()) {
				synchronized (bSync) {
					try {
						try {
							if ((mInputStream.available() > 0)) {
								Thread.sleep(100);
								Log.d(TAG, "11111111offset:" + offset);
								// Thread.sleep(100);
								size = mInputStream.read(buffer, offset,
										128 - offset);
								Log.d(TAG, "getdata:size:" + size);
								Log.d(TAG, "2222222offset:" + offset);
								for (int z = 0; z < size; z++) {
									Log.d(TAG,
											"getdata:buffer["
													+ (z + offset)
													+ "]:0x"
													+ Integer
															.toHexString(buffer[offset
																	+ z]));
								}

								// for(int i=0;i<size;i++)
								// {
								// //Log.d(TAG, "getdata:offset21:"+offset);
								// buffer2[i+offset2] = buffer[i+offset];
								// }
								// offset2 = offset =
								// dataHandle(buffer2,size+offset2);
								offset = dataHandle(buffer, size + offset);
								Log.d(TAG, "33333333offset:" + offset);
								// if(offset <= 0) {
								// Thread.sleep(100);
								// }
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} // while
		} // run
	}

	protected void onDataReceived(final byte[] buffer, final int size) {

		// Debug.startMethodTracing("rev_debug");
		this.runOnUiThread(new Runnable() {
			public void run() {
            if (true) {
					if (buffer[0] == -85 && buffer[1] == 0x05
							&& buffer[2] == 0x0A) {// 0xAB 0x05 0x0A
						// Get cmd from 6M60
						Log.i(TAG, "Get cmd");
						exeCmd.passFlag = true;
						Log.i(TAG, "ttttttttttttttttttttt");
					}
				} else {
					Log.d(TAG, "unknow cmd!!!");
					exeCmd.sendTclCmdFail();
					return;
				}

			}
		});

	}

}
