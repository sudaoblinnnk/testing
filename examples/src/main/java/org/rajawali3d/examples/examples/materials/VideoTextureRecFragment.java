package org.rajawali3d.examples.examples.materials;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources.NotFoundException;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.EllipticalOrbitAnimation3D;
import org.rajawali3d.animation.TranslateAnimation3D;
import org.rajawali3d.cameras.Camera;
import org.rajawali3d.examples.R;
import org.rajawali3d.examples.examples.AExampleFragment;
import org.rajawali3d.examples.examples.EglCore;
import org.rajawali3d.examples.examples.GlUtil;
import org.rajawali3d.examples.examples.TextureMovieEncoder2;
import org.rajawali3d.examples.examples.VideoEncoderCore;
import org.rajawali3d.examples.examples.WindowSurface;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.view.TextureView;

import java.io.File;
import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VideoTextureRecFragment extends AExampleFragment   {
    //implements Choreographer.FrameCallback {

    private static final String TAG = "kurt";



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        return super.onCreateView(inflater, container, savedInstanceState);

    }

    @Override
    public AExampleRenderer createRenderer() {
        return new VideoTextureRenderer(getActivity());
    }

//  @Override
//  public void doFrame(long frameTimeNanos) {
//    RecordFBOActivity.RenderHandler rh = mRenderThread.getHandler();
//    if (rh != null) {
//      Choreographer.getInstance().postFrameCallback(this);
//      rh.sendDoFrame(frameTimeNanos);
//    }
//  }

    private static final int RECMETHOD_DRAW_TWICE = 0;
    private static final int RECMETHOD_FBO = 1;
    private static final int RECMETHOD_BLIT_FRAMEBUFFER = 2;

    private final class VideoTextureRenderer extends AExampleRenderer {
        private MediaPlayer mMediaPlayer;
        private StreamingTexture mVideoTexture;

        // private volatile RenderHandler mHandler;
        private int mRecordMethod = RECMETHOD_BLIT_FRAMEBUFFER;
        private boolean mRecordedPrevious;
        private Rect mVideoRect;

        private WindowSurface mInputWindowSurface;
        private TextureMovieEncoder2 mVideoEncoder;
        private EglCore mEglCore;
        private WindowSurface mWindowSurface;

        // FPS / drop counter.
        private long mRefreshPeriodNanos;
        private long mFpsCountStartNanos;
        private int mFpsCountFrame;
        private int mDroppedFrames;
        private boolean mPreviousWasDropped;

        // Used for recording.
        private boolean mRecordingEnabled;

        public VideoTextureRenderer(Context context) {
            super(context, VideoTextureRecFragment.this);
        }

        @Override
        public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
            super.onRenderSurfaceCreated(config, gl, width, height);
            Log.d(TAG, "onRenderSurfaceCreated w : " + width + " h : " + height);
//
//            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);
//            mVideoTexture = new StreamingTexture("sintelTrailer", mMediaPlayer);
//            if (mVideoTexture.getSurfaceTexture() == null) {
//                throw new NullPointerException("empty texture kurt.");
//            }
//            mWindowSurface = new WindowSurface(mEglCore, mVideoTexture.getSurfaceTexture());
//            mWindowSurface.makeCurrent();
        }


        //    public RenderHandler getHandler() {
