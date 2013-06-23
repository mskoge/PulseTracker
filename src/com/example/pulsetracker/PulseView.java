package com.example.pulsetracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


class PulseView extends SurfaceView implements SurfaceHolder.Callback {
		
	class PulseThread extends Thread implements Handler.Callback {
		private Context mContext;
		private Handler mHandler;
		private SurfaceHolder mSurfaceHolder;
		
		private Paint mLinePaint;
		private Paint mAxisPaint;
		private Paint mCanvasPaint;
		
		private boolean mRun = false;
		private int mState;
		public static final int STATE_RUNNING = 1;
		public static final int STATE_PAUSED = 2;
		
		private static final int MAX_DATA = 100;
		private static final int MAX_PULSE = 2;
		private float[] pulse;
		private long[] time;
		private int ndata = 0;
		private long baseTime;
		private float mTimeSpan;
		
		private static final int BORDER_LEFT = 100;
		private static final int BORDER_RIGHT = 20;
		private static final int BORDER_BOTTOM = 100;
		private static final int BORDER_TOP = 20;
		
		private int mCanvasWidth = 1;
		private int mCanvasHeight = 1;
		private int mChartWidth = 1;
		private int mChartHeight = 1;
	
		public PulseThread(SurfaceHolder surfaceHolder, Context context, Handler handler) {
			mSurfaceHolder = surfaceHolder;
			mHandler = handler;
			mContext = context;
			
			mLinePaint = new Paint();
			mLinePaint.setAntiAlias(true);
			mLinePaint.setARGB(255, 0, 0, 128);
			
			mAxisPaint = new Paint();
			mAxisPaint.setAntiAlias(true);
			mAxisPaint.setARGB(255, 0, 0, 0);

			mCanvasPaint = new Paint();
			mCanvasPaint.setAntiAlias(true);
			mCanvasPaint.setARGB(255, 255, 255, 255);
			mCanvasPaint.setStyle(Paint.Style.FILL);
			
			pulse = new float[MAX_DATA];
			time = new long[MAX_DATA];
			
			baseTime = System.currentTimeMillis();
			mTimeSpan = 10000.f;
		}
		
		private boolean addData(double d) {
			if(ndata>=MAX_DATA) {
				for(int i=0; i<MAX_DATA-1; i++) {
					time[i] = time[i+1];
					pulse[i] = pulse[i+1];
				}
				ndata = MAX_DATA-1;
			};
				
			pulse[ndata] = (float)d;
			time[ndata] = System.currentTimeMillis() - baseTime;
			if(ndata<MAX_DATA) ndata++;
			return true;
		}
		
		public void setTimeSpan(float timeSpan) {
			synchronized(mSurfaceHolder) {
				mTimeSpan = timeSpan;
			}
		}
		
		public void scaleTimeSpan(float scaleFactor) {
			synchronized(mSurfaceHolder) {
				mTimeSpan *= scaleFactor;
				if(mTimeSpan < 1000.f) mTimeSpan = 1000.f;
				if(mTimeSpan > 100000.f) mTimeSpan = 100000.f;
			}
		}
		
		public void setState(int state) {
			synchronized(mSurfaceHolder) {
				mState = state;
			}
		}
		
		public void doStart() {
			synchronized(mSurfaceHolder) {
				setState(STATE_RUNNING);
			}
		}
		
		public void doUnpause() {
			 synchronized (mSurfaceHolder) {
	         }
	         setState(STATE_RUNNING);
		}
		
        public void doPause() {
        	synchronized(mSurfaceHolder) {
        		if(mState == STATE_RUNNING) setState(STATE_PAUSED);
        	}
        }
        
        public void setRunning(boolean r) {
        	mRun = r;
        }
		
		public void setSurfaceSize(int width, int height) {
			synchronized(mSurfaceHolder) {
				mCanvasWidth = width;
				mCanvasHeight = height;
				
				mChartWidth = mCanvasWidth - BORDER_LEFT - BORDER_RIGHT;
				mChartHeight = mCanvasHeight - BORDER_TOP - BORDER_BOTTOM;
			}
		}
		
