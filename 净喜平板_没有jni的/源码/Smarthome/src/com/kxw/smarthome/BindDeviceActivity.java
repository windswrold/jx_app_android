/*
 * 绑定设备，绑定设备的只要流程是：
 * 1.填写订单号，然后服务器生成一个UUID作为此台设备的设备码，并将订单的内容传递到平板
 * 2.平板将订单中的付费方式（包年或包流量）、套餐量（包年为365天，包流量为具体的流量值）写入单片机
 * 3.写完付费方式与套餐量后，查询当前地址的滤芯使用时长，然后写入单片机
 * 4.当套餐与滤芯都写入成功，则回调绑定成功接口
 */
package com.kxw.smarthome;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.xutils.x;
import org.xutils.common.Callback.CommonCallback;
import org.xutils.http.RequestParams;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android_serialport_api.SerialPortUtil;

import com.kxw.smarthome.entity.BaseData;
import com.kxw.smarthome.entity.FilterLifeInfo;
import com.kxw.smarthome.entity.OldFilterLifeInfo;
import com.kxw.smarthome.entity.OptionDescriptionInfo;
import com.kxw.smarthome.entity.OptionDescriptions;
import com.kxw.smarthome.entity.TagInfo;
import com.kxw.smarthome.entity.UserInfo;
import com.kxw.smarthome.entity.VerificationData;
import com.kxw.smarthome.entity.WaterStateInfo;
import com.kxw.smarthome.utils.ConfigUtils;
import com.kxw.smarthome.utils.DBUtils;
import com.kxw.smarthome.utils.DataProcessingUtils;
import com.kxw.smarthome.utils.JsonUtils;
import com.kxw.smarthome.utils.LoadingDialog;
import com.kxw.smarthome.utils.MyToast;
import com.kxw.smarthome.utils.NetUtils;
import com.kxw.smarthome.utils.SharedPreferencesUtil;
import com.kxw.smarthome.utils.ToastUtil;
import com.kxw.smarthome.utils.ToolsUtils;
import com.kxw.smarthome.utils.Utils;

