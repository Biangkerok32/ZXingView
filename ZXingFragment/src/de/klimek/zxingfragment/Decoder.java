package de.klimek.zxingfragment;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;

public class Decoder implements Camera.PreviewCallback {
	public static final String TAG = Decoder.class.getSimpleName();
	private static final Long DECODE_INTERVAL = 500l;
	static final double BOUNDS_FRACTION = 0.6;
	static final double VERTICAL_HEIGHT_FRACTION = 0.3;
	private Activity mActivity;

	private OnDecodedCallback mCallback;
	private DecodeTask mDecodeTask;
	private volatile boolean mDecoding = false;
	private Timer mDelayTimer = new Timer();

	private Camera mCamera;
	private int mCameraDisplayOrientation;
	private byte[] mPreviewBuffer;

	public Decoder(Activity activity) {
		mActivity = activity;
	}

	public void setOnDecodedCallback(OnDecodedCallback callback) {
		mCallback = callback;
	}

	public void startDecoding(Camera camera, int cameraDisplayOrientation) {
		mDecoding = true;

		mCamera = camera;
		mCameraDisplayOrientation = cameraDisplayOrientation;

		// add buffer to camera to prevent garbage collection spam
		mPreviewBuffer = createPreviewBuffer(camera);
		camera.addCallbackBuffer(mPreviewBuffer);
		camera.setPreviewCallbackWithBuffer(this);
	}

	public void stopDecoding() {
		mDecoding = false;
		if (mDecodeTask != null) {
			mDecodeTask.cancel(true);
		}

	}

	private static byte[] createPreviewBuffer(Camera camera) {
		Parameters params = camera.getParameters();
		int width = params.getPreviewSize().width;
		int height = params.getPreviewSize().width;
		int bitsPerPixel = ImageFormat.getBitsPerPixel(params
				.getPreviewFormat());
		int bytesPerPixel = (int) Math.ceil(bitsPerPixel / 8.0);
		int bufferSize = width * height * bytesPerPixel;
		return new byte[bufferSize];
	}

	/*
	 * Called when the camera has a buffer, e.g. by calling
	 * camera.addCallbackBuffer(buffer). This buffer is automatically removed,
	 * but added again after decoding, resulting in a loop until stopDecoding()
	 * is called.
	 */
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (mDecoding) {
			mDecodeTask = new DecodeTask(this, camera, mCameraDisplayOrientation);
			mDecodeTask.execute(data);
		}
	}

	/*
	 * called by mDecodeTask
	 */
	public void onDecodeSuccess(String string) {
		Log.i(Decoder.TAG, "Decode success.");
		if (mDecoding) {
			mCallback.onDecoded(string);
			// request next frame after delay
			mDelayTimer.schedule(new RequestPreviewFrameTask(), DECODE_INTERVAL);
		}
	}

	/*
	 * called by mDecodeTask
	 */
	public void onDecodeFail() {
		// Log.i(Decoder.TAG, "Decode fail.");
		if (mDecoding) {
			// request next frame after delay
			mDelayTimer.schedule(new RequestPreviewFrameTask(), DECODE_INTERVAL);
		}
	}

	private class RequestPreviewFrameTask extends TimerTask {

		@Override
		public void run() {
			if (mDecoding) {

				final Runnable addCallBackBufferTask = new Runnable() {
					@Override
					public void run() {
						if (mDecoding) {
							mCamera.addCallbackBuffer(mPreviewBuffer);
						}
					}
				};

				mActivity.runOnUiThread(addCallBackBufferTask);
			}
		}

	}

	public interface OnDecodedCallback {
		void onDecoded(String decodedData);
	}

}