//      return mHandler;
//    }

        private void background() {
            Camera mCamera = getCurrentCamera();
            final DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(displayMetrics);
            final int width = displayMetrics.widthPixels;
            final int height = displayMetrics.heightPixels;

            final float tan = (float) (Math.tan(0.5 * Math.toRadians(mCamera.getFieldOfView())));
            final float far = (float) mCamera.getFarPlane();
            mCamera.setZ(far);
            mCamera.setLookAt(0, 0, 0);
            final float planeH = tan * far * 2;
            final float aspect = (float) width / (float) height;
            float planeW = planeH;
            if (getContext().getResources().getConfiguration().orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                planeW = planeH / aspect;
            else
                planeW = planeH * aspect;

            Plane mPlane = new Plane(50, 50, 6, 6);
            mPlane.setZ(-30);
            //Plane mPlane = new Plane(planeW, planeH, 1, 1);


            Material material = new Material();
            material.enableLighting(true);
            //material.setDiffuseMethod(new DiffuseMethod.Lambert());
            material.setColorInfluence(0);

            //material.setSpecularMethod(new SpecularMethod.Phong());
            try {
                material.addTexture(new Texture("back", R.drawable.ground));
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }
            // mPlane.setColor(0xff99C224);
            mPlane.setMaterial(material);

            getCurrentScene().addChild(mPlane);
        }

        @Override
        protected void initScene() {

            PointLight pointLight = new PointLight();
            pointLight.setPower(1);
            pointLight.setPosition(-1, 1, 4);

            getCurrentScene().addLight(pointLight);
            getCurrentScene().setBackgroundColor(0xff040404);

            Object3D android = new Cube(3.0f);
            try {
                android.setY(3);
                Material material = new Material();
                try {
                    material.addTexture(new Texture("baby1", R.drawable.particle));
                } catch (ATexture.TextureException e) {
                    e.printStackTrace();
                }
                material.enableLighting(true);
                material.setDiffuseMethod(new DiffuseMethod.Lambert());
                material.setSpecularMethod(new SpecularMethod.Phong());
                android.setMaterial(material);
                // android.setColor(0xff99C224);
                getCurrentScene().addChild(android);
            } catch (NotFoundException e) {
                e.printStackTrace();
            }

            mMediaPlayer = MediaPlayer.create(getContext(),
                    R.raw.sintel_trailer_480p);
            mMediaPlayer.setLooping(true);

            mVideoTexture = new StreamingTexture("sintelTrailer", mMediaPlayer);

            Material material = new Material();
            material.setColorInfluence(0);
            try {
                material.addTexture(mVideoTexture);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }

            Plane screen = new Plane(3, 2, 2, 2, Vector3.Axis.Z);
            screen.setMaterial(material);
            // screen.setRotY(180);
            screen.setX(.1f);
            screen.setY(-1.2f);
            screen.setZ(1.5f);
            getCurrentScene().addChild(screen);

            android.setMaterial(material);

            getCurrentCamera().enableLookAt();
            getCurrentCamera().setLookAt(0, 0, 0);

            // -- animate the spot light

            TranslateAnimation3D lightAnim = new TranslateAnimation3D(
                    new Vector3(-3, 3, 10), // from
                    new Vector3(3, 1, 3)); // to
            lightAnim.setDurationMilliseconds(5000);
            lightAnim.setRepeatMode(Animation.RepeatMode.REVERSE_INFINITE);
            lightAnim.setTransformable3D(pointLight);
            lightAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            getCurrentScene().registerAnimation(lightAnim);
            lightAnim.play();

            // -- animate the camera

            EllipticalOrbitAnimation3D camAnim = new EllipticalOrbitAnimation3D(
                    new Vector3(3, 2, 10), new Vector3(1, 0, 8), 0, 359);
            camAnim.setDurationMilliseconds(20000);
            camAnim.setRepeatMode(Animation.RepeatMode.INFINITE);
            camAnim.setTransformable3D(getCurrentCamera());
            getCurrentScene().registerAnimation(camAnim);
            camAnim.play();

            background();

            mMediaPlayer.start();
        }

        @Override
        protected void onRender(long ellapsedRealtime, double deltaTime) {
            super.onRender(ellapsedRealtime, deltaTime);

            mVideoTexture.update();
//            if (mEglCore == null) {
//                mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);
//                mWindowSurface = new WindowSurface(mEglCore, mVideoTexture.getSurfaceTexture());
//                mWindowSurface.makeCurrent();
//
//                startEncoder();
//                Log.d(TAG, "kurt startEncoder ");
//            }
            doFrame(0);

        }

        @Override
        public void onPause() {
            super.onPause();
            //Choreographer.getInstance().removeFrameCallback(this);

            if (mMediaPlayer != null)
                mMediaPlayer.pause();
        }

        @Override
        public void onResume() {
            super.onResume();
            //Choreographer.getInstance().postFrameCallback(this);

            if (mMediaPlayer != null)
                mMediaPlayer.start();
        }

        @Override
        public void onRenderSurfaceDestroyed(SurfaceTexture surfaceTexture) {
            super.onRenderSurfaceDestroyed(surfaceTexture);
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }


        /**
         * Changes the method we use to render frames to the encoder.
         */
        private void setRecordMethod(int recordMethod) {
            Log.d(TAG, "RT: setRecordMethod " + recordMethod);
            mRecordMethod = recordMethod;
        }

        /**
         * Creates the video encoder object and starts the encoder thread.  Creates an EGL
         * surface for encoder input.
         */
        private void startEncoder() {
            Log.d(TAG, "starting to record");
            // Record at 1280x720, regardless of the window dimensions.  The encoder may
            // explode if given "strange" dimensions, e.g. a width that is not a multiple
            // of 16.  We can box it as needed to preserve dimensions.
            final int BIT_RATE = 4000000;   // 4Mbps
            final int VIDEO_WIDTH = 1280;
            final int VIDEO_HEIGHT = 720;
            int windowWidth = mDefaultViewportWidth;//mWindowSurface.getWidth();
            int windowHeight = mDefaultViewportHeight;//mWindowSurface.getHeight();
            float windowAspect = (float) windowHeight / (float) windowWidth;
            int outWidth, outHeight;
            if (VIDEO_HEIGHT > VIDEO_WIDTH * windowAspect) {
                // limited by narrow width; reduce height
                outWidth = VIDEO_WIDTH;
                outHeight = (int) (VIDEO_WIDTH * windowAspect);
            } else {
                // limited by short height; restrict width
                outHeight = VIDEO_HEIGHT;
                outWidth = (int) (VIDEO_HEIGHT / windowAspect);
            }
            int offX = (VIDEO_WIDTH - outWidth) / 2;
            int offY = (VIDEO_HEIGHT - outHeight) / 2;
            mVideoRect.set(offX, offY, offX + outWidth, offY + outHeight);
            Log.d(TAG, "Adjusting window " + windowWidth + "x" + windowHeight +
                    " to +" + offX + ",+" + offY + " " +
                    mVideoRect.width() + "x" + mVideoRect.height());


            File outputFile = new File(Environment.getExternalStorageDirectory(), "fbo-gl-recording.mp4");
            //File outputFile = new File(VideoTextureRecFragment.this.getActivity().getFilesDir(), "fbo-gl-recording.mp4");
            VideoEncoderCore encoderCore;
            try {
                encoderCore = new VideoEncoderCore(VIDEO_WIDTH, VIDEO_HEIGHT,
                        BIT_RATE, outputFile);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mInputWindowSurface = new WindowSurface(mEglCore, encoderCore.getInputSurface(), true);
            mVideoEncoder = new TextureMovieEncoder2(encoderCore);
        }

        /**
         * Stops the video encoder if it's running.
         */
        private void stopEncoder() {
            if (mVideoEncoder != null) {
                Log.d(TAG, "stopping recorder, mVideoEncoder=" + mVideoEncoder);
                mVideoEncoder.stopRecording();
                // TODO: wait (briefly) until it finishes shutting down so we know file is
                //       complete, or have a callback that updates the UI
                mVideoEncoder = null;
            }

            if (mInputWindowSurface != null) {
                mInputWindowSurface.release();
                mInputWindowSurface = null;
            }
        }

        /**
         * Advance state and draw frame in response to a vsync event.
         */
        private void doFrame(long timeStampNanos) {
            // If we're not keeping up 60fps -- maybe something in the system is busy, maybe
            // recording is too expensive, maybe the CPU frequency governor thinks we're
            // not doing and wants to drop the clock frequencies -- we need to drop frames
            // to catch up.  The "timeStampNanos" value is based on the system monotonic
            // clock, as is System.nanoTime(), so we can compare the values directly.
            //
            // Our clumsy collision detection isn't sophisticated enough to deal with large
            // time gaps, but it's nearly cost-free, so we go ahead and do the computation
            // either way.
            //
            // We can reduce the overhead of recording, as well as the size of the movie,
            // by recording at ~30fps instead of the display refresh rate.  As a quick hack
            // we just record every-other frame, using a "recorded previous" flag.

            //update(timeStampNanos);

            long diff = System.nanoTime() - timeStampNanos;
            long max = mRefreshPeriodNanos - 2000000;   // if we're within 2ms, don't bother
            if (diff > max) {
                // too much, drop a frame
                Log.d(TAG, "diff is " + (diff / 1000000.0) + " ms, max " + (max / 1000000.0) +
                        ", skipping render");
                mRecordedPrevious = false;
                mPreviousWasDropped = true;
                mDroppedFrames++;
                //return;
            }

            boolean swapResult;

//      if (!mRecordingEnabled || mRecordedPrevious) {
//        mRecordedPrevious = false;
//        // Render the scene, swap back to front.
//        draw();
//        swapResult = mWindowSurface.swapBuffers();
//      } else
            {
                mRecordedPrevious = true;

                // recording
                if (//mEglCore.getGlVersion() >= 3 &&
                        mRecordMethod == RECMETHOD_BLIT_FRAMEBUFFER) {
                    //Log.d(TAG, "MODE: blitFramebuffer");
                    // Draw the frame, but don't swap it yet.
                    //draw();

                    mVideoEncoder.frameAvailableSoon();
//
//                    TextureView view = mRajawaliTextureViewWeakRef.get();
//                    if (view != null) {
//                        view.mRendererDelegate.mRenderer.onRenderSurfaceCreated(mEglHelper.mEglConfig, gl, -1, -1);
//                    }

                    //render get GLThread, thread get mEglHelper
                    if (this.exampleFragment.mTextureView.mGLThread.mEglHelper != null) {

                        mInputWindowSurface.makeCurrentReadFrom(this.exampleFragment.mTextureView.mGLThread.mEglHelper.getEglSurface());
                    }
                    //mInputWindowSurface.makeCurrentReadFrom();
                    // Clear the pixels we're not going to overwrite with the blit.  Once again,
                    // this is excessive -- we don't need to clear the entire screen.
                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GlUtil.checkGlError("before glBlitFramebuffer");
                    Log.v(TAG, "glBlitFramebuffer: 0,0," + mWindowSurface.getWidth() + "," +
                            mWindowSurface.getHeight() + "  " + mVideoRect.left + "," +
                            mVideoRect.top + "," + mVideoRect.right + "," + mVideoRect.bottom +
                            "  COLOR_BUFFER GL_NEAREST");
                    GLES30.glBlitFramebuffer(
                            0, 0, mWindowSurface.getWidth(), mWindowSurface.getHeight(),
                            mVideoRect.left, mVideoRect.top, mVideoRect.right, mVideoRect.bottom,
                            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
                    int err;
                    if ((err = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
                        Log.w(TAG, "ERROR: glBlitFramebuffer failed: 0x" +
                                Integer.toHexString(err));
                    }
                    mInputWindowSurface.setPresentationTime(timeStampNanos);
                    mInputWindowSurface.swapBuffers();
                    Log.d(TAG, "kurt doFrame ");
                }
            }

            mPreviousWasDropped = false;

//      if (!swapResult) {
//        // This can happen if the Activity stops without waiting for us to halt.
//        Log.w(TAG, "swapBuffers failed, killing renderer thread");
//        shutdown();
//        return;
//      }

            // Update the FPS counter.
            //
            // Ideally we'd generate something approximate quickly to make the UI look
            // reasonable, then ease into longer sampling periods.
            final int NUM_FRAMES = 120;
            final long ONE_TRILLION = 1000000000000L;
            if (mFpsCountStartNanos == 0) {
                mFpsCountStartNanos = timeStampNanos;
                mFpsCountFrame = 0;
            } else {
                mFpsCountFrame++;
                if (mFpsCountFrame == NUM_FRAMES) {
                    // compute thousands of frames per second
                    long elapsed = timeStampNanos - mFpsCountStartNanos;
                    //mActivityHandler.sendFpsUpdate((int)(NUM_FRAMES * ONE_TRILLION / elapsed), mDroppedFrames);

                    // reset
                    mFpsCountStartNanos = timeStampNanos;
                    mFpsCountFrame = 0;
                }
            }
        }
    }


    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     * <p>
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
//  private static class RenderHandler extends Handler {
//    private static final int MSG_SURFACE_CREATED = 0;
//    private static final int MSG_SURFACE_CHANGED = 1;
//    private static final int MSG_DO_FRAME = 2;
//    private static final int MSG_RECORDING_ENABLED = 3;
//    private static final int MSG_RECORD_METHOD = 4;
//    private static final int MSG_SHUTDOWN = 5;
//
//    // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
//    // but no real harm in it.
//    private WeakReference<VideoTextureRenderer> mWeakRenderThread;
//
//    /**
//     * Call from render thread.
//     */
//    public RenderHandler(VideoTextureRenderer rt) {
//      mWeakRenderThread = new WeakReference<VideoTextureRenderer>(rt);
//    }
//
//    /**
//     * Sends the "surface created" message.
//     * <p>
//     * Call from UI thread.
//     */
//    public void sendSurfaceCreated() {
//      sendMessage(obtainMessage(RecordFBOActivity.RenderHandler.MSG_SURFACE_CREATED));
//    }
//
//    /**
//     * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
//     * <p>
//     * Call from UI thread.
//     */
//    public void sendSurfaceChanged(@SuppressWarnings("unused") int format,
//                                   int width, int height) {
//      // ignore format
//      sendMessage(obtainMessage(RecordFBOActivity.RenderHandler.MSG_SURFACE_CHANGED, width, height));
//    }
//
//    /**
//     * Sends the "do frame" message, forwarding the Choreographer event.
//     * <p>
//     * Call from UI thread.
//     */
//    public void sendDoFrame(long frameTimeNanos) {
//      sendMessage(obtainMessage(RecordFBOActivity.RenderHandler.MSG_DO_FRAME,
//        (int) (frameTimeNanos >> 32), (int) frameTimeNanos));
//    }
//
//    /**
//     * Enable or disable recording.
//     * <p>
//     * Call from non-UI thread.
//     */
//    public void setRecordingEnabled(boolean enabled) {
//      sendMessage(obtainMessage(MSG_RECORDING_ENABLED, enabled ? 1 : 0, 0));
//    }
//
//    /**
//     * Set the method used to render a frame for the encoder.
//     * <p>
//     * Call from non-UI thread.
//     */
//    public void setRecordMethod(int recordMethod) {
//      sendMessage(obtainMessage(MSG_RECORD_METHOD, recordMethod, 0));
//    }
//
//    /**
//     * Sends the "shutdown" message, which tells the render thread to halt.
//     * <p>
//     * Call from UI thread.
//     */
//    public void sendShutdown() {
//      sendMessage(obtainMessage(RecordFBOActivity.RenderHandler.MSG_SHUTDOWN));
//    }
//
//    @Override  // runs on RenderThread
//    public void handleMessage(Message msg) {
//      int what = msg.what;
//      //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);
//
//      RecordFBOActivity.RenderThread renderThread = mWeakRenderThread.get();
//      if (renderThread == null) {
//        Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
//        return;
//      }
//
//      switch (what) {
//        case MSG_SURFACE_CREATED:
//          renderThread.surfaceCreated();
//          break;
//        case MSG_SURFACE_CHANGED:
//          renderThread.surfaceChanged(msg.arg1, msg.arg2);
//          break;
//        case MSG_DO_FRAME:
//          long timestamp = (((long) msg.arg1) << 32) |
//            (((long) msg.arg2) & 0xffffffffL);
//          renderThread.doFrame(timestamp);
//          break;
//        case MSG_RECORDING_ENABLED:
//          renderThread.setRecordingEnabled(msg.arg1 != 0);
//          break;
//        case MSG_RECORD_METHOD:
//          renderThread.setRecordMethod(msg.arg1);
//          break;
//        case MSG_SHUTDOWN:
//          renderThread.shutdown();
//          break;
//        default:
//          throw new RuntimeException("unknown message " + what);
//      }
//    }
//  }
}