		@Override
        public void run() {
            while(mRun) {
            	Canvas c = null;
            	try {
            		c = mSurfaceHolder.lockCanvas(null);
            		synchronized (mSurfaceHolder) {
            			//if(mState == STATE_RUNNING) getData();
            			doDraw(c);
                    }
            	} finally {
            		// do this in a finally so that if an exception is thrown   
            		// during the above, we don't leave the Surface in an       
                    // inconsistent state                                       
                    if(c != null) {
                    	mSurfaceHolder.unlockCanvasAndPost(c);
                    }
            	}
            }
		}

				
		private void doDraw(Canvas canvas) {	
			
			// Clear canvas
			canvas.drawRect(0,mCanvasHeight,mCanvasWidth,0,mCanvasPaint);
			
			// Draw Axes
			canvas.drawLine(BORDER_LEFT, mCanvasHeight-BORDER_BOTTOM, 
						BORDER_LEFT, BORDER_TOP, mAxisPaint);
			canvas.drawLine(BORDER_LEFT, mCanvasHeight-BORDER_BOTTOM, 
					mCanvasWidth-BORDER_RIGHT, mCanvasHeight-BORDER_BOTTOM, mAxisPaint);
			
			// Draw Axis Labels
			float time_min = time[0];
			if(ndata!=0) {
				if(time[ndata-1] - time[0] > mTimeSpan) {
					time_min = time[ndata-1]-mTimeSpan;
				}
			}
			
			float dx = mChartWidth / mTimeSpan;
			float dy = mChartHeight / MAX_PULSE;
			for(int i=0; i<=10; i++) {
				canvas.drawText(Double.toString(i*MAX_PULSE/10.),
						BORDER_LEFT-40.f, 
						mCanvasHeight-BORDER_BOTTOM-dy*i*MAX_PULSE/10.f, mAxisPaint);
			}
			for(int i=0; i<=10; i++) {
				canvas.drawText(Float.toString(((int)((time_min + i*mTimeSpan/10.f)/100))/10.f),
						BORDER_LEFT+dx*i*mTimeSpan/10.f, 
						mCanvasHeight-BORDER_BOTTOM+20.f, mAxisPaint);
			}
			
			// Draw Data
			for(int i=1; i<ndata; i++) {
				if(time[i]>=time_min)
					canvas.drawLine((time[i-1]-time_min)*dx+BORDER_LEFT, 
							mCanvasHeight-BORDER_BOTTOM-pulse[i-1]*dy, 
							(time[i]-time_min)*dx+BORDER_LEFT, 
							mCanvasHeight-BORDER_BOTTOM-pulse[i]*dy, mLinePaint);
			}
		}

		@Override
		public boolean handleMessage(Message msg) {
			Bundle b = msg.getData();
			double d = b.getDouble("PulseData");
			addData(d);
			return true;
		}
	}
	
	private PulseThread thread;
	
	private ScaleGestureDetector mScaleDetector;
	
	public PulseView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// register our interest in hearing about changes to our surface        
	    SurfaceHolder holder = getHolder();
	    holder.addCallback(this);

	    // create thread only; it's started in surfaceCreated()                 
	    thread = new PulseThread(holder, context, new Handler());
	    
	    mScaleDetector = new ScaleGestureDetector(context, 
	    		new ScaleGestureDetector.SimpleOnScaleGestureListener() {
	    	@Override
			public boolean onScale(ScaleGestureDetector detector) {
				thread.scaleTimeSpan(1.f/detector.getScaleFactor());
				invalidate();
				return true;
			}
	    });
	    
	    setFocusable(true);
	}
	
	public PulseThread getThread() {
		return thread;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
	    // Let the ScaleGestureDetector inspect all events.
	    mScaleDetector.onTouchEvent(ev);
	    return true;
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) thread.doPause();
    }

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		thread.setSurfaceSize(width, height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		thread.setRunning(true);
		thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		thread.setRunning(false);

		while (retry) {
			try {
				thread.join();
				retry = false;
	        } catch (InterruptedException e) {
	        }
	    }
	}
}
