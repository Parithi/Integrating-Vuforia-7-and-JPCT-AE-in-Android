package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Object3D;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.World;
import com.threed.jpct.util.MemoryHelper;
import com.vuforia.CameraCalibration;
import com.vuforia.Device;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleAppRenderer;
import com.vuforia.samples.SampleApplication.SampleAppRendererControl;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleMath;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ImageTargetRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "ImageTargetRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private ImageTargets mActivity;
    private SampleAppRenderer mSampleAppRenderer;

    private boolean mIsActive = false;

    private World world = null;
    private Light sun = null;
    public Object3D obj;
    public Texture texture;
    private FrameBuffer fb = null;
    private GL10 lastGl = null;
    private Camera cam;
    private float[] modelViewMat;
    private float fov;
    private float fovy;

    public ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, -1.0f, 10000000f);

        world = new World();
        world.setAmbientLight(200, 200, 200);

        sun = new Light(world);
        sun.setIntensity(250, 250, 250);

        Object3D[] object3Darray = new Object3D[0];
        try {
            object3Darray = Loader.load3DS(mActivity.getAssets().open("Audi_S3.3DS"),0.25f);
        } catch (IOException e) {
            e.printStackTrace();
        }

        obj = Object3D.mergeAll(object3Darray);
        obj.strip();
        obj.build();

        world.addObject(obj);

        cam = world.getCamera();
//        cam.moveCamera(Camera.CAMERA_MOVELEFT,2);
//        cam.lookAt(obj.getTransformedCenter());

        SimpleVector sv = new SimpleVector();
        sv.set(obj.getTransformedCenter());
        sv.y -= 100;
        sv.z -= 100;
        sun.setPosition(sv);
        MemoryHelper.compact();
    }


    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        obj.rotateZ(0.025f);
        mSampleAppRenderer.render();

        updateCamera();
        world.renderScene(fb);
        world.draw(fb);
        fb.display();
    }


    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        vuforiaAppSession.onSurfaceCreated();
        mSampleAppRenderer.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        vuforiaAppSession.onSurfaceChanged(w, h);

        mSampleAppRenderer.onConfigurationChanged(mIsActive);
        initRendering();

        if (lastGl != gl) {
            if (fb != null) {
                fb.dispose();
            }
            fb = new FrameBuffer(w, h);
            Config.viewportOffsetAffectsRenderTarget = true;

            fb.setVirtualDimensions(fb.getWidth(), fb.getHeight());
            lastGl = gl;
        } else {
            fb.resize(w, h);
            fb.setVirtualDimensions(w, h);
        }
    }

    private void initRendering()
    {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        mActivity.loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }

    public void updateConfiguration()
    {
        mSampleAppRenderer.onConfigurationChanged(mIsActive);
        CameraCalibration camCalibration = com.vuforia.CameraDevice.getInstance().getCameraCalibration();
        Vec2F size = camCalibration.getSize();
        Vec2F focalLength = camCalibration.getFocalLength();
        float fovyRadians = (float) (2 * Math.atan(0.5f * size.getData()[1] / focalLength.getData()[1]));
        float fovRadians = (float) (2 * Math.atan(0.5f * size.getData()[0] / focalLength.getData()[0]));

        if (mSampleAppRenderer.mIsPortrait) {
            setFovy(fovRadians);
            setFov(fovyRadians);
        } else {
            setFov(fovRadians);
            setFovy(fovyRadians);
        }
    }

    public void renderFrame(State state, float[] projectionMatrix)
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        mSampleAppRenderer.renderVideoBackground();

        float[] modelviewArray = new float[16];

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            printUserData(trackable);

            Matrix44F modelViewMatrix = Tool.convertPose2GLMatrix(result.getPose());
            Matrix44F inverseMV = SampleMath.Matrix44FInverse(modelViewMatrix);
            Matrix44F invTranspMV = SampleMath.Matrix44FTranspose(inverseMV);

            modelviewArray = invTranspMV.getData();
            updateModelviewMatrix(modelviewArray);

        }

        if (state.getNumTrackableResults() == 0) {
            float m [] = {
                    1,0,0,0,
                    0,1,0,0,
                    0,0,1,0,
                    0,0,-10000,1
            };
            modelviewArray = m;
            updateModelviewMatrix(modelviewArray);
        }

        Renderer.getInstance().end();
    }

    private void updateModelviewMatrix(float mat[]) {
        modelViewMat = mat;
    }

    private void printUserData(Trackable trackable)
    {
        String userData = (String) trackable.getUserData();
    }


    private void updateCamera() {
        if (modelViewMat != null) {
            float[] m = modelViewMat;

            final SimpleVector camUp;
            if (mSampleAppRenderer.mIsPortrait) {
                camUp = new SimpleVector(-m[0], -m[1], -m[2]);
            } else {
                camUp = new SimpleVector(-m[4], -m[5], -m[6]);
            }

            final SimpleVector camDirection = new SimpleVector(m[8], m[9], m[10]);
            final SimpleVector camPosition = new SimpleVector(m[12], m[13], m[14]);

            cam.setOrientation(camDirection, camUp);
            cam.setPosition(camPosition);

            cam.setFOV(fov);
            cam.setYFOV(fovy);
        }
    }

    private void setFov(float fov) {
        this.fov = fov;
    }

    private void setFovy(float fovy) {
        this.fovy = fovy;
    }

}
