package org.rajawali3d.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import org.rajawali3d.R;
import org.rajawali3d.record.EglCore;
import org.rajawali3d.record.GlUtil;
import org.rajawali3d.record.WindowSurface;
import org.rajawali3d.renderer.ISurfaceRenderer;
import org.rajawali3d.util.Capabilities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

//import android.view.Surface;
//import javax.microedition.khronos.egl.EGL14;
//import javax.microedition.khronos.egl.EGL11;
//import javax.microedition.khronos.egl.EGLConfig;
//import javax.microedition.khronos.egl.EGLContext;
//import javax.microedition.khronos.egl.EGLDisplay;
//import javax.microedition.khronos.egl.EGLSurface;
//import javax.microedition.khronos.egl.EGL10;
//import javax.microedition.khronos.opengles.GL;
//import javax.microedition.khronos.opengles.GL10;

//import javax.microedition.khronos.opengles.GL10;

/**
 * Rajawali version of a {@link RecordTextureView}. If you plan on using Rajawali with a {@link RecordTextureView},
 * it is imperative that you extend this class or life cycle events may not function as you expect.
 *
 * @author Jared Woolston (jwoolston@tenkiv.com)
 */
public class RecordTextureView extends android.view.TextureView implements ISurface {
    private final static String TAG = "TextureView";
    private final static boolean LOG_ATTACH_DETACH = true;
    private final static boolean LOG_THREADS = true;
    private final static boolean LOG_PAUSE_RESUME = true;
    private final static boolean LOG_SURFACE = true;
    private final static boolean LOG_RENDERER = true;
    private final static boolean LOG_RENDERER_DRAW_FRAME = true;
    private final static boolean LOG_EGL = true;

    private static final GLThreadManager sGLThreadManager = new GLThreadManager();

    private final WeakReference<RecordTextureView> mThisWeakRef = new WeakReference<>(this);

    protected double mFrameRate = 60.0;
    protected int mRenderMode = RENDERMODE_WHEN_DIRTY;
    protected ANTI_ALIASING_CONFIG mAntiAliasingConfig = ANTI_ALIASING_CONFIG.NONE;
    protected int mBitsRed = 5;
    protected int mBitsGreen = 6;
    protected int mBitsBlue = 5;
    protected int mBitsAlpha = 0;
    protected int mBitsDepth = 16;
    protected int mMultiSampleCount = 0;

    public GLThread mGLThread;
    private boolean mDetached;
//    private EGLConfigChooser mEGLConfigChooser;
//    private EGLContextFactory mEGLContextFactory;
//    private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
    private int mEGLContextClientVersion;

    private boolean mPreserveEGLContextOnPause;

    public RendererDelegate mRendererDelegate;

    public RecordTextureView(Context context) {
        super(context);
    }

