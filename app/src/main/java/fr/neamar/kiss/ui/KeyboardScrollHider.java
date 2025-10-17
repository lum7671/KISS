package fr.neamar.kiss.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.NonNull;

/**
 * Utility class for automatically hiding the keyboard when scrolling down a {@link android.widget.ListView},
 * keeping the position of the finger on the list stable
 */
public class KeyboardScrollHider implements View.OnTouchListener {
    private final static int THRESHOLD = 24;

    private final KeyboardHandler handler;
    private final BlockableListView list;
    private final View listParent;
    private final BottomPullEffectView pullEffect;
    private int listHeightInitial = 0;

    private float offsetYStart = 0;
    private float offsetYCurrent = 0;
    private int offsetYDiff = 0;

    private MotionEvent lastMotionEvent;
    private int initialWindowPadding = 0;
    private boolean resizeDone = false;

    private boolean scrollBarEnabled = true;

    public KeyboardScrollHider(KeyboardHandler handler, BlockableListView list, BottomPullEffectView pullEffect) {
        this.handler = handler;
        this.list = list;
        this.listParent = (View) list.getParent();
        this.pullEffect = pullEffect;
    }

    /**
     * Start monitoring and intercepting touch events of the target list view and providing our
     * transformations
     */
    public void start() {
        this.list.setOnTouchListener(this);
    }

    /**
     *
     */
    @SuppressWarnings("unused")
    public void stop() {
        this.list.setOnTouchListener(null);
        this.handleResizeDone();
    }

    private int getWindowPadding() {
        ViewGroup rootView = (ViewGroup) this.list.getRootView();
        return rootView.getChildAt(0).getPaddingBottom();
    }

    private int getWindowWidth() {
        ViewGroup rootView = (ViewGroup) this.list.getRootView();
        return rootView.getChildAt(0).getWidth();
    }

    private void setListLayoutHeight(int height) {
        final ViewGroup.LayoutParams params = this.list.getLayoutParams();
        params.height = height;
        this.list.setLayoutParams(params);
        this.list.forceLayout();
    }

    protected void handleResizeDone() {
        if (this.resizeDone) {
            return;
        }

        // Give the list view the control over it's input back
        this.list.unblockTouchEvents();

        // Quickly fade out edge pull effect
        this.pullEffect.releasePull();

        // Make sure list uses the height of it's parent
        this.list.setVerticalScrollBarEnabled(this.scrollBarEnabled);
        this.setListLayoutHeight(ViewGroup.LayoutParams.MATCH_PARENT);

        this.resizeDone = true;
    }

    private void updateListViewHeight() {
        // Don't do anything if the window hasn't resized yet or if we're already done
        if (this.getWindowPadding() >= this.initialWindowPadding || this.resizeDone) {
            return;
        }

        // Skip animation during keyboard hide to prevent "earthquake" effect
        // Just let adjustResize handle the window resize naturally
        return;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        this.scrollBarEnabled = this.list.isVerticalScrollBarEnabled();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                this.offsetYStart = event.getY();
                this.offsetYCurrent = event.getY();
                this.offsetYDiff = 0;

                this.lastMotionEvent = event;
                this.resizeDone = false;
                this.initialWindowPadding = this.getWindowPadding();

                // Don't lock list height - let adjustResize handle it
                // this.listHeightInitial = this.list.getHeight();
                // this.setListLayoutHeight(this.listHeightInitial);
                break;

            case MotionEvent.ACTION_MOVE:
                this.offsetYCurrent = event.getY();
                this.lastMotionEvent = event;

                // Skip updateListViewHeight to prevent animation conflicts
                // this.updateListViewHeight();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.lastMotionEvent = null;

                // Skip animation - let adjustResize handle resize naturally
                this.handleResizeDone();
                break;
        }

        // Hide the keyboard if the user has scrolled down by about half a result item
        if (isScrolled()) {
            this.handler.hideKeyboard();
            this.handler.applyScrollSystemUi();
        }

        return false;
    }

    public void fixScroll() {
        this.list.post(() -> {
            resizeDone = false;
            handleResizeDone();
        });
    }

    public boolean isScrolled() {
        return (this.offsetYCurrent - this.offsetYStart) > THRESHOLD;
    }

    public interface KeyboardHandler {
        void showKeyboard();

        void hideKeyboard();

        void applyScrollSystemUi();
    }
}