public class BindDeviceActivity extends BaseActivity implements
		OnClickListener, OnLongClickListener {

	private EditText phone_num_et, device_alias_et;
	private Button get_device_code_bt, unbind_device_code_bt,
			renew_device_code_bt;
	private LinearLayout bind_view;
	private LinearLayout hit_view;
	private TextView phone_num, device_alias;
	private static Context context;
	private UserInfo userInfo;
	private FilterLifeInfo mFilterLifeInfo, pFilterLifeInfo;
	private int times = 0;
	private SerialPortUtil mSerialPortUtil;
	private BaseData mBaseData;
	private WaterStateInfo mWaterStateInfo;
	private View reset_top_left, reset_top_right, reset_bottom_left,
			reset_bottom_right;
	
	private boolean setType = true;
	private boolean setLife = true;	
	private boolean isClickTL, isClickTR, isClickBL, isClickBR;
	private Handler handler;
	private Message msg;
	private String pro_no;
	private String oldOrderno = null;
	private String newOrderno = null;
	private int tag = -1;
	
	private Handler mRenewHandler;  
	private HandlerThread mRenewHandlerThread;  
	
	private Handler mWorkHandler;  
	private HandlerThread mWorkHandlerThread;  
	
	private Handler mStateHandler;  
	private HandlerThread mStateHandlerThread;  
	
	private Handler mFilterLifeHandler;  
	private HandlerThread mFilterLifeHandlerThread;  
	
	private OptionDescriptions optionDescriptions = new OptionDescriptions();
	private List<OptionDescriptionInfo> options = new ArrayList<OptionDescriptionInfo>();
	private OptionDescriptionInfo get_order_option = new OptionDescriptionInfo();
	private OptionDescriptionInfo get_filter_option = new OptionDescriptionInfo();
	private OptionDescriptionInfo unbind_device_option = new OptionDescriptionInfo();
	private OptionDescriptionInfo renew_option = new OptionDescriptionInfo();
	
	private VerificationData verificationData;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setBaseContentView(R.layout.device_code_activity);
		context = this;
		initView();
		
	    mRenewHandlerThread = new HandlerThread("SettingActivity_mRenewHandlerThread", 5);  
	    mRenewHandlerThread.start();  
	    mRenewHandler = new Handler(mRenewHandlerThread.getLooper()); 
	    
	    mWorkHandlerThread = new HandlerThread("SettingActivity_mWorkHandlerThread", 5);  
	    mWorkHandlerThread.start();  
	    mWorkHandler = new Handler(mWorkHandlerThread.getLooper()); 
	    
	    mStateHandlerThread = new HandlerThread("SettingActivity_mStateHandlerThread", 5);  
	    mStateHandlerThread.start();  
	    mStateHandler = new Handler(mStateHandlerThread.getLooper()); 
	    
	    mFilterLifeHandlerThread = new HandlerThread("SettingActivity_mFilterLifeHandlerThread", 5);  
	    mFilterLifeHandlerThread.start();  
	    mFilterLifeHandler = new Handler(mFilterLifeHandlerThread.getLooper()); 
		
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.arg1) {

				case 0:
					setType = false;
					setLife = false;
					// DBUtils.updateDB(userInfo);
					initData();
					LoadingDialog.loadingSuccess();
					break;

				case 1:
					//写入数据失败，则清除前面写入的一些数据
					bindFailed(getString(R.string.bind_write_data_err));
					break;

				default:
					break;
				}
				super.handleMessage(msg);
			}
		};
		initData();
		
		verificationData = new VerificationData(BindDeviceActivity.this);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		isClickBL = false;
		isClickBR = false;
		isClickTL = false;
		isClickTR = false;
		super.onResume();
	}

	private void initView() {
		// TODO Auto-generated method stub

		phone_num_et = (EditText) findViewById(R.id.phone_num_et);
		device_alias_et = (EditText) findViewById(R.id.device_alias_et);
		get_device_code_bt = (Button) findViewById(R.id.get_device_code_bt);
		unbind_device_code_bt = (Button) findViewById(R.id.unbind_device_code_bt);
		renew_device_code_bt = (Button) findViewById(R.id.renew_device_code_bt);
		reset_top_left = (View) findViewById(R.id.reset_top_left);
		reset_top_right = (View) findViewById(R.id.reset_top_right);
		reset_bottom_left = (View) findViewById(R.id.reset_bottom_left);
		reset_bottom_right = (View) findViewById(R.id.reset_bottom_right);
		get_device_code_bt.setOnClickListener(this);
		unbind_device_code_bt.setOnClickListener(this);
		reset_top_left.setOnLongClickListener(this);
		reset_top_right.setOnLongClickListener(this);
		reset_bottom_left.setOnLongClickListener(this);
		reset_bottom_right.setOnLongClickListener(this);
		renew_device_code_bt.setOnClickListener(this);

		bind_view = (LinearLayout) findViewById(R.id.bind_view);
		hit_view = (LinearLayout) findViewById(R.id.hit_view);
		phone_num = (TextView) findViewById(R.id.phone_num);
		device_alias = (TextView) findViewById(R.id.device_alias);
	}

	private void initData() {
		// TODO Auto-generated method stub
		
		pro_no = SharedPreferencesUtil.getStringData(BindDeviceActivity.this,"pro_no", "");
		tag = SharedPreferencesUtil.getIntData(BindDeviceActivity.this, "tag", -1);
		userInfo = DBUtils.getFirstData(UserInfo.class);
		mSerialPortUtil = MyApplication.getSerialPortUtil();
		mBaseData = mSerialPortUtil.returnBaseData();

		if (userInfo != null) {
			bind_view.setVisibility(View.GONE);
			hit_view.setVisibility(View.VISIBLE);
			// phone_num.setText(userInfo.phone_num());
			phone_num.setText(Html.fromHtml(String.format(
					getString(R.string.order_no), userInfo.getOrder_no())));
			
			if(!ToolsUtils.isEmpty(SharedPreferencesUtil.getStringData(BindDeviceActivity.this, "alias", "")))
			{
				device_alias.setVisibility(View.VISIBLE);
				device_alias.setText(Html.fromHtml(String.format(
						getString(R.string.device_alias), SharedPreferencesUtil.getStringData(BindDeviceActivity.this, "alias", ""))));
			}
			
			if ((mBaseData.waterSurplus <= 0 || mBaseData.waterSurplus == 65535) 
					&& (mBaseData.timeSurplus <= 0 || mBaseData.timeSurplus == 65535)) {
				renew_device_code_bt.setVisibility(View.VISIBLE);
			}
			
			//验证上次是否续费回调成功
			if(!SharedPreferencesUtil.getStringData(BindDeviceActivity.this,"oldOrderno", userInfo.getOrder_no()).equals(userInfo.getOrder_no()))
			{
				System.out.println("====续费同步====");
				oldOrderno = SharedPreferencesUtil.getStringData(BindDeviceActivity.this,"oldOrderno", "");
				newOrderno = userInfo.getOrder_no();
				isRenewSycn();
			}
			
//			System.out.println("解绑标记===="+tag);
//			
//			if(1 == tag)//测试账号的，有解绑功能
//			{
//				unbind_device_code_bt.setVisibility(View.VISIBLE);
//			}
//			else
//			{
//				unbind_device_code_bt.setVisibility(View.GONE);
//			}
			
		} else {
			hit_view.setVisibility(View.GONE);
		}

		if (SharedPreferencesUtil.getIntData(BindDeviceActivity.this, "model",
				-1) < 0) {
			isStartState = true;
			mSerialPortUtil = MyApplication.getSerialPortUtil();
			mStateHandler.post(mStateRunnable);
		} else {
			System.out.println("model==="
					+ SharedPreferencesUtil.getIntData(BindDeviceActivity.this,
							"model", -1));
		}
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub

		switch (v.getId()) {
		case R.id.title_back_ll:
			finish();
			break;

		case R.id.get_device_code_bt:
			if (!NetUtils.isConnected(context)) {
				MyToast.getManager(context).show(
						getString(R.string.network_disconnected));
				return;
			} else {
				String orderNo = phone_num_et.getText().toString();
				if (orderNo != null && orderNo.length() > 0) {
					new LoadingDialog(context,
							getString(R.string.device_binding), false);
					getDeviceCode(orderNo, pro_no);

				} else {
					MyToast.getManager(context).show(
							getString(R.string.edit_text_error));
				}
			}
			break;
		case R.id.unbind_device_code_bt:
			
			if (!NetUtils.isConnected(context)) {
				MyToast.getManager(context).show(
						getString(R.string.network_disconnected));
			} else {
				showHintDialog(6);
			}
			break;
		case R.id.renew_device_code_bt:

			if (!NetUtils.isConnected(context)) {
				MyToast.getManager(context).show(
						getString(R.string.network_disconnected));
			} else {
				showHintDialog(7);
			}
			break;
		default:
			break;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		setType = false;
		setLife = false;
		mStateHandler.removeCallbacks(mStateRunnable);
		mWorkHandler.removeCallbacks(mWorkRunnable);
		mFilterLifeHandler.removeCallbacks(mFilterLifeRunnable);
		mRenewHandler.removeCallbacks(mRenewRunnable);
	}

	private void bindFailed(String msg) {
		setType = false;
		setLife = false;
		LoadingDialog.loadingFailed(msg);
		DBUtils.deleteAll(UserInfo.class);
		unbindDevice();
	}

	// 设置套餐类型及总量
	private Runnable mWorkRunnable = new Runnable() {  
	    @Override  
	    public void run() {
			while (setType) {
				times++;
				mSerialPortUtil = MyApplication.getSerialPortUtil();
				while ((mSerialPortUtil.setPayType(userInfo.getPay_proid()) > 0 && mSerialPortUtil
						.getReturn() >= 0)) {
					if (userInfo.getPay_proid() == 0) {
						while ((mSerialPortUtil.setWaterVolume((int) Math
								.rint(userInfo.getQuantity())) > 0 && mSerialPortUtil
								.getReturn() >= 0)) {
							times = 0;
							if (pro_no.equals("")) {
								getFilterLife();
							} else {
								getOldFilterLife();
							}
							return;
						}
					} else if (userInfo.getPay_proid() == 1) {
						while ((mSerialPortUtil.setDueTime((int) Math
								.rint(userInfo.getQuantity())) > 0 && mSerialPortUtil
								.getReturn() >= 0)) {
							times = 0;
							if (pro_no.equals("")) {
								getFilterLife();
							} else {
								getOldFilterLife();
							}
							return;
						}
					}
				}
				if (times >= 10) {
					msg = handler.obtainMessage();
					msg.arg1 = 1;
					handler.sendMessage(msg);
					return;
				}
			}
		}  
	}; 

	// 设置滤芯使用寿命	
	private Runnable mFilterLifeRunnable = new Runnable() {  
	    @Override  
	    public void run() {
			setType = false;
			int try_times = 0;

			int life[] = { mFilterLifeInfo.pp, mFilterLifeInfo.cto,
					mFilterLifeInfo.ro, mFilterLifeInfo.t33,
					mFilterLifeInfo.wfr };

			while (setLife) {
				while (try_times < 5) {
					if (mSerialPortUtil.setFilterLife(life, life.length) > 0
							&& mSerialPortUtil.getReturn() >= 0) {
						try_times = 0;
						break;
					} else {
						try_times++;
					}
				}
				;
				if (try_times >= 5) {
					break;
				}
				while (try_times < 5) {
//					if (pFilterLifeInfo != null)// 重新绑定时候的逻辑
//					{
//						if (DBUtils.saveDB(userInfo)
//								&& DBUtils.saveDB(pFilterLifeInfo)) {
//							setLife = false;
//							break;
//						} else {
//							try_times++;
//						}
//					} else {
//						if (DBUtils.saveDB(userInfo)
//								&& DBUtils.saveDB(mFilterLifeInfo)) {
//							setLife = false;
//							break;
//						} else {
//							try_times++;
//						}
//					}
					
					if (DBUtils.saveDB(userInfo)
							&& DBUtils.saveDB(pFilterLifeInfo)) {
						setLife = false;
						break;
					} else {
						try_times++;
					}
				}
				if (try_times >= 5) {
					break;
				}
			}
			if (try_times >= 5) {
				msg = handler.obtainMessage();
				msg.arg1 = 1;
				handler.sendMessage(msg);
				DBUtils.deleteAll(FilterLifeInfo.class);
			} else {
				bindingFeedback();
			}
		}  
	}; 

	// 获取设备码
	public void getDeviceCode(final String orderNo, String pro_no) {

		if (SharedPreferencesUtil.getIntData(BindDeviceActivity.this, "model",
				-1) < 0) {
			LoadingDialog.loadingFailed(getString(R.string.bind_no_model_data));
			return;
		}

		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("orderno", orderNo);
			jObj.accumulate("pro_no", pro_no);
			
			get_order_option.setId("1");
			get_order_option.setOption("BindDeviceActivity：绑定同步获取套餐信息");
			get_order_option.setParam("orderno："+orderNo+"; "
					+"pro_no："+pro_no);
			get_order_option.setLocalDate("原来的mBaseData："+mBaseData.toString());
		} catch (Exception e) {
		}

		RequestParams params = new RequestParams(
				ConfigUtils.get_old_deviceCode_url);

		// params.setSslSocketFactory(sslSocketFactory)
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				LoadingDialog.loadingFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				LoadingDialog.loadingFailed(getString(R.string.data_get_error));

			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub
				userInfo = new UserInfo();
				if (JsonUtils.result(response) == 0) {
					List<UserInfo> list = new ArrayList<UserInfo>();
					List<TagInfo> taglist = new ArrayList<TagInfo>();
					
					try {
						list.addAll(JsonUtils.jsonToArrayList(
								DataProcessingUtils.decode(new JSONObject(
										response).getString("data")),
								UserInfo.class));
						
						taglist.addAll(JsonUtils.jsonToArrayList(
								DataProcessingUtils.decode(new JSONObject(
										response).getString("data")),
										TagInfo.class));
						
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if (list != null && list.size() > 0) {
						userInfo = list.get(0);
						tag = taglist.get(0).getTag();
						
						//00：壁挂；01：台式；02：立式
						if (SharedPreferencesUtil.getIntData(
								BindDeviceActivity.this, "model", -1) != Integer
								.parseInt(userInfo.proname) - 1) {
							LoadingDialog
									.loadingFailed(getString(R.string.bind_model_data_err));
							return;
						}

						userInfo.setOrder_no(orderNo);
						userInfo._id = 1;
						get_order_option.setNetDate("查询到的套餐信息："+userInfo.toString()+"; ");
						options.clear();
						options.add(get_order_option);
						DBUtils.deleteAll(UserInfo.class);					
						mWorkHandler.post(mWorkRunnable);
					} else {
						LoadingDialog
								.loadingFailed(getString(R.string.bind_no_order_data));
					}
				} else {
					LoadingDialog.loadingFailed(JsonUtils.msg(response));
				}
			}
		});
	}

	// 获取滤芯寿命（没用过的滤芯）
	public void getFilterLife() {

		if (!NetUtils.isConnected(context)) {
			LoadingDialog
					.loadingFailed(getString(R.string.network_disconnected));
			return;
		}
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("code", SharedPreferencesUtil.getStringData(
					BindDeviceActivity.this, "province", ""));
			get_filter_option.setId("2");
			get_filter_option.setOption("BindDeviceActivity：设备绑定获取全新的滤芯寿命");
			get_filter_option.setParam("code："+SharedPreferencesUtil.getStringData(BindDeviceActivity.this, "province", ""));
		} catch (Exception e) {
		}
		RequestParams params = new RequestParams(
				ConfigUtils.get_elementLife_url);
		// params.setSslSocketFactory(sslSocketFactory)
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub
			}

			@Override
			public void onSuccess(String response) {

				// TODO Auto-generated method stub
				// [{"pp":100,"cto":200,"ro":150,"t33":450,"wfr":666}]
				mFilterLifeInfo = new FilterLifeInfo();// 原来机器的滤芯寿命
				pFilterLifeInfo = new FilterLifeInfo();// 对应省份的滤芯寿命
				if (JsonUtils.result(response) == 0) {
					List<FilterLifeInfo> list = new ArrayList<FilterLifeInfo>();
					try {
						list.addAll(JsonUtils.jsonToArrayList(
								DataProcessingUtils.decode(new JSONObject(
										response).getString("data")),
								FilterLifeInfo.class));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if (list != null && list.size() > 0) {
						mFilterLifeInfo = list.get(0);
						mFilterLifeInfo._id = 1;
						pFilterLifeInfo = list.get(0);
						pFilterLifeInfo._id = 1;
						setLife = true;
						get_filter_option.setNetDate("对应城市的滤芯寿命："+mFilterLifeInfo.toString());
						options.add(get_filter_option);
						optionDescriptions.setDates(options);
						try {							
							mFilterLifeHandler.post(mFilterLifeRunnable);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						bindFailed(getString(R.string.bind_no_life_data));
					}
				} else {
					bindFailed(JsonUtils.msg(response));
				}
			}
		});
	}

	// 获取滤芯寿命（用过的滤芯）
	public void getOldFilterLife() {

		if (!NetUtils.isConnected(context)) {
			LoadingDialog
					.loadingFailed(getString(R.string.network_disconnected));
			return;
		}
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("pro_no", pro_no);
			jObj.accumulate("code", SharedPreferencesUtil.getStringData(
					BindDeviceActivity.this, "province", ""));
			
			get_filter_option.setId("2");
			get_filter_option.setOption("BindDeviceActivity：绑定设备获取剩余的滤芯寿命");
			get_filter_option.setParam(
					"pro_no："+pro_no+"；"
					+"code："+SharedPreferencesUtil.getStringData(BindDeviceActivity.this, "province", ""));
		} catch (Exception e) {
		}
		RequestParams params = new RequestParams(
				ConfigUtils.get_old_filter_life_url);
		// params.setSslSocketFactory(sslSocketFactory)
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onSuccess(String response) {

				// TODO Auto-generated method stub
				// [{"pp":100,"cto":200,"ro":150,"t33":450,"wfr":666}]
				mFilterLifeInfo = new FilterLifeInfo();// 原来机器的滤芯寿命
				pFilterLifeInfo = new FilterLifeInfo();// 对应省份的滤芯寿命
				if (JsonUtils.result(response) == 0) {
					List<OldFilterLifeInfo> list = new ArrayList<OldFilterLifeInfo>();
					try {
						list.addAll(JsonUtils.jsonToArrayList(
								DataProcessingUtils.decode(new JSONObject(
										response).getString("data")),
								OldFilterLifeInfo.class));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if (list != null && list.size() > 0
							&& list.get(0).getCode() != null
							&& list.get(0).getCode().size() > 0
							&& list.get(0).getPro_no() != null
							&& list.get(0).getPro_no().size() > 0) {
						mFilterLifeInfo = list.get(0).getPro_no().get(0);
						mFilterLifeInfo._id = 1;
						pFilterLifeInfo = list.get(0).getCode().get(0);
						pFilterLifeInfo._id = 1;

						get_filter_option.setNetDate("剩余滤芯寿命："+mFilterLifeInfo.toString()+";"
								+"原始滤芯寿命："+pFilterLifeInfo.toString());
						options.add(get_filter_option);
						optionDescriptions.setDates(options);
						
						setLife = true;
						try {
							mFilterLifeHandler.post(mFilterLifeRunnable);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						bindFailed(getString(R.string.bind_no_life_data));
					}
				} else {
					bindFailed(JsonUtils.msg(response));
				}
			}
		});
	}

	// 绑定成功回调
	public void bindingFeedback() {
		RequestParams params = new RequestParams(ConfigUtils.binding_back_url);
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("code", SharedPreferencesUtil.getStringData(
					BindDeviceActivity.this, "province", ""));
			jObj.accumulate("prono", userInfo.getPro_no());
			jObj.accumulate("orderno", userInfo.getOrder_no());
			jObj.accumulate("status", 0);// 0表示绑定成功，1表示失败
			jObj.accumulate("pro_alias", device_alias_et.getText().toString());
		} catch (Exception e) {
		}
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));

				// 绑定不成功，
				if (pro_no.equals("")) {
					DBUtils.deleteAll(FilterLifeInfo.class);
				}

				Intent reset_intent = new Intent(ConfigUtils.reset_device_alarm);
				sendBroadcast(reset_intent);
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				bindFailed(getString(R.string.data_get_error));

				// 绑定不成功，
				if (pro_no.equals("")) {
					DBUtils.deleteAll(FilterLifeInfo.class);
				}

				Intent reset_intent = new Intent(ConfigUtils.reset_device_alarm);
				sendBroadcast(reset_intent);
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub
				if (JsonUtils.result(response) == 0) {
					// msg= handler.obtainMessage();
					// msg.arg1=0;
					// handler.sendMessage(msg);
					// Utils.pro_no=userInfo.getPro_no();
					// Utils.payment_type=userInfo.getPay_proid();
					
					Intent intent = new Intent(ConfigUtils.upload_option_description_action);
					intent.putExtra("options", optionDescriptions);
					sendBroadcast(intent);

					// 保存下机器码
					SharedPreferencesUtil.saveStringData(
							BindDeviceActivity.this, "pro_no",
							userInfo.getPro_no());
					
					// 保存下是否能解绑的标记
					SharedPreferencesUtil.saveIntData(BindDeviceActivity.this,
							"tag", tag);
					
					//保存下订单号
					SharedPreferencesUtil.saveStringData(BindDeviceActivity.this,
							"oldOrderno", userInfo.getOrder_no());
					
					
					//保存别名
					if(device_alias_et.getText() != null && !ToolsUtils.isEmpty(device_alias_et.getText().toString()))
					{
						SharedPreferencesUtil.saveStringData(BindDeviceActivity.this,
								"alias", device_alias_et.getText().toString());
					}

					ToastUtil
							.showShortToast(getString(R.string.device_bind_success));
					
					LoadingDialog.dismiss();
					
					//保存验证的数据
					verificationData.setPay_proid(userInfo.getPay_proid());
					verificationData.setBindDate(System.currentTimeMillis() / (long) 1000);
					verificationData.setMultiple(userInfo.getMultiple());
					if(userInfo.getPay_proid() == 1)//包年
					{
						verificationData.setTimeSurplus((int)userInfo.getQuantity());
						verificationData.setWaterSurplus(0);
					}
					else
					{
						verificationData.setTimeSurplus(0);
						verificationData.setWaterSurplus((int)userInfo.getQuantity());
					}
					
					/**
					 * 这是过滤数据库里已经存在的错误的滤芯寿命
					 */
					verificationData.setFirstFilter(mFilterLifeInfo.getPp() > pFilterLifeInfo.getPp() ? pFilterLifeInfo.getPp() : mFilterLifeInfo.getPp());
					verificationData.setSecondFilter(mFilterLifeInfo.getCto() > pFilterLifeInfo.getCto() ? pFilterLifeInfo.getCto() : mFilterLifeInfo.getCto());
					verificationData.setThirdFilter(mFilterLifeInfo.getRo() > pFilterLifeInfo.getRo() ? pFilterLifeInfo.getRo() : mFilterLifeInfo.getRo());
					verificationData.setFourthFilter(mFilterLifeInfo.getT33() > pFilterLifeInfo.getT33() ? pFilterLifeInfo.getT33() : mFilterLifeInfo.getT33());
					verificationData.setFivethFilter(mFilterLifeInfo.getWfr() > pFilterLifeInfo.getWfr() ? pFilterLifeInfo.getWfr() : mFilterLifeInfo.getWfr());
					
					setResult(101);
					finish();
				} else {
					bindFailed(JsonUtils.msg(response));

					if (pro_no.equals("")) {
						DBUtils.deleteAll(FilterLifeInfo.class);
					}

					Intent reset_intent = new Intent(
							ConfigUtils.reset_device_alarm);
					sendBroadcast(reset_intent);
				}
			}
		});
	}

	// 解绑设备
	public void unbindingDevice() {
		UserInfo userInfo = DBUtils.getFirstData(UserInfo.class);
		if(userInfo == null)
		{
			return;
		}
		RequestParams params = new RequestParams(
				ConfigUtils.get_unbind_device_url);
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("orderno", userInfo.getOrder_no());
			
			unbind_device_option.setId("1");
			unbind_device_option.setOption("BindDeviceActivity：解绑设备操作");
			unbind_device_option.setParam("orderno："+userInfo.getOrder_no());
			unbind_device_option.setLocalDate("原来的BaseData"+mBaseData.toString());
		} catch (Exception e) {
		}
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				LoadingDialog
				.loadingUnbindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				LoadingDialog
						.loadingUnbindFailed(getString(R.string.data_get_error));
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub
				if (JsonUtils.result(response) == 0) {
					
					unbind_device_option.setNetDate("result:0");
					options.clear();
					options.add(unbind_device_option);
					optionDescriptions.setDates(options);
					
					unbindDevice();
				} else {
					LoadingDialog.loadingUnbindFailed(JsonUtils.msg(response));
				}
			}
		});
	}

	// 解绑设备回调
	public void unbindingDeviceBackCall() {
		final UserInfo userInfo = DBUtils.getFirstData(UserInfo.class);
		verificationData = new VerificationData(BindDeviceActivity.this);
		verificationData.play();
		if(userInfo != null
				&& verificationData != null && verificationData.getBindDate() != -1 && verificationData.getFirstFilter() != -1
				&& verificationData.getFivethFilter() != -1 && verificationData.getFourthFilter() != -1 && verificationData.getPay_proid() != -1
				&& verificationData.getSecondFilter() != -1 && verificationData.getThirdFilter() != -1 && verificationData.getTimeSurplus() != -1
				&& verificationData.getWaterSurplus() != -1)
		{
			RequestParams params = new RequestParams(
					ConfigUtils.get_unbind_device_backcall_url);
			JSONObject jObj = new JSONObject();
			try {
				jObj.accumulate("orderno", userInfo.getOrder_no());
				jObj.accumulate("pro_no", userInfo.getPro_no());
				
				jObj.accumulate("prfpp", verificationData.getFirstFilter() < 0 ? 0 : verificationData.getFirstFilter());
				jObj.accumulate("prfcto", verificationData.getSecondFilter() < 0 ? 0 : verificationData.getSecondFilter());
				jObj.accumulate("prfro", verificationData.getThirdFilter() < 0 ? 0 : verificationData.getThirdFilter());
				jObj.accumulate("prft33", verificationData.getFourthFilter() < 0 ? 0 : verificationData.getFourthFilter());
				jObj.accumulate("prfwfr", verificationData.getFivethFilter() < 0 ? 0 : verificationData.getFivethFilter());	
				
				if (userInfo.pay_proid > 0) { // 按时间计费
					jObj.accumulate("restflow", 0);
					jObj.accumulate("day", verificationData.getTimeSurplus() < 0 ? 0 : verificationData.getTimeSurplus());
				} else {
					jObj.accumulate("restflow", verificationData.getWaterSurplus() < 0 ? 0 : verificationData.getWaterSurplus());	
					jObj.accumulate("day", 0);
				}
			} catch (Exception e) {
			}
			params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
			params.setConnectTimeout(10000);
			x.http().post(params, new CommonCallback<String>() {

				@Override
				public void onCancelled(CancelledException arg0) {
					// TODO Auto-generated method stub
					LoadingDialog
					.loadingUnbindFailed(getString(R.string.data_get_error));
				}

				@Override
				public void onError(Throwable arg0, boolean arg1) {
					// TODO Auto-generated method stub
					LoadingDialog
							.loadingUnbindFailed(getString(R.string.data_get_error));
				}

				@Override
				public void onFinished() {
					// TODO Auto-generated method stub

				}

				@Override
				public void onSuccess(String response) {
					// TODO Auto-generated method stub
					if (JsonUtils.result(response) == 0) {
						
						Intent intent = new Intent(ConfigUtils.upload_option_description_action);
						intent.putExtra("options", optionDescriptions);
						sendBroadcast(intent);
						
						DBUtils.deleteAll(UserInfo.class);

						// 保存下机器码
						SharedPreferencesUtil.saveStringData(
								BindDeviceActivity.this, "pro_no",
								userInfo != null ? userInfo.getPro_no() : "");

						//清除下订单是否能解绑的标记
						SharedPreferencesUtil.saveIntData(
								BindDeviceActivity.this, "tag", -1);
						
						// 清空下旧的订单号码
						SharedPreferencesUtil.saveStringData(BindDeviceActivity.this,
								"oldOrderno", "");
						
						//清楚别名
						SharedPreferencesUtil.saveStringData(BindDeviceActivity.this,
								"alias", "");
						
						ToastUtil
								.showShortToast(getString(R.string.device_unbind_success));
						LoadingDialog.dismiss();
						
						//清除验证的数据
						verificationData.clearVerificationData();
						
						setResult(101);
						finish();
					} else {
						LoadingDialog.loadingUnbindFailed(JsonUtils
								.msg(response));
					}
				}
			});
		}
		else
		{
			LoadingDialog.dismiss();
			ToastUtil.showShortToast(getString(R.string.err_orderdata));
			finish();
		}
	}

	// 解绑接口
	private void unbindDevice() {
		while (true) {
			if (!Utils.inuse) {
				mSerialPortUtil = MyApplication.getSerialPortUtil();
				Utils.inuse = true;
				int times = 0;
				int setResult = -1;
				int returnsResult = -1;
				do {
					setResult = mSerialPortUtil.setUnbind();
					if (setResult < 0) {
						// faile to sent data
						break;
					}
					// succes to sent data
					returnsResult = mSerialPortUtil.getReturn();
					if (returnsResult >= 0) {
						unbindingDeviceBackCall();
						break;
					} else {
						times++;
					}
				} while (times < 3);
				Utils.inuse = false;
				if (times >= 3) {
					LoadingDialog.dismiss();
					ToastUtil
							.showShortToast(getString(R.string.device_unbind_failed));
				}
				return;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// TODO Auto-generated method stub
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (getCurrentFocus() != null
					&& getCurrentFocus().getWindowToken() != null) {
				InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				manager.hideSoftInputFromWindow(getCurrentFocus()
						.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			}
		}
		return super.onTouchEvent(event);
	}

	@Override
	public boolean onLongClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
		case R.id.reset_top_left:
			isClickTL = true;
			resetParam();
			break;
		case R.id.reset_top_right:
			isClickTR = true;
			resetParam();
			break;
		case R.id.reset_bottom_left:
			isClickBL = true;
			resetParam();
			break;
		case R.id.reset_bottom_right:
			isClickBR = true;
			resetParam();
			break;
		}
		return false;
	}

	private void resetParam() {
		if (isClickBL && isClickBR && isClickTL && isClickTR) {
			showHintDialog(5);
		}
	}

	boolean isStartState;
	private Runnable mStateRunnable = new Runnable() {  
	    @Override  
	    public void run() {
			while (isStartState) {
				if (!Utils.inuse) {// 串口没有使用, 没有开关水的操作
					Utils.inuse = true;// 串口正在被使用。。。

					// 获取用水开关状态
					int setValue = -1;
					int returnValue = -1;

					try {
						setValue = mSerialPortUtil.setWaterState();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						Utils.inuse = false;
					}
					if (setValue < 0) {
						// faile to sent data
					} else {
						// succes to sent data
						returnValue = mSerialPortUtil.getWaterState();
						if (returnValue >= 0) {
							mWaterStateInfo = mSerialPortUtil
									.returnWaterStateInfo();
							if (mWaterStateInfo != null
									&& mWaterStateInfo.model >= 0) {
								SharedPreferencesUtil.saveIntData(
										BindDeviceActivity.this, "model",
										mWaterStateInfo.model); // 保存下机型信息
							}
							Utils.inuse = false;// 串口使用完毕。。。
							isStartState = false;
							break;
						}
					}
					Utils.inuse = false;// 串口使用完毕。。。
				}
			}
		}  
	}; 

	// 获取续费订单信息
	public void getRenewInfo() {
		if (userInfo != null && userInfo.pro_no != null && userInfo.order_no != null) {
			oldOrderno = userInfo.getOrder_no();
		} else {
			return;
		}
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("prono", pro_no);
			
			renew_option.setId("1");
			renew_option.setOption("BindDeviceActivity：订单续费操作");
			renew_option.setParam("prono："+pro_no);
			renew_option.setLocalDate("原来的BaseData"+mBaseData.toString());
		} catch (Exception e) {
		}
		RequestParams params = new RequestParams(ConfigUtils.get_renowInfo_url);
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				LoadingDialog.loadingFailed(getString(R.string.device_renew_failed));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				LoadingDialog.loadingFailed(getString(R.string.device_renew_failed));
			}

			@Override
			public void onFinished() {
				
			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub
				if (JsonUtils.result(response) == 0) {
					List<UserInfo> list = new ArrayList<UserInfo>();
					try {
						list.addAll(JsonUtils.jsonToArrayList(
								DataProcessingUtils.decode(new JSONObject(
										response).getString("data")),
								UserInfo.class));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if (list != null && list.size() > 0) {
						userInfo = list.get(0);
						userInfo._id = 1;
						System.out.println("userInfo =" + userInfo.toString());
						DBUtils.deleteAll(UserInfo.class);
						if (DBUtils.saveDB(userInfo)) {
							
							renew_option.setNetDate("查询到的套餐信息："+userInfo.toString());
							options.clear();
							options.add(renew_option);
							optionDescriptions.setDates(options);
							
							newOrderno = userInfo.getOrder_no();
							mRenewHandler.post(mRenewRunnable);
						}
					}
				}
				else
				{
					LoadingDialog.loadingFailed(JsonUtils.msg(response));
				}
			}
		});
	}

	// 续费数据写入成功回调接口
	public void getRenewInfoBaskcall() {
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("prono", pro_no);
			jObj.accumulate("orderno", oldOrderno);
			jObj.accumulate("newOrderno", newOrderno);
		} catch (Exception e) {
		}
		RequestParams params = new RequestParams(
				ConfigUtils.get_renowInfo_backcall_url);
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
				SharedPreferencesUtil.saveStringData(BindDeviceActivity.this, "oldOrderno", oldOrderno);//保存起来，要是续费服务不成功，第二次进来对比，不一样就在回调一次，麻痹	
				LoadingDialog.loadingFailed(getString(R.string.device_renew_success_2));
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
				SharedPreferencesUtil.saveStringData(BindDeviceActivity.this, "oldOrderno", oldOrderno);//保存起来，要是续费服务不成功，第二次进来对比，不一样就在回调一次，麻痹	
				LoadingDialog.loadingFailed(getString(R.string.device_renew_success_2));
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub
			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub

				Intent intent = new Intent(ConfigUtils.upload_option_description_action);
				intent.putExtra("options", optionDescriptions);
				sendBroadcast(intent);
				
				//保存验证的数据
				verificationData.setPay_proid(userInfo.getPay_proid());
				verificationData.setBindDate(System.currentTimeMillis() / (long) 1000);
				verificationData.setMultiple(userInfo.getMultiple());
				if(userInfo.getPay_proid() == 1)//包年
				{
					verificationData.setTimeSurplus((int)userInfo.getDay());
					verificationData.setWaterSurplus(0);
				}
				else
				{
					verificationData.setTimeSurplus(0);
					verificationData.setWaterSurplus((int)userInfo.getQuantity());
				}