    public RecordTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyAttributes(context, attrs);
    }

    public RecordTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyAttributes(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public RecordTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        applyAttributes(context, attrs);
    }

    private void applyAttributes(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        final TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.TextureView);
        final int count = array.getIndexCount();
        for (int i = 0; i < count; ++i) {
            int attr = array.getIndex(i);
            if (attr == R.styleable.TextureView_frameRate) {
                mFrameRate = array.getFloat(attr, 60.0f);
            } else if (attr == R.styleable.TextureView_renderMode) {
                mRenderMode = array.getInt(attr, RENDERMODE_WHEN_DIRTY);
            } else if (attr == R.styleable.TextureView_antiAliasingType) {
                mAntiAliasingConfig = ANTI_ALIASING_CONFIG.fromInteger(array.getInteger(attr, ANTI_ALIASING_CONFIG.NONE.ordinal()));
            } else if (attr == R.styleable.TextureView_bitsRed) {
                mBitsRed = array.getInteger(attr, 5);
            } else if (attr == R.styleable.TextureView_bitsGreen) {
                mBitsGreen = array.getInteger(attr, 6);
            } else if (attr == R.styleable.TextureView_bitsBlue) {
                mBitsBlue = array.getInteger(attr, 5);
            } else if (attr == R.styleable.TextureView_bitsAlpha) {
                mBitsAlpha = array.getInteger(attr, 0);
            } else if (attr == R.styleable.TextureView_bitsDepth) {
                mBitsDepth = array.getInteger(attr, 16);
            }
        }
        array.recycle();
    }

    private void initialize() {
        //final int glesMajorVersion = Capabilities.getGLESMajorVersion();
        //TODO change Capabilities.getGLESMajorVersion()
        final int glesMajorVersion = 3;
        setEGLContextClientVersion(glesMajorVersion);

        //setEGLConfigChooser(new RajawaliEGLConfigChooser(glesMajorVersion, mAntiAliasingConfig, mMultiSampleCount,
        //    mBitsRed, mBitsGreen, mBitsBlue, mBitsAlpha, mBitsDepth));
    }

    private void checkRenderThreadState() {
        if (mGLThread != null) {
            throw new IllegalStateException("setRenderer has already been called for this instance.");
        }
    }

    /**
     * This method is part of the SurfaceTexture.Callback interface, and is
     * not normally called or subclassed by clients of TextureView.
     */
    private void surfaceCreated(int width, int height) {
        mGLThread.surfaceCreated(width, height);
    }

    /**
     * This method is part of the SurfaceTexture.Callback interface, and is
     * not normally called or subclassed by clients of TextureView.
     */
    private void surfaceDestroyed() {
        // Surface will be destroyed when we return
        mGLThread.surfaceDestroyed();
    }

    /**
     * This method is part of the SurfaceTexture.Callback interface, and is
     * not normally called or subclassed by clients of TextureView.
     */
    private void surfaceChanged(int w, int h) {
        mGLThread.onWindowResize(w, h);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        if (!isInEditMode()) {
            if (visibility == View.GONE || visibility == View.INVISIBLE) {
                onPause();
            } else {
                onResume();
            }
        }
        super.onVisibilityChanged(changedView, visibility);
    }

    /**
     * This method is used as part of the View class and is not normally
     * called or subclassed by clients of TextureView.
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LOG_ATTACH_DETACH) {
            Log.d(TAG, "onAttachedToWindow reattach =" + mDetached);
        }
        if (mDetached && (mRendererDelegate != null)) {
            int renderMode = RENDERMODE_CONTINUOUSLY;
            if (mGLThread != null) {
                renderMode = mGLThread.getRenderMode();
            }
            mGLThread = new GLThread(mThisWeakRef);
            if (renderMode != RENDERMODE_CONTINUOUSLY) {
                mGLThread.setRenderMode(renderMode);
            }
            mGLThread.start();
        }
        mDetached = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (LOG_ATTACH_DETACH) {
            Log.v(TAG, "onDetachedFromWindow");
        }
        mRendererDelegate.mRenderer.onRenderSurfaceDestroyed(null);
        if (mGLThread != null) {
            mGLThread.requestExitAndWait();
        }
        mDetached = true;
        super.onDetachedFromWindow();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mGLThread != null) {
                // GLThread may still be running if this view was never
                // attached to a window.
                mGLThread.requestExitAndWait();
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public void setFrameRate(double rate) {
        mFrameRate = rate;
        if (mRendererDelegate != null) {
            mRendererDelegate.mRenderer.setFrameRate(rate);
        }
    }

    @Override
    public int getRenderMode() {
        if (mRendererDelegate != null) {
            return getRenderModeInternal();
        } else {
            return mRenderMode;
        }
    }

    @Override
    public void setRenderMode(int mode) {
        mRenderMode = mode;
        if (mRendererDelegate != null) {
            setRenderModeInternal(mRenderMode);
        }
    }

    @Override
    public void setAntiAliasingMode(ANTI_ALIASING_CONFIG config) {
        mAntiAliasingConfig = config;
    }

    @Override
    public void setSampleCount(int count) {
        mMultiSampleCount = count;
    }

    @Override
    public void setSurfaceRenderer(ISurfaceRenderer renderer) throws IllegalStateException {
        if (mRendererDelegate != null) throw new IllegalStateException("A renderer has already been set for this view.");
        initialize();

        // Configure the EGL stuff
        checkRenderThreadState();
//        if (mEGLConfigChooser == null) {
//            throw new IllegalStateException("You must set an EGL config before attempting to set a surface renderer.");
//        }
//        if (mEGLContextFactory == null) {
//            mEGLContextFactory = new DefaultContextFactory();
//        }
//        if (mEGLWindowSurfaceFactory == null) {
//            mEGLWindowSurfaceFactory = new DefaultWindowSurfaceFactory();
//        }

        // Create our delegate
        final RendererDelegate delegate = new RecordTextureView.RendererDelegate(renderer, this);
        // Create the GL thread
        mGLThread = new GLThread(mThisWeakRef);
        mGLThread.start();
        // Render mode cant be set until the GL thread exists
        setRenderModeInternal(mRenderMode);
        // Register the delegate for callbacks
        mRendererDelegate = delegate; // Done to make sure we dont publish a reference before its safe.
        setSurfaceTextureListener(mRendererDelegate);
    }

    @Override
    public void requestRenderUpdate() {
        mGLThread.requestRender();
    }

    /**
     * Control whether the EGL context is preserved when the TextureView is paused and
     * resumed.
     * <p/>
     * If set to true, then the EGL context may be preserved when the TextureView is paused.
     * Whether the EGL context is actually preserved or not depends upon whether the
     * Android device that the program is running on can support an arbitrary number of EGL
     * contexts or not. Devices that can only support a limited number of EGL contexts must
     * release the  EGL context in order to allow multiple applications to share the GPU.
     * <p/>
     * If set to false, the EGL context will be released when the TextureView is paused,
     * and recreated when the TextureView is resumed.
     * <p/>
     * <p/>
     * The default is false.
     *
     * @param preserveOnPause preserve the EGL context when paused
     */
    public void setPreserveEGLContextOnPause(boolean preserveOnPause) {
        mPreserveEGLContextOnPause = preserveOnPause;
    }

    /**
     * @return true if the EGL context will be preserved when paused
     */
    public boolean getPreserveEGLContextOnPause() {
        return mPreserveEGLContextOnPause;
    }

    /**
     * Install a custom EGLContextFactory.
     * <p>If this method is
     * called, it must be called before {@link #setSurfaceRenderer(ISurfaceRenderer)}
     * is called.
     * <p/>
     * If this method is not called, then by default
     * a context will be created with no shared context and
     * with a null attribute list.
     */
