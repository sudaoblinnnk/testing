package org.rajawali3d.examples.examples.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.airbnb.lottie.LottieAnimationView;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.examples.R;
import org.rajawali3d.examples.examples.AExampleFragment;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;

public class LottieTextureFragment extends AExampleFragment {

    FragmentToDraw mFragmentToDraw;
    Handler mHandler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());

        final FrameLayout fragmentFrame = new FrameLayout(getActivity());
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentFrame.setLayoutParams(params);
        fragmentFrame.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_bright));
        fragmentFrame.setId(R.id.lottie_to_texture_frame);
        fragmentFrame.setVisibility(View.INVISIBLE);
        mLayout.addView(fragmentFrame);

        mFragmentToDraw = new FragmentToDraw();
        getActivity().getSupportFragmentManager().beginTransaction().add(R.id.lottie_to_texture_frame, mFragmentToDraw, "custom").commit();
        return mLayout;
    }

    @Override
    public AExampleRenderer createRenderer() {
        return new ViewTextureRenderer(getActivity(), this);
    }

    @Override
    protected void onBeforeApplyRenderer() {
        super.onBeforeApplyRenderer();
    }

    private final class ViewTextureRenderer extends AExampleRenderer implements StreamingTexture.ISurfaceListener {
        private int mFrameCount;
        private Surface mSurface;
        private StreamingTexture mStreamingTexture;
        private volatile boolean mShouldUpdateTexture;

        private final float[] mMatrix = new float[16];

        private Object3D mObject3D;

        public ViewTextureRenderer(Context context, @Nullable AExampleFragment fragment) {
            super(context, fragment);
        }

        @Override
        public void initScene() {
            Plane screen = new Plane(3, 2, 2, 2, Vector3.Axis.Z);

            mStreamingTexture = new StreamingTexture("lottie_viewTexture", this);
            Material material = new Material();
            material.setColorInfluence(0);
            try {
                material.addTexture(mStreamingTexture);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }

            screen.setMaterial(material);
            screen.setX(.1f);
            screen.setY(-.2f);
            screen.setZ(1.5f);
            getCurrentScene().addChild(screen);

            getCurrentCamera().enableLookAt();
            getCurrentCamera().setLookAt(0, 0, 0);
        }

        final Runnable mUpdateTexture = new Runnable() {
            public void run() {
                // -- Draw the view on the canvas
                final Canvas canvas = mSurface.lockCanvas(null);
                mStreamingTexture.getSurfaceTexture().getTransformMatrix(mMatrix);
                mFragmentToDraw.getView().draw(canvas);
                mSurface.unlockCanvasAndPost(canvas);
                // -- Indicates that the texture should be updated on the OpenGL thread.
                mShouldUpdateTexture = true;
            }
        };

        @Override
        protected void onRender(long ellapsedRealtime, double deltaTime) {
            // -- not a really accurate way of doing things but you get the point :)
            if (mSurface != null && mFrameCount++ >= (mFrameRate * 0.25)) {
                mFrameCount = 0;
                mHandler.post(mUpdateTexture);
            }
            // -- update the texture because it is ready
            if (mShouldUpdateTexture) {
                mStreamingTexture.update();
                mShouldUpdateTexture = false;
            }
            super.onRender(ellapsedRealtime, deltaTime);
        }

        @Override
        public void setSurface(Surface surface) {
            mSurface = surface;
            mStreamingTexture.getSurfaceTexture().setDefaultBufferSize(1024, 1024);
        }
    }

    public static final class FragmentToDraw extends Fragment {

        LottieAnimationView av;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.lottie_to_texture, container, false);
            return view;
        }
    }
}