//				verificationData.setFirstFilter(mFilterLifeInfo.pp);
//				verificationData.setSecondFilter(mFilterLifeInfo.cto);
//				verificationData.setThirdFilter(mFilterLifeInfo.ro);
//				verificationData.setFourthFilter(mFilterLifeInfo.t33);
//				verificationData.setFivethFilter(mFilterLifeInfo.wfr);
				
				if (JsonUtils.result(response) == 0) 
				{
					LoadingDialog.dismiss();
					SharedPreferencesUtil.saveStringData(BindDeviceActivity.this, "oldOrderno", newOrderno);//保存起来，要是续费服务不成功，第二次进来对比，不一样就在回调一次，麻痹	
					ToastUtil.showLongToast(getString(R.string.device_renew_success));
					setResult(101);
					finish();
				}
				else 
				{		
					LoadingDialog.dismiss();
					SharedPreferencesUtil.saveStringData(BindDeviceActivity.this, "oldOrderno", oldOrderno);//保存起来，要是续费服务不成功，第二次进来对比，不一样就在回调一次，麻痹	
					ToastUtil.showLongToast(getString(R.string.device_renew_success_2));
					System.out.println(JsonUtils.msg(response));
					setResult(101);
					finish();
				}
			}
		});
	}
	
	// 检测续费操作成功后服务器状态是否同步
	public void isRenewSycn() {
		JSONObject jObj = new JSONObject();
		try {
			jObj.accumulate("prono", pro_no);
			jObj.accumulate("orderno", oldOrderno);
			jObj.accumulate("newOrderno", newOrderno);
		} catch (Exception e) {
		}
		RequestParams params = new RequestParams(
				ConfigUtils.get_renowInfo_backcall_url);
		params.setBodyContent(DataProcessingUtils.encrypt(jObj.toString()));
		params.setConnectTimeout(10000);
		x.http().post(params, new CommonCallback<String>() {

			@Override
			public void onCancelled(CancelledException arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onError(Throwable arg0, boolean arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onFinished() {
				// TODO Auto-generated method stub
			}

			@Override
			public void onSuccess(String response) {
				// TODO Auto-generated method stub
				
				if (JsonUtils.result(response) == 0) 
				{
					SharedPreferencesUtil.saveStringData(BindDeviceActivity.this, "oldOrderno", newOrderno);//保存起来，要是续费服务不成功，第二次进来对比，不一样就在回调一次，麻痹	
				}
			}
		});
	}
	
	//续费	
	private Runnable mRenewRunnable = new Runnable() {  
	    @Override  
	    public void run() {
			while (true) {
				if (!Utils.inuse) {// 串口没有使用
					mSerialPortUtil = MyApplication.getSerialPortUtil();
					Utils.inuse = true;
					int times = 0;
					int setResult = -1;
					int returnsResult = -1;
					do {
						setResult = mSerialPortUtil
								.setPayType(userInfo.pay_proid);
						if (setResult < 0) {
							// faile to sent data
							break;
						}
						// succes to sent data
						returnsResult = mSerialPortUtil.getReturn();
						if (returnsResult >= 0) {
							int setVolume = -1;
							int returnsVolume = -1;
							int ste_time = 0;
							if (userInfo.pay_proid == 0) {
								do {
									setVolume = mSerialPortUtil
											.setWaterVolume((int) Math
													.rint(userInfo.quantity + 0.5));
									if (setVolume < 0) {
										// faile to sent data
										break;
									}
									// succes to sent data
									returnsVolume = mSerialPortUtil.getReturn();
									if (returnsVolume >= 0) {
										getRenewInfoBaskcall();
										break;
									} else {
										ste_time++;
									}
								} while (ste_time < 3);
							} else if (userInfo.pay_proid == 1) {
								do {
									setVolume = mSerialPortUtil
											.setDueTime(userInfo.day);
									if (setVolume < 0) {
										// faile to sent data
										break;
									}
									// succes to sent data
									returnsVolume = mSerialPortUtil.getReturn();
									if (returnsVolume >= 0) {
										getRenewInfoBaskcall();
										break;
									} else {
										ste_time++;
									}
								} while (ste_time < 3);
							}
							if (ste_time <= 3) {
								break;
							}
						} else {
							times++;
						}
					} while (times < 3);
					Utils.inuse = false;
					return;
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}  
	}; 
	
	/**
	 * 显示操作提示框
	 * @param type
	 */
	private void showHintDialog(int type)
	{
		Intent intent = new Intent(context, HintDialogActivity.class);
//		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("type", type);
		startActivityForResult(intent, 100);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		System.out.println("resultCode==="+resultCode);
		if(resultCode == 105)
		{
			setResult(101);
			finish();
		}
		if(resultCode == 106)
		{
			new LoadingDialog(context, getString(R.string.device_unbinding), false);
			unbindingDevice();
		}
		else if(resultCode == 107)
		{
			new LoadingDialog(context, getString(R.string.device_renew), false);
			getRenewInfo();
		}
	}
}