//    public void setEGLContextFactory(EGLContextFactory factory) {
//        checkRenderThreadState();
//        mEGLContextFactory = factory;
//    }

    /**
     * Install a custom EGLWindowSurfaceFactory.
     * <p>If this method is
     * called, it must be called before {@link #setSurfaceRenderer(ISurfaceRenderer)}
     * is called.
     * <p/>
     * If this method is not called, then by default
     * a window surface will be created with a null attribute list.
     */
//    public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) {
//        checkRenderThreadState();
//        mEGLWindowSurfaceFactory = factory;
//    }

    /**
     * Install a custom EGLConfigChooser.
     * <p>If this method is
     * called, it must be called before {@link #setSurfaceRenderer(ISurfaceRenderer)}
     * is called.
     * <p/>
     * If no setEGLConfigChooser method is called, then by default the
     * view will choose an EGLConfig that is compatible with the current
     * android.view.Surface, with a depth buffer depth of
     * at least 16 bits.
     *
     * @param configChooser {@link GLSurfaceView.EGLConfigChooser} The EGL Configuration chooser.
     */
//    public void setEGLConfigChooser(EGLConfigChooser configChooser) {
//        checkRenderThreadState();
//        mEGLConfigChooser = configChooser;
//    }

    /**
     * Install a config chooser which will choose a config
     * with at least the specified depthSize and stencilSize,
     * and exactly the specified redSize, greenSize, blueSize and alphaSize.
     * <p>If this method is
     * called, it must be called before {@link #setSurfaceRenderer(ISurfaceRenderer)}
     * is called.
     * <p/>
     * If no setEGLConfigChooser method is called, then by default the
     * view will choose an RGB_888 surface with a depth buffer depth of
     * at least 16 bits.
     */
    public void setEGLConfigChooser(int redSize, int greenSize, int blueSize,
                                    int alphaSize, int depthSize, int stencilSize) {
        //setEGLConfigChooser(new ComponentSizeChooser(redSize, greenSize,
        //    blueSize, alphaSize, depthSize, stencilSize));
    }

    public void setEGLContextClientVersion(int version) {
        checkRenderThreadState();
        mEGLContextClientVersion = version;
    }

    /**
     * Set the rendering mode. When renderMode is
     * RENDERMODE_CONTINUOUSLY, the renderer is called
     * repeatedly to re-render the scene. When renderMode
     * is RENDERMODE_WHEN_DIRTY, the renderer only rendered when the surface
     * is created, or when {@link #requestRenderUpdate} is called. Defaults to RENDERMODE_CONTINUOUSLY.
     * <p/>
     * Using RENDERMODE_WHEN_DIRTY can improve battery life and overall system performance
     * by allowing the GPU and CPU to idle when the view does not need to be updated.
     * <p/>
     * This method can only be called after {@link #setSurfaceRenderer(ISurfaceRenderer)}
     *
     * @param renderMode one of the RENDERMODE_X constants
     *
     * @see #RENDERMODE_CONTINUOUSLY
     * @see #RENDERMODE_WHEN_DIRTY
     */
    private void setRenderModeInternal(int renderMode) {
        mGLThread.setRenderMode(renderMode);
    }

    /**
     * Get the current rendering mode. May be called
     * from any thread. Must not be called before a renderer has been set.
     *
     * @return the current rendering mode.
     * @see #RENDERMODE_CONTINUOUSLY
     * @see #RENDERMODE_WHEN_DIRTY
     */
    private int getRenderModeInternal() {
        return mGLThread.getRenderMode();
    }

    /**
     * Inform the view that the activity is paused. The owner of this view must
     * call this method when the activity is paused. Calling this method will
     * pause the rendering thread.
     * Must not be called before a renderer has been set.
     */
    public void onPause() {
        if (mRendererDelegate != null) {
            mRendererDelegate.mRenderer.onPause();
        }
        if (mGLThread != null) {
            mGLThread.onPause();
        }
    }

    /**
     * Inform the view that the activity is resumed. The owner of this view must
     * call this method when the activity is resumed. Calling this method will
     * recreate the OpenGL display and resume the rendering
     * thread.
     * Must not be called before a renderer has been set.
     */
    public void onResume() {
        if (mRendererDelegate != null) {
            mRendererDelegate.mRenderer.onResume();
        }
        mGLThread.onResume();
    }

    /**
     * Queue a runnable to be run on the GL rendering thread. This can be used
     * to communicate with the Renderer on the rendering thread.
     * Must not be called before a renderer has been set.
     *
     * @param r the runnable to be run on the GL rendering thread.
     */
    public void queueEvent(Runnable r) {
        mGLThread.queueEvent(r);
    }

    public static class RendererDelegate implements SurfaceTextureListener {

        final RecordTextureView mRajawaliTextureView;
        public final ISurfaceRenderer mRenderer;

        public RendererDelegate(ISurfaceRenderer renderer, RecordTextureView textureView) {
            mRenderer = renderer;
            mRajawaliTextureView = textureView;
            mRenderer.setFrameRate(mRajawaliTextureView.mRenderMode == ISurface.RENDERMODE_WHEN_DIRTY ?
                mRajawaliTextureView.mFrameRate : 0);
            mRenderer.setAntiAliasingMode(mRajawaliTextureView.mAntiAliasingConfig);
            mRenderer.setRenderSurface(mRajawaliTextureView);
            mRajawaliTextureView.setSurfaceTextureListener(this);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mRajawaliTextureView.surfaceCreated(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mRajawaliTextureView.surfaceChanged(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            surface.release();
            mRajawaliTextureView.surfaceDestroyed();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Do nothing
        }
    }

    /**
     * A generic GL Thread. Takes care of initializing EGL and GL. Delegates
     * to a Renderer instance to do the actual drawing. Can be configured to
     * render continuously or on request.
     * <p/>
     * All potentially blocking synchronization is done through the
     * sGLThreadManager object. This avoids multiple-lock ordering issues.
     */
    public static class GLThread extends Thread {

        // Once the thread is started, all accesses to the following member
        // variables are protected by the sGLThreadManager monitor
        private boolean mShouldExit;
        private boolean mExited;
        private boolean mRequestPaused;
        private boolean mPaused;
        private boolean mHasSurface;
        private boolean mSurfaceIsBad;
        private boolean mWaitingForSurface;
        private boolean mHaveEglContext;
        private boolean mHaveEglSurface;
        private boolean mFinishedCreatingEglSurface;
        private boolean mShouldReleaseEglContext;
        private int mWidth;
        private int mHeight;
        private int mRenderMode;
        private boolean mRequestRender;
        private boolean mRenderComplete;
        private ArrayList<Runnable> mEventQueue = new ArrayList<>();
        private boolean mSizeChanged = true;

        // End of member variables protected by the sGLThreadManager monitor.

        //public EglHelper mEglHelper;
        public EglCore mEglCore;
        protected WindowSurface mWindowSurface;
        /**
         * Set once at thread construction time, nulled out when the parent view is garbage
         * called. This weak reference allows the TextureView to be garbage collected while
         * the RajawaliGLThread is still alive.
         */
        public WeakReference<RecordTextureView> mRajawaliTextureViewWeakRef;

        GLThread(WeakReference<RecordTextureView> glSurfaceViewWeakRef) {
            super();
            mWidth = 0;
            mHeight = 0;
            mRequestRender = true;
            mRenderMode = RENDERMODE_CONTINUOUSLY;
            mRajawaliTextureViewWeakRef = glSurfaceViewWeakRef;
        }

        private void initEGL() {
            if (mEglCore == null) {
                Log.d(TAG, "mEglCore is null");
                mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);

                //mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
                RecordTextureView tv = mRajawaliTextureViewWeakRef.get();
                if (tv != null) {
                    SurfaceTexture surfaceTexture = tv.getSurfaceTexture();
                    if (surfaceTexture == null) {
                        throw new NullPointerException("SurfaceTexture is null");
                    }
                    mWindowSurface = new WindowSurface(mEglCore, surfaceTexture);
                    mWindowSurface.makeCurrent();
                } else {
                    throw new NullPointerException("TextureView is null");
                }
                Log.d(TAG, "initEGL");
            } else {
                Log.d(TAG, "mEglCore is not null");

            }

        }

        @Override
        public void run() {
            setName("RajawaliGLThread " + getId());
            if (LOG_THREADS) {
                Log.i("RajawaliGLThread", "starting tid=" + getId());
            }

            try {
                guardedRun();
            } catch (InterruptedException e) {
                // fall thru and exit normally
            } finally {
                sGLThreadManager.threadExiting(this);
            }
        }

        /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
        private void stopEglSurfaceLocked() {
            if (mHaveEglSurface) {
                mHaveEglSurface = false;
                //mEglHelper.destroySurface();
            }
        }


        private void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            int[] values = new int[1];

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }

            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
            mEglCore.release();
        }

        /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
        private void stopEglContextLocked() {
            if (mHaveEglContext) {
                //mEglHelper.finish();
                releaseGl();
                mHaveEglContext = false;
                sGLThreadManager.releaseEglContextLocked(this);
            }
        }

        private void guardedRun() throws InterruptedException {
            //mEglHelper = new EglHelper(mRajawaliTextureViewWeakRef);
            mHaveEglContext = false;
            mHaveEglSurface = false;
            try {
                GL10 gl = null;
                boolean createEglContext = false;
                boolean createEglSurface = false;
                boolean createGlInterface = false;
                boolean lostEglContext = false;
                boolean sizeChanged = false;
                boolean wantRenderNotification = false;
                boolean doRenderNotification = false;
                boolean askedToReleaseEglContext = false;
                int w = 0;
                int h = 0;
                Runnable event = null;

                while (true) {
                    synchronized (sGLThreadManager) {
                        while (true) {
                            if (mShouldExit) {
                                return;
                            }

                            if (!mEventQueue.isEmpty()) {
                                event = mEventQueue.remove(0);
                                break;
                            }

                            // Update the pause state.
                            boolean pausing = false;
                            if (mPaused != mRequestPaused) {
                                pausing = mRequestPaused;
                                mPaused = mRequestPaused;
                                sGLThreadManager.notifyAll();
                                if (LOG_PAUSE_RESUME) {
                                    Log.i("RajawaliGLThread", "mPaused is now " + mPaused + " tid=" + getId());
                                }
                            }

                            // Do we need to give up the EGL context?
                            if (mShouldReleaseEglContext) {
                                if (LOG_SURFACE) {
                                    Log.i("RajawaliGLThread", "releasing EGL context because asked to tid=" + getId());
                                }
                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                mShouldReleaseEglContext = false;
                                askedToReleaseEglContext = true;
                            }

                            // Have we lost the EGL context?
                            if (lostEglContext) {
                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                lostEglContext = false;
                            }

                            // When pausing, release the EGL surface:
                            if (pausing && mHaveEglSurface) {
                                if (LOG_SURFACE) {
                                    Log.i("RajawaliGLThread", "releasing EGL surface because paused tid=" + getId());
                                }
                                stopEglSurfaceLocked();
                            }

                            // When pausing, optionally release the EGL Context:
                            if (pausing && mHaveEglContext) {
                                RecordTextureView view = mRajawaliTextureViewWeakRef.get();
                                boolean preserveEglContextOnPause = (view != null) && view.mPreserveEGLContextOnPause;
                                if (!preserveEglContextOnPause || sGLThreadManager.shouldReleaseEGLContextWhenPausing()) {
                                    stopEglContextLocked();
                                    if (LOG_SURFACE) {
                                        Log.i("RajawaliGLThread", "releasing EGL context because paused tid=" + getId());
                                    }
                                }
                            }

                            // When pausing, optionally terminate EGL:
                            if (pausing) {
                                if (sGLThreadManager.shouldTerminateEGLWhenPausing()) {
                                    //mEglHelper.finish();
                                    releaseGl();

                                    if (LOG_SURFACE) {
                                        Log.i("RajawaliGLThread", "terminating EGL because paused tid=" + getId());
                                    }
                                }
                            }

                            // Have we lost the SurfaceView surface?
                            if ((!mHasSurface) && (!mWaitingForSurface)) {
                                if (LOG_SURFACE) {
                                    Log.i("RajawaliGLThread", "noticed surfaceView surface lost tid=" + getId());
                                }
                                if (mHaveEglSurface) {
                                    stopEglSurfaceLocked();
                                }
                                mWaitingForSurface = true;
                                mSurfaceIsBad = false;
                                sGLThreadManager.notifyAll();
                            }

                            // Have we acquired the surface view surface?
                            if (mHasSurface && mWaitingForSurface) {
                                if (LOG_SURFACE) {
                                    Log.i("RajawaliGLThread", "noticed surfaceView surface acquired tid=" + getId());
                                }
                                mWaitingForSurface = false;
                                sGLThreadManager.notifyAll();
                            }

                            if (doRenderNotification) {
                                if (LOG_SURFACE) {
                                    Log.i("RajawaliGLThread", "sending render notification tid=" + getId());
                                }
                                wantRenderNotification = false;
                                doRenderNotification = false;
                                mRenderComplete = true;
                                sGLThreadManager.notifyAll();
                            }

                            // Ready to draw?
                            if (readyToDraw()) {
                                // If we don't have an EGL context, try to acquire one.
                                if (!mHaveEglContext) {
                                    if (askedToReleaseEglContext) {
                                        askedToReleaseEglContext = false;
                                    } else if (sGLThreadManager.tryAcquireEglContextLocked(this)) {
                                        try {
                                            //mEglHelper.start();
                                            initEGL();
                                        } catch (RuntimeException t) {
                                            sGLThreadManager.releaseEglContextLocked(this);
                                            throw t;
                                        }
                                        mHaveEglContext = true;
                                        createEglContext = true;

                                        sGLThreadManager.notifyAll();
                                    }
                                }

                                if (mHaveEglContext && !mHaveEglSurface) {
                                    mHaveEglSurface = true;
                                    createEglSurface = true;
                                    createGlInterface = true;
                                    sizeChanged = true;
                                }

                                if (mHaveEglSurface) {
                                    if (mSizeChanged) {
                                        sizeChanged = true;
                                        w = mWidth;
                                        h = mHeight;
                                        wantRenderNotification = true;
                                        if (LOG_SURFACE) {
                                            Log.i("RajawaliGLThread", "noticing that we want render notification tid=" + getId());
                                        }

                                        // Destroy and recreate the EGL surface.
                                        createEglSurface = true;

                                        mSizeChanged = false;
                                    }
                                    mRequestRender = false;
                                    sGLThreadManager.notifyAll();
                                    break;
                                }
                            }

                            // By design, this is the only place in a RajawaliGLThread thread where we wait().
                            if (LOG_THREADS) {
                                Log.i("RajawaliGLThread", "waiting tid=" + getId()
                                    + " mHaveEglContext: " + mHaveEglContext
                                    + " mHaveEglSurface: " + mHaveEglSurface
                                    + " mFinishedCreatingEglSurface: " + mFinishedCreatingEglSurface
                                    + " mPaused: " + mPaused
                                    + " mHasSurface: " + mHasSurface
                                    + " mSurfaceIsBad: " + mSurfaceIsBad
                                    + " mWaitingForSurface: " + mWaitingForSurface
                                    + " mWidth: " + mWidth
                                    + " mHeight: " + mHeight
                                    + " mRequestRender: " + mRequestRender
                                    + " mRenderMode: " + mRenderMode);
                            }
                            sGLThreadManager.wait();
                        }
                    } // end of synchronized(sGLThreadManager)

                    if (event != null) {
                        event.run();
                        event = null;
                        continue;
                    }

                    if (createEglSurface) {
                        if (LOG_SURFACE) {
                            Log.w("RajawaliGLThread", "egl createSurface");
                        }
                        //if (mEglHelper.createSurface()) {
                        if (mWindowSurface != null) {
                            synchronized (sGLThreadManager) {
                                mFinishedCreatingEglSurface = true;
                                sGLThreadManager.notifyAll();
                            }
                        } else {
                            synchronized (sGLThreadManager) {
                                mFinishedCreatingEglSurface = true;
                                mSurfaceIsBad = true;
                                sGLThreadManager.notifyAll();
                            }
                            continue;
                        }
                        createEglSurface = false;
                    }

                    if (createGlInterface) {
                        //gl = (GL10) mEglHelper.createGL();
                        gl = null;

                        sGLThreadManager.checkGLDriver();
                        createGlInterface = false;
                    }

                    if (createEglContext) {
                        if (LOG_RENDERER) {
                            Log.w("RajawaliGLThread", "onSurfaceCreated");
                        }
                        RecordTextureView view = mRajawaliTextureViewWeakRef.get();
                        if (view != null) {
                            view.mRendererDelegate.mRenderer.onRenderSurfaceCreated(null, gl, -1, -1);
                        }
                        createEglContext = false;
                    }

                    if (sizeChanged) {
                        if (LOG_RENDERER) {
                            Log.w("RajawaliGLThread", "onSurfaceChanged(" + w + ", " + h + ")");
                        }
                        RecordTextureView view = mRajawaliTextureViewWeakRef.get();
                        if (view != null) {
                            view.mRendererDelegate.mRenderer.onRenderSurfaceSizeChanged(gl, w, h);
                        }
                        sizeChanged = false;
                    }

                    if (LOG_RENDERER_DRAW_FRAME) {
                        Log.w("RajawaliGLThread", "onDrawFrame tid=" + getId());
                    }
                    {
                        RecordTextureView view = mRajawaliTextureViewWeakRef.get();
                        if (view != null) {
                            view.mRendererDelegate.mRenderer.onRenderFrame(gl);
                        }
                    }
                    //int swapError = mEglHelper.swap();
                    mWindowSurface.swapBuffers();

//                    switch (swapError) {
//                        case EGL14.EGL_SUCCESS:
//                            break;
//                        case EGL14.EGL_CONTEXT_LOST:
//                            if (LOG_SURFACE) {
//                                Log.i("RajawaliGLThread", "egl context lost tid=" + getId());
//                            }
//                            lostEglContext = true;
//                            break;
//                        default:
//                            // Other errors typically mean that the current surface is bad,
//                            // probably because the SurfaceView surface has been destroyed,
//                            // but we haven't been notified yet.
//                            // Log the error to help developers understand why rendering stopped.
//                            EglHelper.logEglErrorAsWarning("RajawaliGLThread", "eglSwapBuffers", swapError);
//
//                            synchronized (sGLThreadManager) {
//                                mSurfaceIsBad = true;
//                                sGLThreadManager.notifyAll();
//                            }
//                            break;
//                    }

                    if (wantRenderNotification) {
                        doRenderNotification = true;
                    }
                }

            } finally {
                /*
                 * clean-up everything...
                 */
                synchronized (sGLThreadManager) {
                    stopEglSurfaceLocked();
                    stopEglContextLocked();
                }
            }
        }

        public boolean ableToDraw() {
            return mHaveEglContext && mHaveEglSurface && readyToDraw();
        }

        private boolean readyToDraw() {
            return (!mPaused) && mHasSurface && (!mSurfaceIsBad)
                && (mWidth > 0) && (mHeight > 0)
                && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY));
        }

        public void setRenderMode(int renderMode) {
            if (!((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY))) {
                throw new IllegalArgumentException("renderMode");
            }
            synchronized (sGLThreadManager) {
                mRenderMode = renderMode;
                sGLThreadManager.notifyAll();
            }
        }

        public int getRenderMode() {
            synchronized (sGLThreadManager) {
                return mRenderMode;
            }
        }

        public void requestRender() {
            synchronized (sGLThreadManager) {
                mRequestRender = true;
                sGLThreadManager.notifyAll();
            }
        }

        public void surfaceCreated(int w, int h) {
            synchronized (sGLThreadManager) {
                if (LOG_THREADS) {
                    Log.i("RajawaliGLThread", "surfaceCreated tid=" + getId());
                }
                mHasSurface = true;
                mWidth = w;
                mHeight = h;
                mFinishedCreatingEglSurface = false;
                sGLThreadManager.notifyAll();
                while (mWaitingForSurface
                    && !mFinishedCreatingEglSurface
                    && !mExited) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void surfaceDestroyed() {
            synchronized (sGLThreadManager) {
                if (LOG_THREADS) {
                    Log.i("RajawaliGLThread", "surfaceDestroyed tid=" + getId());
                }
                mHasSurface = false;
                sGLThreadManager.notifyAll();
                while ((!mWaitingForSurface) && (!mExited)) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onPause() {
            synchronized (sGLThreadManager) {
                if (LOG_PAUSE_RESUME) {
                    Log.i("RajawaliGLThread", "onPause tid=" + getId());
                }
                mRequestPaused = true;
                sGLThreadManager.notifyAll();
                while ((!mExited) && (!mPaused)) {
                    if (LOG_PAUSE_RESUME) {
                        Log.i("Main thread", "onPause waiting for mPaused.");
                    }
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onResume() {
            synchronized (sGLThreadManager) {
                if (LOG_PAUSE_RESUME) {
                    Log.i("RajawaliGLThread", "onResume tid=" + getId());
                }
                mRequestPaused = false;
                mRequestRender = true;
                mRenderComplete = false;
                sGLThreadManager.notifyAll();
                while ((!mExited) && mPaused && (!mRenderComplete)) {
                    if (LOG_PAUSE_RESUME) {
                        Log.i("Main thread", "onResume waiting for !mPaused.");
                    }
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onWindowResize(int w, int h) {
            synchronized (sGLThreadManager) {
                mWidth = w;
                mHeight = h;
                mSizeChanged = true;
                mRequestRender = true;
                mRenderComplete = false;
                sGLThreadManager.notifyAll();

                // Wait for thread to react to resize and render a frame
                while (!mExited && !mPaused && !mRenderComplete
                    && ableToDraw()) {
                    if (LOG_SURFACE) {
                        Log.i("Main thread", "onWindowResize waiting for render complete from tid=" + getId());
                    }
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestExitAndWait() {
            // don't call this from RajawaliGLThread thread or it is a guaranteed
            // deadlock!
            synchronized (sGLThreadManager) {
                mShouldExit = true;
                sGLThreadManager.notifyAll();
                while (!mExited) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestReleaseEglContextLocked() {
            mShouldReleaseEglContext = true;
            sGLThreadManager.notifyAll();
        }

        /**
         * Queue an "event" to be run on the GL rendering thread.
         *
         * @param r the runnable to be run on the GL rendering thread.
         */
        public void queueEvent(Runnable r) {
            if (r == null) {
                throw new IllegalArgumentException("r must not be null");
            }
            synchronized (sGLThreadManager) {
                mEventQueue.add(r);
                sGLThreadManager.notifyAll();
            }
        }
    }

    public static class GLThreadManager {
        private static String TAG = "RajawaliGLThreadManager";

        private boolean mGLESVersionCheckComplete;
        private int mGLESVersion;
        private boolean mGLESDriverCheckComplete;
        private boolean mMultipleGLESContextsAllowed;
        private boolean mLimitedGLESContexts;
        private static final int kGLES_20 = 0x20000;
        private static final String kMSM7K_RENDERER_PREFIX = "Q3Dimension MSM7500 ";
        private GLThread mEglOwner;

        public synchronized void threadExiting(GLThread thread) {
            if (LOG_THREADS) {
                Log.i("RajawaliGLThread", "exiting tid=" + thread.getId());
            }
            thread.mExited = true;
            if (mEglOwner == thread) {
                mEglOwner = null;
            }
            notifyAll();
        }

        /*
         * Tries once to acquire the right to use an EGL
         * context. Does not block. Requires that we are already
         * in the sGLThreadManager monitor when this is called.
         *
         * @return true if the right to use an EGL context was acquired.
         */
        public boolean tryAcquireEglContextLocked(GLThread thread) {
            if (mEglOwner == thread || mEglOwner == null) {
                mEglOwner = thread;
                notifyAll();
                return true;
            }
            checkGLESVersion();
            if (mMultipleGLESContextsAllowed) {
                return true;
            }
            // Notify the owning thread that it should release the context.
            // TODO: implement a fairness policy. Currently
            // if the owning thread is drawing continuously it will just
            // reacquire the EGL context.
            if (mEglOwner != null) {
                mEglOwner.requestReleaseEglContextLocked();
            }
            return false;
        }

        /*
         * Releases the EGL context. Requires that we are already in the
         * sGLThreadManager monitor when this is called.
         */
        public void releaseEglContextLocked(GLThread thread) {
            if (mEglOwner == thread) {
                mEglOwner = null;
            }
            notifyAll();
        }

        public synchronized boolean shouldReleaseEGLContextWhenPausing() {
            // Release the EGL context when pausing even if
            // the hardware supports multiple EGL contexts.
            // Otherwise the device could run out of EGL contexts.
            return mLimitedGLESContexts;
        }

        public synchronized boolean shouldTerminateEGLWhenPausing() {
            checkGLESVersion();
            return !mMultipleGLESContextsAllowed;
        }

        private void checkGLESVersion() {
            if (!mGLESVersionCheckComplete) {
                mGLESVersion = Capabilities.getGLESMajorVersion();
                if (mGLESVersion >= kGLES_20) {
                    mMultipleGLESContextsAllowed = true;
                }
                if (LOG_SURFACE) {
                    Log.w(TAG, "checkGLESVersion mGLESVersion =" +
                        " " + mGLESVersion + " mMultipleGLESContextsAllowed = " + mMultipleGLESContextsAllowed);
                }
                mGLESVersionCheckComplete = true;
            }
        }

        public synchronized void checkGLDriver() {
            if (!mGLESDriverCheckComplete) {
                checkGLESVersion();
                String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
                if (mGLESVersion < kGLES_20) {
                    mMultipleGLESContextsAllowed =
                        !renderer.startsWith(kMSM7K_RENDERER_PREFIX);
                    notifyAll();
                }
                mLimitedGLESContexts = !mMultipleGLESContextsAllowed;
                if (LOG_SURFACE) {
                    Log.w(TAG, "checkGLDriver renderer = \"" + renderer + "\" multipleContextsAllowed = "
                        + mMultipleGLESContextsAllowed
                        + " mLimitedGLESContexts = " + mLimitedGLESContexts);
                }
                mGLESDriverCheckComplete = true;
            }
        }
    }
}
