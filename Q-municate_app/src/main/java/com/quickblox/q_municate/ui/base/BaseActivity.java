package com.quickblox.q_municate.ui.base;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.view.Window;

import com.quickblox.q_municate.App;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.service.QBService;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate.ui.dialogs.ProgressDialog;
import com.quickblox.q_municate.ui.splash.SplashActivity;
import com.quickblox.q_municate_core.utils.DialogUtils;
import com.quickblox.q_municate_core.utils.ErrorUtils;

public abstract class BaseActivity extends Activity {

    public static final int DOUBLE_BACK_DELAY = 2000;

    protected final ProgressDialog progress;
    protected App app;
    protected ActionBar actionBar;
    protected QBService service;
    protected boolean useDoubleBackPressed;
    protected Fragment currentFragment;
    protected FailAction failAction;
    protected SuccessAction successAction;
    protected ActivityHelper activityHelper;

    private boolean doubleBackToExitPressedOnce;
    private boolean bounded;
    private ServiceConnection serviceConnection = new QBChatServiceConnection();

    public BaseActivity() {
        progress = ProgressDialog.newInstance(R.string.dlg_wait_please);
    }

    public FailAction getFailAction() {
        return failAction;
    }

    public synchronized void showProgress() {
        if (!progress.isAdded()) {
            progress.show(getFragmentManager(), null);
        }
    }

    public synchronized void hideProgress() {
        if (progress != null && progress.getActivity() != null) {
            progress.dismissAllowingStateLoss();
        }
    }

    public void hideActionBarProgress() {
        activityHelper.hideActionBarProgress();
    }

    public void showActionBarProgress() {
        activityHelper.showActionBarProgress();
    }

    public void addAction(String action, Command command) {
        activityHelper.addAction(action, command);
    }

    public boolean hasAction(String action) {
        return activityHelper.hasAction(action);
    }

    public void removeAction(String action) {
        activityHelper.removeAction(action);
    }

    public void updateBroadcastActionList() {
        activityHelper.updateBroadcastActionList();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        app = App.getInstance();
        actionBar = getActionBar();
        failAction = new FailAction();
        successAction = new SuccessAction();
        activityHelper = new ActivityHelper(this, new GlobalListener());
        activityHelper.onCreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectToService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityHelper.onResume();
        addAction(QBServiceConsts.LOGIN_REST_SUCCESS_ACTION, successAction);
    }

    @Override
    protected void onPause() {
        activityHelper.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce || !useDoubleBackPressed) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        DialogUtils.show(this, getString(R.string.dlg_click_back_again));
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, DOUBLE_BACK_DELAY);
    }

    protected void onConnectedToService() {
    }

    protected void navigateToParent() {
        Intent intent = NavUtils.getParentActivityIntent(this);
        if (intent == null) {
            finish();
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            NavUtils.navigateUpTo(this, intent);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T _findViewById(int viewId) {
        return (T) findViewById(viewId);
    }

    protected void setCurrentFragment(Fragment fragment) {
        currentFragment = fragment;
        getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction transaction = buildTransaction();
        transaction.replace(R.id.container, fragment, null);
        transaction.commit();
    }

    private void unbindService() {
        if (bounded) {
            unbindService(serviceConnection);
        }
    }

    private void connectToService() {
        Intent intent = new Intent(this, QBService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private FragmentTransaction buildTransaction() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        return transaction;
    }

    protected void onFailAction(String action) {

    }

    protected void onSuccessAction(String action) {

    }

    public class FailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception e = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
            ErrorUtils.showError(BaseActivity.this, e);
            hideProgress();
            onFailAction(bundle.getString(QBServiceConsts.COMMAND_ACTION));
        }
    }

    public class SuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            hideProgress();
            onSuccessAction(bundle.getString(QBServiceConsts.COMMAND_ACTION));
        }
    }

    private class GlobalListener implements ActivityHelper.GlobalActionsListener {

        @Override
        public void onReceiveChatMessageAction(Bundle extras) {
            boolean isSplashActivity = activityHelper.getContext() instanceof SplashActivity;
            if(!isSplashActivity) {
                activityHelper.onReceiveMessage(extras);
            }
        }

        @Override
        public void onReceiveForceReloginAction(Bundle extras) {
            activityHelper.forceRelogin();
        }

        @Override
        public void onReceiveRefreshSessionAction(Bundle extras) {
            DialogUtils.show(BaseActivity.this, getString(R.string.dlg_refresh_session));
            showProgress();
            activityHelper.refreshSession();
        }

        @Override
        public void onReceiveFriendActionAction(Bundle extras) {
            String alertMessage = extras.getString(QBServiceConsts.EXTRA_FRIEND_ALERT_MESSAGE);
            activityHelper.showFriendAlert(alertMessage);
        }
    }

    private class QBChatServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bounded = true;
            service = ((QBService.QBServiceBinder) binder).getService();
            onConnectedToService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }
}